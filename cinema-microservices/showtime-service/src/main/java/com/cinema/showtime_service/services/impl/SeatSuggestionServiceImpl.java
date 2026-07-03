package com.cinema.showtime_service.services.impl;

import com.cinema.Enum.ShowTimeEnum;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.showtime_service.dto.request.SeatSuggestionRequest;
import com.cinema.showtime_service.dto.response.SeatSuggestionCandidateResponse;
import com.cinema.showtime_service.dto.response.SeatSuggestionResponse;
import com.cinema.showtime_service.entity.PricingPolicy;
import com.cinema.showtime_service.entity.ShowTime;
import com.cinema.showtime_service.grpc.BookingGrpcClient;
import com.cinema.showtime_service.grpc.SeatGrpcClient;
import com.cinema.showtime_service.repository.PricingPolicyRepository;
import com.cinema.showtime_service.repository.ShowTimeRepository;
import com.cinema.showtime_service.services.SeatSuggestionService;
import com.cinema.grpc.seat.LayoutSeatPayload;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SeatSuggestionServiceImpl implements SeatSuggestionService {

    private static final int MAX_CANDIDATES = 5;
    private static final int MAX_BLOCKS_PER_SIZE = 12;

    ShowTimeRepository showTimeRepository;
    PricingPolicyRepository pricingPolicyRepository;
    SeatGrpcClient seatGrpcClient;
    BookingGrpcClient bookingGrpcClient;

    @Override
    @Transactional(readOnly = true)
    public SeatSuggestionResponse suggestSeatSuggestions(UUID showtimeId, SeatSuggestionRequest request) {
        validateRequest(request);

        ShowTime showTime = showTimeRepository.findById(showtimeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOWTIME_NOT_FOUND));
        PricingPolicy pricingPolicy = pricingPolicyRepository.findByIdAndIsDeletedFalse(showTime.getPricingPolicyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        SeatGrpcClient.LayoutBundle layout = seatGrpcClient.getLayoutByHallId(showTime.getHallId());
        List<SeatNode> availableSeats = loadAvailableSeats(showtimeId, layout, pricingPolicy);
        boolean preferCoupleSeat = Boolean.TRUE.equals(request.getPreferCoupleSeat());
        Map<String, String> couplePartnerBySeatCode = buildCouplePartnerBySeatCode(layout);

        if (availableSeats.size() < request.getSeatCount()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_AVAILABLE_SEATS);
        }

        List<SeatSuggestionCandidateResponse> candidates = preferCoupleSeat
                ? buildCouplePreferredCandidates(
                        availableSeats,
                        layout,
                        request.getSeatCount(),
                        couplePartnerBySeatCode)
                : buildExactCandidates(
                availableSeats,
                layout,
                request.getSeatCount(),
                false);

        if (candidates.isEmpty()) {
            candidates = buildFallbackCandidates(
                    availableSeats,
                    layout,
                    request.getSeatCount(),
                    preferCoupleSeat,
                    couplePartnerBySeatCode);
        }

        candidates = candidates.stream()
                .sorted(candidateComparator())
                .limit(MAX_CANDIDATES)
                .toList();

        return SeatSuggestionResponse.builder()
                .showtimeId(showtimeId)
                .requestedSeatCount(request.getSeatCount())
                .preferCoupleSeat(preferCoupleSeat)
                .candidates(candidates)
                .build();
    }

    private void validateRequest(SeatSuggestionRequest request) {
        if (request == null || request.getSeatCount() == null || request.getPreferCoupleSeat() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        if (request.getSeatCount() < 1 || request.getSeatCount() > 5) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private List<SeatNode> loadAvailableSeats(UUID showtimeId,
                                              SeatGrpcClient.LayoutBundle layout,
                                              PricingPolicy pricingPolicy) {
        List<String> seatCodes = layout.getSeats().stream()
                .map(LayoutSeatPayload::getSeatCode)
                .filter(Objects::nonNull)
                .map(this::normalizeSeatCode)
                .toList();

        Map<String, String> runtimeStates = seatCodes.isEmpty()
                ? Map.of()
                : bookingGrpcClient.getSeatRuntimeStates(showtimeId, seatCodes);

        return layout.getSeats().stream()
                .map(seat -> toSeatNode(seat, runtimeStates.get(normalizeSeatCode(seat.getSeatCode())), pricingPolicy))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(SeatNode::row).thenComparingInt(SeatNode::col))
                .toList();
    }

    private SeatNode toSeatNode(LayoutSeatPayload seat, String runtimeState, PricingPolicy pricingPolicy) {
        if (seat == null) {
            return null;
        }
        String state = runtimeState == null ? "AVAILABLE" : runtimeState.trim().toUpperCase(Locale.ROOT);
        if (!"AVAILABLE".equals(state)) {
            return null;
        }

        String seatType = seat.getSeatType() == null ? null : seat.getSeatType().trim().toUpperCase(Locale.ROOT);
        long price = resolvePriceBySeatType(seatType, pricingPolicy);
        return new SeatNode(
                normalizeSeatCode(seat.getSeatCode()),
                seat.getRow(),
                seat.getCol(),
                seatType,
                price);
    }

    private long resolvePriceBySeatType(String seatType, PricingPolicy pricingPolicy) {
        if (seatType == null || pricingPolicy == null) {
            return 0L;
        }
        return switch (seatType) {
            case "VIP" -> safeLong(pricingPolicy.getVipPrice());
            case "COUPLE" -> safeLong(pricingPolicy.getCouplePrice());
            default -> safeLong(pricingPolicy.getStandardPrice());
        };
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private List<SeatSuggestionCandidateResponse> buildExactCandidates(List<SeatNode> availableSeats,
                                                                       SeatGrpcClient.LayoutBundle layout,
                                                                       int seatCount,
                                                                       boolean preferCoupleSeat) {
        List<SeatSuggestionCandidateResponse> candidates = new ArrayList<>();
        for (List<SeatNode> segment : splitIntoSegments(availableSeats)) {
            if (segment.size() < seatCount) {
                continue;
            }
            for (int index = 0; index <= segment.size() - seatCount; index++) {
                List<SeatNode> window = segment.subList(index, index + seatCount);
                candidates.add(buildCandidate(window, layout, preferCoupleSeat, true));
            }
        }
        return dedupeAndSort(candidates);
    }

    private List<SeatSuggestionCandidateResponse> buildCouplePreferredCandidates(List<SeatNode> availableSeats,
                                                                                 SeatGrpcClient.LayoutBundle layout,
                                                                                 int seatCount,
                                                                                 Map<String, String> couplePartnerBySeatCode) {
        List<BlockCandidate> coupleBlocks = buildCouplePairBlocks(availableSeats, layout, couplePartnerBySeatCode);
        List<SeatNode> standardSingles = availableSeats.stream()
                .filter(seat -> !isCoupleSeat(seat))
                .toList();

        List<SeatSuggestionCandidateResponse> candidates = new ArrayList<>();
        Set<String> dedupeKeys = new HashSet<>();
        int maxCouplePairs = Math.min(seatCount / 2, coupleBlocks.size());
        for (int targetCouplePairs = maxCouplePairs; targetCouplePairs >= 0; targetCouplePairs--) {
            int singlesNeeded = seatCount - (targetCouplePairs * 2);
            searchCouplePreferredCandidates(
                    targetCouplePairs,
                    targetCouplePairs,
                    singlesNeeded,
                    coupleBlocks,
                    0,
                    new ArrayList<>(),
                    new LinkedHashSet<>(),
                    standardSingles,
                    layout,
                    candidates,
                    dedupeKeys);
        }
        return dedupeAndSort(candidates);
    }

    private List<SeatSuggestionCandidateResponse> buildFallbackCandidates(List<SeatNode> availableSeats,
                                                                          SeatGrpcClient.LayoutBundle layout,
                                                                          int seatCount,
                                                                          boolean preferCoupleSeat,
                                                                          Map<String, String> couplePartnerBySeatCode) {
        Map<Integer, List<BlockCandidate>> blocksBySize = buildBlocksBySize(availableSeats, layout, seatCount - 1,
                preferCoupleSeat, couplePartnerBySeatCode);
        List<SeatSuggestionCandidateResponse> candidates = new ArrayList<>();
        searchFallbackCandidates(
                seatCount,
                seatCount - 1,
                blocksBySize,
                new ArrayList<>(),
                new LinkedHashSet<>(),
                layout,
                preferCoupleSeat,
                candidates,
                new HashSet<>());
        return dedupeAndSort(candidates);
    }

    private void searchCouplePreferredCandidates(int targetCouplePairs,
                                                 int remainingCouplePairs,
                                                 int singlesNeeded,
                                                 List<BlockCandidate> coupleBlocks,
                                                 int startIndex,
                                                 List<BlockCandidate> chosenCoupleBlocks,
                                                 Set<String> usedSeatCodes,
                                                 List<SeatNode> standardSingles,
                                                 SeatGrpcClient.LayoutBundle layout,
                                                 List<SeatSuggestionCandidateResponse> results,
                                                 Set<String> dedupeKeys) {
        if (results.size() >= 1000) {
            return;
        }
        if (remainingCouplePairs == 0) {
            List<SeatNode> chosenCoupleSeats = chosenCoupleBlocks.stream()
                    .flatMap(block -> block.seats().stream())
                    .toList();
            List<SeatNode> singlePool = standardSingles.stream()
                    .filter(seat -> !usedSeatCodes.contains(seat.seatCode()))
                    .sorted(singleSeatComparator(chosenCoupleSeats, layout))
                    .limit(Math.max(12, singlesNeeded * 8))
                    .toList();

            searchSingleSeatCombinations(
                    singlesNeeded,
                    singlePool,
                    0,
                    new ArrayList<>(),
                    chosenCoupleSeats,
                    targetCouplePairs,
                    layout,
                    results,
                    dedupeKeys);
            return;
        }

        for (int index = startIndex; index < coupleBlocks.size(); index++) {
            BlockCandidate block = coupleBlocks.get(index);
            if (!Collections.disjoint(usedSeatCodes, block.seatCodeSet())) {
                continue;
            }
            chosenCoupleBlocks.add(block);
            usedSeatCodes.addAll(block.seatCodeSet());
            searchCouplePreferredCandidates(
                    targetCouplePairs,
                    remainingCouplePairs - 1,
                    singlesNeeded,
                    coupleBlocks,
                    index + 1,
                    chosenCoupleBlocks,
                    usedSeatCodes,
                    standardSingles,
                    layout,
                    results,
                    dedupeKeys);
            chosenCoupleBlocks.remove(chosenCoupleBlocks.size() - 1);
            usedSeatCodes.removeAll(block.seatCodeSet());
        }
    }

    private void searchSingleSeatCombinations(int remainingSingles,
                                              List<SeatNode> singlePool,
                                              int startIndex,
                                              List<SeatNode> chosenSingles,
                                              List<SeatNode> chosenCoupleSeats,
                                              int selectedCouplePairs,
                                              SeatGrpcClient.LayoutBundle layout,
                                              List<SeatSuggestionCandidateResponse> results,
                                              Set<String> dedupeKeys) {
        if (results.size() >= 1000) {
            return;
        }
        if (remainingSingles == 0) {
            List<SeatNode> seats = new ArrayList<>(chosenCoupleSeats);
            seats.addAll(chosenSingles);
            boolean exact = countGroups(seats.stream()
                    .sorted(Comparator.comparingInt(SeatNode::row).thenComparingInt(SeatNode::col))
                    .toList()) == 1;
            SeatSuggestionCandidateResponse candidate = buildCandidate(
                    seats,
                    layout,
                    true,
                    exact,
                    selectedCouplePairs);
            if (dedupeKeys.add(canonicalKey(candidate.getSeatCodes()))) {
                results.add(candidate);
            }
            return;
        }

        for (int index = startIndex; index < singlePool.size(); index++) {
            chosenSingles.add(singlePool.get(index));
            searchSingleSeatCombinations(
                    remainingSingles - 1,
                    singlePool,
                    index + 1,
                    chosenSingles,
                    chosenCoupleSeats,
                    selectedCouplePairs,
                    layout,
                    results,
                    dedupeKeys);
            chosenSingles.remove(chosenSingles.size() - 1);
        }
    }

    private void searchFallbackCandidates(int remainingSeats,
                                          int maxPartSize,
                                          Map<Integer, List<BlockCandidate>> blocksBySize,
                                          List<BlockCandidate> chosen,
                                          Set<String> usedSeatCodes,
                                          SeatGrpcClient.LayoutBundle layout,
                                          boolean preferCoupleSeat,
                                          List<SeatSuggestionCandidateResponse> results,
                                          Set<String> dedupeKeys) {
        if (results.size() >= 1000) {
            return;
        }
        if (remainingSeats == 0) {
            List<SeatNode> seats = chosen.stream()
                    .flatMap(block -> block.seats().stream())
                    .sorted(Comparator.comparingInt(SeatNode::row).thenComparingInt(SeatNode::col))
                    .toList();
            SeatSuggestionCandidateResponse candidate = buildCandidate(seats, layout, preferCoupleSeat, false);
            String key = canonicalKey(candidate.getSeatCodes());
            if (dedupeKeys.add(key)) {
                results.add(candidate);
            }
            return;
        }

        int upperBound = Math.min(maxPartSize, remainingSeats);
        for (int partSize = upperBound; partSize >= 1; partSize--) {
            List<BlockCandidate> blocks = blocksBySize.getOrDefault(partSize, List.of());
            for (BlockCandidate block : blocks) {
                if (!Collections.disjoint(usedSeatCodes, block.seatCodeSet())) {
                    continue;
                }
                chosen.add(block);
                usedSeatCodes.addAll(block.seatCodeSet());
                searchFallbackCandidates(
                        remainingSeats - partSize,
                        partSize,
                        blocksBySize,
                        chosen,
                        usedSeatCodes,
                        layout,
                        preferCoupleSeat,
                        results,
                        dedupeKeys);
                chosen.remove(chosen.size() - 1);
                usedSeatCodes.removeAll(block.seatCodeSet());
            }
        }
    }

    private Map<Integer, List<BlockCandidate>> buildBlocksBySize(List<SeatNode> availableSeats,
                                                                 SeatGrpcClient.LayoutBundle layout,
                                                                 int maxSize,
                                                                 boolean preferCoupleSeat,
                                                                 Map<String, String> couplePartnerBySeatCode) {
        Map<Integer, List<BlockCandidate>> result = new HashMap<>();
        for (List<SeatNode> segment : splitIntoSegments(availableSeats)) {
            for (int size = 1; size <= Math.min(maxSize, segment.size()); size++) {
                for (int index = 0; index <= segment.size() - size; index++) {
                    List<SeatNode> window = segment.subList(index, index + size);
                    if (!isValidSeatSelection(window, couplePartnerBySeatCode)) {
                        continue;
                    }
                    BlockCandidate block = new BlockCandidate(window);
                    result.computeIfAbsent(size, ignore -> new ArrayList<>()).add(block);
                }
            }
        }

        Comparator<BlockCandidate> comparator = blockComparator(layout, preferCoupleSeat);
        result.replaceAll((size, blocks) -> blocks.stream()
                .sorted(comparator)
                .limit(MAX_BLOCKS_PER_SIZE)
                .toList());
        return result;
    }

    private Comparator<BlockCandidate> blockComparator(SeatGrpcClient.LayoutBundle layout,
                                                       boolean preferCoupleSeat) {
        if (preferCoupleSeat) {
            return Comparator.comparingInt((BlockCandidate block) -> -block.couplePairCount())
                    .thenComparingInt(BlockCandidate::rowSpan)
                    .thenComparing(Comparator.comparingDouble((BlockCandidate block) -> -screenDistance(block.seats(), layout)))
                    .thenComparingInt((BlockCandidate block) -> -block.maxCol())
                    .thenComparingInt(BlockCandidate::groupCount)
                    .thenComparing(BlockCandidate::canonicalSeatCodes);
        }

        return Comparator.comparingDouble((BlockCandidate block) -> centerDistance(block.seats(), layout))
                .thenComparing(Comparator.comparingDouble((BlockCandidate block) -> -screenDistance(block.seats(), layout)))
                .thenComparingInt(BlockCandidate::groupCount)
                .thenComparing(BlockCandidate::canonicalSeatCodes);
    }

    private List<List<SeatNode>> splitIntoSegments(List<SeatNode> availableSeats) {
        Map<Integer, List<SeatNode>> byRow = availableSeats.stream()
                .collect(Collectors.groupingBy(SeatNode::row, LinkedHashMap::new, Collectors.toList()));

        List<List<SeatNode>> segments = new ArrayList<>();
        for (List<SeatNode> rowSeats : byRow.values()) {
            rowSeats.sort(Comparator.comparingInt(SeatNode::col));
            List<SeatNode> current = new ArrayList<>();
            SeatNode previous = null;
            for (SeatNode seat : rowSeats) {
                if (previous == null || seat.col() == previous.col() + 1) {
                    current.add(seat);
                } else {
                    if (!current.isEmpty()) {
                        segments.add(new ArrayList<>(current));
                    }
                    current.clear();
                    current.add(seat);
                }
                previous = seat;
            }
            if (!current.isEmpty()) {
                segments.add(new ArrayList<>(current));
            }
        }
        return segments;
    }

    private SeatSuggestionCandidateResponse buildCandidate(List<SeatNode> seats,
                                                           SeatGrpcClient.LayoutBundle layout,
                                                           boolean preferCoupleSeat,
                                                           boolean exact) {
        return buildCandidate(seats, layout, preferCoupleSeat, exact, countCoupleSeats(seats) / 2);
    }

    private SeatSuggestionCandidateResponse buildCandidate(List<SeatNode> seats,
                                                           SeatGrpcClient.LayoutBundle layout,
                                                           boolean preferCoupleSeat,
                                                           boolean exact,
                                                           int couplePairCount) {
        List<SeatNode> orderedSeats = seats.stream()
                .sorted(Comparator.comparingInt(SeatNode::row).thenComparingInt(SeatNode::col))
                .toList();
        List<String> seatCodes = orderedSeats.stream()
                .map(SeatNode::seatCode)
                .toList();
        boolean hasCouple = orderedSeats.stream().anyMatch(seat -> "COUPLE".equalsIgnoreCase(seat.seatType()));
        int seatCount = orderedSeats.size();
        long totalPrice = orderedSeats.stream().mapToLong(SeatNode::price).sum();
        double centerDistance = centerDistance(orderedSeats, layout);
        double screenDistance = screenDistance(orderedSeats, layout);
        int groupCount = countGroups(orderedSeats);
        int score = preferCoupleSeat
                ? calculateCoupleScore(orderedSeats, screenDistance, couplePairCount, exact, groupCount)
                : calculateScore(centerDistance, screenDistance, preferCoupleSeat, couplePairCount, exact, groupCount);

        return SeatSuggestionCandidateResponse.builder()
                .seatCodes(seatCodes)
                .seatCount(seatCount)
                .hasCoupleSeats(hasCouple)
                .totalPrice(totalPrice)
                .score(score)
                .reason(buildCleanReasonMessage(exact, preferCoupleSeat, couplePairCount, seatCount - (couplePairCount * 2), groupCount))
                .build();
    }

    private int calculateScore(double centerDistance,
                               double screenDistance,
                               boolean preferCoupleSeat,
                               int couplePairCount,
                               boolean exact,
                               int groupCount) {
        int score = 100_000;
        score -= (int) Math.round(centerDistance * 10_000);
        score += (int) Math.round(screenDistance * 500);
        if (preferCoupleSeat && couplePairCount > 0) {
            score += 5_000;
        }
        if (exact) {
            score += 10_000;
        }
        score -= Math.max(0, groupCount - 1) * 2_500;
        return score;
    }

    private int calculateCoupleScore(List<SeatNode> seats,
                                     double screenDistance,
                                     int couplePairCount,
                                     boolean exact,
                                     int groupCount) {
        int score = 0;
        if (couplePairCount > 0) {
            score += couplePairCount * 1_000_000;
        } else {
            score -= 1_000_000;
        }
        score += rightEdge(seats) * 100;
        score += rightEdgeOfSingles(seats) * 50;
        score += (int) Math.round(screenDistance * 250);
        score -= coupleFallbackPenalty(seats);
        score -= Math.max(0, coupleGroupCount(seats) - 1) * 100_000;
        if (couplePairCount > 0) {
            score += 5_000;
        }
        return score;
    }

    private String buildCleanReasonMessage(boolean exact,
                                           boolean preferCoupleSeat,
                                           int couplePairCount,
                                           int singleSeatCount,
                                           int groupCount) {
        StringBuilder reason = new StringBuilder();
        reason.append(exact ? "Ghế liền nhau, gần trung tâm" : "Fallback tổ hợp ghế gần trung tâm");
        if (preferCoupleSeat && couplePairCount > 0) {
            reason.append(", ưu tiên ghế couple theo cặp cố định");
            if (singleSeatCount > 0) {
                reason.append(", ghép thêm ghế thường gần cặp ghế");
            }
        } else if (preferCoupleSeat) {
            reason.append(", fallback khi không đủ cặp couple phù hợp");
        }
        if (!exact && groupCount > 1) {
            reason.append(", nhiều cụm ghế nhỏ");
        }
        return reason.toString();
    }

    private String buildReasonMessage(boolean exact,
                                      boolean preferCoupleSeat,
                                      int couplePairCount,
                                      int singleSeatCount,
                                      int groupCount) {
        StringBuilder reason = new StringBuilder();
        reason.append(exact ? "Ghế liền nhau, gần trung tâm" : "Fallback tổ hợp ghế gần trung tâm");
        if (preferCoupleSeat && couplePairCount > 0) {
            reason.append(", ưu tiên ghế couple theo cặp");
            if (singleSeatCount > 0) {
                reason.append(", ghép thêm ghế thường gần cặp ghế");
            }
        } else if (preferCoupleSeat) {
            reason.append(", fallback khi không đủ cặp couple phù hợp");
        }
        if (!exact && groupCount > 1) {
            reason.append(", nhiều cụm ghế nhỏ");
        }
        return reason.toString();
    }

    private Map<String, String> buildCouplePartnerBySeatCode(SeatGrpcClient.LayoutBundle layout) {
        Map<String, String> partners = new HashMap<>();
        if (layout == null || layout.getSeats() == null) {
            return partners;
        }

        Map<Integer, List<LayoutSeatPayload>> byRow = layout.getSeats().stream()
                .filter(Objects::nonNull)
                .filter(this::isCoupleSeat)
                .collect(Collectors.groupingBy(LayoutSeatPayload::getRow, LinkedHashMap::new, Collectors.toList()));

        for (List<LayoutSeatPayload> rowSeats : byRow.values()) {
            rowSeats.sort(Comparator.comparingInt(LayoutSeatPayload::getCol));
            for (int index = 0; index < rowSeats.size(); index++) {
                if (index + 1 >= rowSeats.size()) {
                    continue;
                }
                LayoutSeatPayload current = rowSeats.get(index);
                LayoutSeatPayload partner = rowSeats.get(index + 1);
                if (partner.getCol() != current.getCol() + 1) {
                    continue;
                }

                String currentSeatCode = normalizeSeatCode(current.getSeatCode());
                String partnerSeatCode = normalizeSeatCode(partner.getSeatCode());
                partners.put(currentSeatCode, partnerSeatCode);
                partners.put(partnerSeatCode, currentSeatCode);
                index++;
            }
        }
        return partners;
    }

    private List<BlockCandidate> buildCouplePairBlocks(List<SeatNode> availableSeats,
                                                       SeatGrpcClient.LayoutBundle layout,
                                                       Map<String, String> couplePartnerBySeatCode) {
        Map<String, SeatNode> seatByCode = availableSeats.stream()
                .collect(Collectors.toMap(SeatNode::seatCode, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Set<String> consumed = new HashSet<>();
        List<BlockCandidate> blocks = new ArrayList<>();

        for (SeatNode seat : availableSeats) {
            if (!isCoupleSeat(seat) || !consumed.add(seat.seatCode())) {
                continue;
            }
            String partnerCode = couplePartnerBySeatCode.get(seat.seatCode());
            SeatNode partner = partnerCode == null ? null : seatByCode.get(partnerCode);
            if (partner == null) {
                continue;
            }
            consumed.add(partner.seatCode());
            List<SeatNode> pairSeats = List.of(seat, partner).stream()
                    .sorted(Comparator.comparingInt(SeatNode::row).thenComparingInt(SeatNode::col))
                    .toList();
            blocks.add(new BlockCandidate(pairSeats));
        }

        return blocks.stream()
                .sorted(blockComparator(layout, true))
                .limit(MAX_BLOCKS_PER_SIZE)
                .toList();
    }

    private boolean isValidSeatSelection(List<SeatNode> seats, Map<String, String> couplePartnerBySeatCode) {
        if (seats == null || seats.isEmpty()) {
            return false;
        }
        Set<String> seatCodes = seats.stream()
                .map(SeatNode::seatCode)
                .collect(Collectors.toSet());
        for (SeatNode seat : seats) {
            if (!isCoupleSeat(seat)) {
                continue;
            }
            String partnerCode = couplePartnerBySeatCode.get(seat.seatCode());
            if (partnerCode == null || !seatCodes.contains(partnerCode)) {
                return false;
            }
        }
        return true;
    }

    private Comparator<SeatNode> singleSeatComparator(List<SeatNode> chosenCoupleSeats,
                                                      SeatGrpcClient.LayoutBundle layout) {
        if (chosenCoupleSeats == null || chosenCoupleSeats.isEmpty()) {
            return Comparator.comparingDouble((SeatNode seat) -> -screenDistance(List.of(seat), layout))
                    .thenComparing(Comparator.comparingInt(SeatNode::col).reversed())
                    .thenComparing(SeatNode::seatCode);
        }

        return Comparator.comparingDouble((SeatNode seat) -> distanceToChosenCouples(seat, chosenCoupleSeats))
                .thenComparing(Comparator.comparingDouble((SeatNode seat) -> -screenDistance(List.of(seat), layout)))
                .thenComparing(Comparator.comparingInt(SeatNode::col).reversed())
                .thenComparing(SeatNode::seatCode);
    }

    private double distanceToChosenCouples(SeatNode seat, List<SeatNode> chosenCoupleSeats) {
        if (chosenCoupleSeats == null || chosenCoupleSeats.isEmpty()) {
            return Double.MAX_VALUE;
        }
        if (coupleGroupCount(chosenCoupleSeats) > 1) {
            SeatNode anchorSeat = chosenCoupleSeats.stream()
                    .max(Comparator.comparingInt(SeatNode::row).thenComparingInt(SeatNode::col))
                    .orElse(null);
            if (anchorSeat == null) {
                return Double.MAX_VALUE;
            }
            return Math.abs(seat.row() - anchorSeat.row()) * 10.0
                    + Math.abs(seat.col() - anchorSeat.col());
        }
        double centerRow = chosenCoupleSeats.stream()
                .mapToInt(SeatNode::row)
                .average()
                .orElse(Double.NaN);
        double centerCol = chosenCoupleSeats.stream()
                .mapToInt(SeatNode::col)
                .average()
                .orElse(Double.NaN);
        if (Double.isNaN(centerRow) || Double.isNaN(centerCol)) {
            return Double.MAX_VALUE;
        }
        return Math.abs(seat.row() - centerRow) + Math.abs(seat.col() - centerCol);
    }

    private int coupleFallbackPenalty(List<SeatNode> seats) {
        if (seats == null || seats.isEmpty()) {
            return 0;
        }
        List<SeatNode> coupleSeats = seats.stream()
                .filter(this::isCoupleSeat)
                .toList();
        if (coupleSeats.isEmpty()) {
            return 0;
        }

        double penalty = 0.0;
        for (SeatNode seat : seats) {
            if (isCoupleSeat(seat)) {
                continue;
            }
            penalty += distanceToChosenCouples(seat, coupleSeats);
        }
        return (int) Math.round(penalty * 10_000);
    }

    private int rightEdge(List<SeatNode> seats) {
        if (seats == null || seats.isEmpty()) {
            return 0;
        }
        return seats.stream()
                .mapToInt(SeatNode::col)
                .max()
                .orElse(0);
    }

    private int rightEdgeOfSingles(List<SeatNode> seats) {
        if (seats == null || seats.isEmpty()) {
            return 0;
        }
        return seats.stream()
                .filter(seat -> !isCoupleSeat(seat))
                .mapToInt(SeatNode::col)
                .max()
                .orElse(0);
    }

    private int rowSpread(List<SeatNode> seats) {
        if (seats == null || seats.isEmpty()) {
            return 0;
        }
        int minRow = Integer.MAX_VALUE;
        int maxRow = Integer.MIN_VALUE;
        for (SeatNode seat : seats) {
            minRow = Math.min(minRow, seat.row());
            maxRow = Math.max(maxRow, seat.row());
        }
        return maxRow - minRow;
    }

    private int coupleGroupCount(List<SeatNode> seats) {
        if (seats == null || seats.isEmpty()) {
            return 0;
        }
        List<SeatNode> coupleSeats = seats.stream()
                .filter(this::isCoupleSeat)
                .sorted(Comparator.comparingInt(SeatNode::row).thenComparingInt(SeatNode::col))
                .toList();
        if (coupleSeats.isEmpty()) {
            return 0;
        }

        int groups = 1;
        for (int index = 1; index < coupleSeats.size(); index++) {
            SeatNode previous = coupleSeats.get(index - 1);
            SeatNode current = coupleSeats.get(index);
            if (previous.row() != current.row() || current.col() != previous.col() + 1) {
                groups++;
            }
        }
        return groups;
    }

    private boolean isCoupleSeat(SeatNode seat) {
        return seat != null && "COUPLE".equalsIgnoreCase(seat.seatType());
    }

    private boolean isCoupleSeat(LayoutSeatPayload seat) {
        return seat != null && "COUPLE".equalsIgnoreCase(seat.getSeatType());
    }

    private int countCoupleSeats(List<SeatNode> seats) {
        return (int) seats.stream().filter(this::isCoupleSeat).count();
    }

    private int countGroups(List<SeatNode> seats) {
        if (seats.isEmpty()) {
            return 0;
        }
        int groups = 1;
        for (int index = 1; index < seats.size(); index++) {
            SeatNode previous = seats.get(index - 1);
            SeatNode current = seats.get(index);
            boolean contiguous = previous.row() == current.row() && current.col() == previous.col() + 1;
            if (!contiguous) {
                groups++;
            }
        }
        return groups;
    }

    private double centerDistance(List<SeatNode> seats, SeatGrpcClient.LayoutBundle layout) {
        if (seats.isEmpty()) {
            return Double.MAX_VALUE;
        }
        double centerRow = (layout.getTotalRows() + 1) / 2.0;
        double centerCol = (layout.getTotalCols() + 1) / 2.0;
        double total = 0.0;
        for (SeatNode seat : seats) {
            total += Math.abs(seat.row() - centerRow) + Math.abs(seat.col() - centerCol);
        }
        return total / seats.size();
    }

    private double screenDistance(List<SeatNode> seats, SeatGrpcClient.LayoutBundle layout) {
        if (seats.isEmpty()) {
            return 0.0;
        }
        String screenPosition = layout.getScreenPosition() == null
                ? "TOP"
                : layout.getScreenPosition().trim().toUpperCase(Locale.ROOT);

        double total = 0.0;
        for (SeatNode seat : seats) {
            total += switch (screenPosition) {
                case "BOTTOM" -> Math.max(0, layout.getTotalRows() - seat.row());
                case "LEFT" -> Math.max(0, seat.col() - 1);
                case "RIGHT" -> Math.max(0, layout.getTotalCols() - seat.col());
                default -> Math.max(0, seat.row() - 1);
            };
        }
        return total / seats.size();
    }

    private Comparator<SeatSuggestionCandidateResponse> candidateComparator() {
        return Comparator.comparingInt(SeatSuggestionCandidateResponse::getScore).reversed()
                .thenComparing(candidate -> canonicalKey(candidate.getSeatCodes()));
    }

    private List<SeatSuggestionCandidateResponse> dedupeAndSort(List<SeatSuggestionCandidateResponse> candidates) {
        Map<String, SeatSuggestionCandidateResponse> deduped = new LinkedHashMap<>();
        for (SeatSuggestionCandidateResponse candidate : candidates) {
            deduped.putIfAbsent(canonicalKey(candidate.getSeatCodes()), candidate);
        }
        return deduped.values().stream()
                .sorted(candidateComparator())
                .toList();
    }

    private String canonicalKey(Collection<String> seatCodes) {
        if (seatCodes == null || seatCodes.isEmpty()) {
            return "";
        }
        return seatCodes.stream()
                .filter(Objects::nonNull)
                .map(this::normalizeSeatCode)
                .sorted()
                .collect(Collectors.joining(","));
    }

    private String normalizeSeatCode(String seatCode) {
        return seatCode == null ? "" : seatCode.trim().toUpperCase(Locale.ROOT);
    }

    private record SeatNode(String seatCode, int row, int col, String seatType, long price) {
    }

    private record BlockCandidate(List<SeatNode> seats) {
        BlockCandidate {
            seats = seats == null ? List.of() : List.copyOf(seats);
        }

        Set<String> seatCodeSet() {
            return seats.stream()
                    .map(SeatNode::seatCode)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        boolean hasCoupleSeats() {
            return seats.stream().anyMatch(seat -> "COUPLE".equalsIgnoreCase(seat.seatType()));
        }

        int couplePairCount() {
            return (int) seats.stream().filter(seat -> "COUPLE".equalsIgnoreCase(seat.seatType())).count() / 2;
        }

        int groupCount() {
            if (seats.isEmpty()) {
                return 0;
            }
            int groups = 1;
            for (int index = 1; index < seats.size(); index++) {
                SeatNode previous = seats.get(index - 1);
                SeatNode current = seats.get(index);
                if (previous.row() != current.row() || current.col() != previous.col() + 1) {
                    groups++;
                }
            }
            return groups;
        }

        String canonicalSeatCodes() {
            return seats.stream()
                    .map(SeatNode::seatCode)
                    .sorted()
                    .collect(Collectors.joining(","));
        }

        int rowSpan() {
            if (seats.isEmpty()) {
                return 0;
            }
            int minRow = seats.stream().mapToInt(SeatNode::row).min().orElse(0);
            int maxRow = seats.stream().mapToInt(SeatNode::row).max().orElse(0);
            return maxRow - minRow;
        }

        int maxCol() {
            return seats.stream()
                    .mapToInt(SeatNode::col)
                    .max()
                    .orElse(0);
        }
    }
}

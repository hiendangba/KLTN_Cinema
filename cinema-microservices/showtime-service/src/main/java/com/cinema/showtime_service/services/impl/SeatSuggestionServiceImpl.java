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
        boolean oddPreferCoupleSeat = preferCoupleSeat && request.getSeatCount() % 2 != 0;

        if (availableSeats.size() < request.getSeatCount()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_AVAILABLE_SEATS);
        }

        List<SeatSuggestionCandidateResponse> candidates = oddPreferCoupleSeat
                ? List.of()
                : buildExactCandidates(
                        availableSeats,
                        layout,
                        request.getSeatCount(),
                        preferCoupleSeat);

        if (candidates.isEmpty()) {
            candidates = buildFallbackCandidates(
                    availableSeats,
                    layout,
                    request.getSeatCount(),
                    preferCoupleSeat);
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

    private List<SeatSuggestionCandidateResponse> buildFallbackCandidates(List<SeatNode> availableSeats,
                                                                          SeatGrpcClient.LayoutBundle layout,
                                                                          int seatCount,
                                                                          boolean preferCoupleSeat) {
        Map<Integer, List<BlockCandidate>> blocksBySize = buildBlocksBySize(availableSeats, layout, seatCount - 1,
                preferCoupleSeat);
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
                                                                 boolean preferCoupleSeat) {
        Map<Integer, List<BlockCandidate>> result = new HashMap<>();
        for (List<SeatNode> segment : splitIntoSegments(availableSeats)) {
            for (int size = 1; size <= Math.min(maxSize, segment.size()); size++) {
                for (int index = 0; index <= segment.size() - size; index++) {
                    List<SeatNode> window = segment.subList(index, index + size);
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
        return Comparator.comparingDouble((BlockCandidate block) -> centerDistance(block.seats(), layout))
                .thenComparing(Comparator.comparingInt((BlockCandidate block) -> preferCoupleSeat && block.hasCoupleSeats() ? 0 : 1))
                .thenComparing(Comparator.comparingDouble((BlockCandidate block) -> -screenDistance(block.seats(), layout)))
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
        List<SeatNode> orderedSeats = seats.stream()
                .sorted(Comparator.comparingInt(SeatNode::row).thenComparingInt(SeatNode::col))
                .toList();
        List<String> seatCodes = orderedSeats.stream()
                .map(SeatNode::seatCode)
                .toList();
        boolean hasCouple = orderedSeats.stream().anyMatch(seat -> "COUPLE".equalsIgnoreCase(seat.seatType()));
        int seatCount = orderedSeats.size();
        int coupleCount = (int) orderedSeats.stream().filter(seat -> "COUPLE".equalsIgnoreCase(seat.seatType())).count();
        long totalPrice = orderedSeats.stream().mapToLong(SeatNode::price).sum();
        double centerDistance = centerDistance(orderedSeats, layout);
        double screenDistance = screenDistance(orderedSeats, layout);
        int groupCount = countGroups(orderedSeats);
        int score = calculateScore(centerDistance, screenDistance, preferCoupleSeat, hasCouple, coupleCount, exact, groupCount);

        return SeatSuggestionCandidateResponse.builder()
                .seatCodes(seatCodes)
                .seatCount(seatCount)
                .hasCoupleSeats(hasCouple)
                .totalPrice(totalPrice)
                .score(score)
                .reason(buildReason(exact, preferCoupleSeat, hasCouple, groupCount, seatCodes))
                .build();
    }

    private int calculateScore(double centerDistance,
                               double screenDistance,
                               boolean preferCoupleSeat,
                               boolean hasCoupleSeats,
                               int coupleCount,
                               boolean exact,
                               int groupCount) {
        int score = 100_000;
        score -= (int) Math.round(centerDistance * 10_000);
        score += (int) Math.round(screenDistance * 500);
        if (preferCoupleSeat && hasCoupleSeats) {
            score += coupleCount * 20_000;
        }
        if (exact) {
            score += 10_000;
        }
        score -= Math.max(0, groupCount - 1) * 2_500;
        return score;
    }

    private String buildReason(boolean exact,
                               boolean preferCoupleSeat,
                               boolean hasCoupleSeats,
                               int groupCount,
                               List<String> seatCodes) {
        StringBuilder reason = new StringBuilder();
        reason.append(exact ? "Ghế liền nhau, gần trung tâm" : "Fallback tổ hợp ghế gần trung tâm");
        if (preferCoupleSeat && hasCoupleSeats) {
            reason.append(", ưu tiên couple");
        }
        if (!exact && groupCount > 1) {
            reason.append(", nhiều cụm ghế nhỏ");
        }
        return reason.toString();
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

        String canonicalSeatCodes() {
            return seats.stream()
                    .map(SeatNode::seatCode)
                    .sorted()
                    .collect(Collectors.joining(","));
        }
    }
}

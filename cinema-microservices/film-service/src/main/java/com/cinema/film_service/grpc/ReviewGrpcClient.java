package com.cinema.film_service.grpc;

import com.cinema.grpc.review.FilmRatingSummaryPayload;
import com.cinema.grpc.review.GetFilmRatingSummariesReply;
import com.cinema.grpc.review.GetFilmRatingSummariesRequest;
import com.cinema.grpc.review.ReviewInternalServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class ReviewGrpcClient {

    private final ReviewInternalServiceGrpc.ReviewInternalServiceBlockingStub reviewBlockingStub;

    public ReviewGrpcClient(GrpcChannelFactory channelFactory) {
        this.reviewBlockingStub = ReviewInternalServiceGrpc.newBlockingStub(channelFactory.createChannel("review"));
    }

    public Map<UUID, RatingSummary> getFilmRatingSummaries(List<UUID> filmIds) {
        if (filmIds == null || filmIds.isEmpty()) {
            return Map.of();
        }

        log.info("Calling review-service gRPC getFilmRatingSummaries for {} filmIds", filmIds.size());
        try {
            GetFilmRatingSummariesReply reply = reviewBlockingStub.getFilmRatingSummaries(
                    GetFilmRatingSummariesRequest.newBuilder()
                            .addAllFilmIds(filmIds.stream().map(UUID::toString).toList())
                            .build());

            if (!reply.getSuccess()) {
                log.warn(
                        "review-service gRPC getFilmRatingSummaries returned failure: errorKey={}, message={}, filmIds={}",
                        reply.getErrorKey(),
                        reply.getMessage(),
                        filmIds);
                return Map.of();
            }

            Map<UUID, RatingSummary> summaries = new HashMap<>();
            for (FilmRatingSummaryPayload payload : reply.getSummariesList()) {
                try {
                    UUID filmId = UUID.fromString(payload.getFilmId());
                    summaries.put(filmId, new RatingSummary(payload.getAverageRating(), payload.getReviewCount()));
                } catch (IllegalArgumentException ignored) {
                    // Ignore malformed IDs and fall back to zero values.
                    log.warn("review-service returned malformed filmId payload while resolving rating summary: {}", payload);
                }
            }
            log.info("review-service gRPC returned {} rating summaries for {} requested filmIds", summaries.size(), filmIds.size());
            return summaries;
        } catch (StatusRuntimeException ex) {
            log.warn("review-service gRPC call failed for filmIds={}: {}", filmIds, ex.getStatus(), ex);
            return Map.of();
        } catch (RuntimeException ex) {
            log.warn("Unexpected error while calling review-service gRPC for filmIds={}", filmIds, ex);
            return Map.of();
        }
    }

    public record RatingSummary(double averageRating, long reviewCount) {
    }
}

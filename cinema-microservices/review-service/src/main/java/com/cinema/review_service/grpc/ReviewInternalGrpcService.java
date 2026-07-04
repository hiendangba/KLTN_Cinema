package com.cinema.review_service.grpc;

import com.cinema.exception.ErrorCode;
import com.cinema.grpc.review.FilmRatingSummaryPayload;
import com.cinema.grpc.review.GetFilmRatingSummariesReply;
import com.cinema.grpc.review.GetFilmRatingSummariesRequest;
import com.cinema.grpc.review.ReviewInternalServiceGrpc;
import com.cinema.review_service.entity.enums.ReviewStatus;
import com.cinema.review_service.repository.ReviewRepository;
import com.cinema.review_service.repository.projection.ReviewRatingSummaryView;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReviewInternalGrpcService extends ReviewInternalServiceGrpc.ReviewInternalServiceImplBase implements BindableService {

    private final ReviewRepository reviewRepository;

    @Override
    public void getFilmRatingSummaries(
            GetFilmRatingSummariesRequest request,
            StreamObserver<GetFilmRatingSummariesReply> responseObserver) {
        try {
            List<UUID> filmIds = request.getFilmIdsList().stream()
                    .map(UUID::fromString)
                    .distinct()
                    .toList();

            if (filmIds.isEmpty()) {
                responseObserver.onNext(GetFilmRatingSummariesReply.newBuilder()
                        .setSuccess(true)
                        .setMessage("Rating summaries fetched successfully")
                        .build());
                responseObserver.onCompleted();
                return;
            }

            List<ReviewRatingSummaryView> summaries = reviewRepository.findRatingSummariesByFilmIds(filmIds, ReviewStatus.ACTIVE);
            LinkedHashMap<UUID, ReviewRatingSummaryView> summaryMap = new LinkedHashMap<>();
            for (ReviewRatingSummaryView summary : summaries) {
                if (summary != null && summary.getFilmId() != null) {
                    summaryMap.put(summary.getFilmId(), summary);
                }
            }

            GetFilmRatingSummariesReply.Builder replyBuilder = GetFilmRatingSummariesReply.newBuilder()
                    .setSuccess(true)
                    .setMessage("Rating summaries fetched successfully");

            for (UUID filmId : filmIds) {
                ReviewRatingSummaryView summary = summaryMap.get(filmId);
                if (summary == null) {
                    continue;
                }

                replyBuilder.addSummaries(FilmRatingSummaryPayload.newBuilder()
                        .setFilmId(filmId.toString())
                        .setAverageRating(roundToTwoDecimals(summary.getAverageRating()))
                        .setReviewCount(summary.getReviewCount() == null ? 0L : summary.getReviewCount())
                        .build());
            }

            responseObserver.onNext(replyBuilder.build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException ex) {
            responseObserver.onNext(GetFilmRatingSummariesReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INVALID_FORMAT.name())
                    .setMessage(ErrorCode.INVALID_FORMAT.getMessage())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while fetching film rating summaries", ex);
            responseObserver.onNext(GetFilmRatingSummariesReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INTERNAL_ERROR.name())
                    .setMessage(ErrorCode.INTERNAL_ERROR.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }

    private double roundToTwoDecimals(Double value) {
        if (value == null) {
            return 0.0d;
        }
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}

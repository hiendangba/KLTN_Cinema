package com.cinema.review_service.repository.projection;

import java.util.UUID;

public interface ReviewRatingSummaryView {
    UUID getFilmId();

    Double getAverageRating();

    Long getReviewCount();
}

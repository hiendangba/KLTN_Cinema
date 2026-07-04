package com.cinema.review_service.dto.request;

import com.cinema.dto.request.CursorPageRequest;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ReviewCursorPageRequest extends CursorPageRequest<ReviewField> {

    public int getLimitedSize() {
        return Math.min(getSizeOrDefault(), 10);
    }
}

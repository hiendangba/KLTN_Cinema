package com.cinema.dto.request;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class PageableUtils {

    public static Pageable toPageable(BasePageRequest request) {
        request.validate();

        if (request.getSortBy() == null || request.getSortBy().isEmpty()) {
            return PageRequest.of(request.getPage(), request.getSize());
        }

        Sort.Direction direction = Sort.Direction.fromString(request.getSortDirection().toUpperCase());
        Sort sort = Sort.by(direction, request.getSortBy());

        return PageRequest.of(request.getPage(), request.getSize(), sort);
    }

    public static Pageable toPageable(BasePageRequest request, String... defaultSortFields) {
        request.validate();

        if (request.getSortBy() == null || request.getSortBy().isEmpty()) {
            if (defaultSortFields.length > 0) {
                Sort sort = Sort.by(defaultSortFields[0]);
                return PageRequest.of(request.getPage(), request.getSize(), sort);
            }
            return PageRequest.of(request.getPage(), request.getSize());
        }

        Sort.Direction direction = Sort.Direction.fromString(request.getSortDirection().toUpperCase());
        Sort sort = Sort.by(direction, request.getSortBy());

        return PageRequest.of(request.getPage(), request.getSize(), sort);
    }
}

package com.cinema.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

public class PageResponseMapper {

    /**
     * Chuyển đổi Spring Data Page sang BasePageResponse
     */
    public static <T> BasePageResponse<T> toPageResponse(Page<T> page) {
        return new BasePageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements());
    }

    /**
     * Chuyển đổi danh sách và tổng số bản ghi sang BasePageResponse
     */
    public static <T> BasePageResponse<T> toPageResponse(List<T> data, int currentPage, int pageSize,
            long totalElements) {
        return new BasePageResponse<>(data, currentPage, pageSize, totalElements);
    }
}

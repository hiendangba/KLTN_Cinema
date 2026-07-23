package com.cinema.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class ResultResponse<T> {
    public List<ErrorResponse> errorResponse;
    public List<SuccessResponse<T>> successResponse;
}

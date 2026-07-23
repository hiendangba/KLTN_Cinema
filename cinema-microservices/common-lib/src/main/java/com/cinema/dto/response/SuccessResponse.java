package com.cinema.dto.response;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SuccessResponse<T> {
    private T data;
}
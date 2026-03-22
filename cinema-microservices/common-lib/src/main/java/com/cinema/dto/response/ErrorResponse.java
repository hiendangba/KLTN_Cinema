package com.cinema.dto.response;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    int index;
    String field;
    String code;
    String message;
    Object value;
    String timestamp;
}

package com.cinema.controller;

import com.cinema.dto.response.APIResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.time.LocalDateTime;
@Slf4j
public abstract class BaseController {

    protected <T> ResponseEntity<APIResponse<T>> ok(T data) {
        return ResponseEntity.ok(buildResponse(true, "SUCCESS", "Operation successful", data));
    }
    protected <T> ResponseEntity<APIResponse<T>> created(T data) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(buildResponse(true, "CREATED", "Created successfully", data));
    }

    protected ResponseEntity<Void> noContent() {
        return ResponseEntity.noContent().build();
    }

    private <T> APIResponse<T> buildResponse(boolean success, String code, String message, T data) {
        return APIResponse.<T>builder()
                .success(success)
                .code(code)
                .message(message)
                .path(getRequestPath())
                .timestamp(LocalDateTime.now())
                .data(data)
                .build();
    }

    private String getRequestPath() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                return attributes.getRequest().getRequestURI();
            }
        } catch (Exception e) {
            log.error("Failed to get request path", e);

        }
        return null;
    }
}
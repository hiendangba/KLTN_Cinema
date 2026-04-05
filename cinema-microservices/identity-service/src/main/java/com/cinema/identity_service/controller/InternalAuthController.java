package com.cinema.identity_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/auth")
public class InternalAuthController {

    @GetMapping("/check")
    public ResponseEntity<Void> authCheck() {
        return ResponseEntity.ok().build();
    }
}

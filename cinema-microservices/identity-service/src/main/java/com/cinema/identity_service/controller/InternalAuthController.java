package com.cinema.identity_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/auth")
public class InternalAuthController {

    @RequestMapping(path = { "/check", "/check/**", "/**" }, method = { RequestMethod.GET, RequestMethod.POST })
    public ResponseEntity<Void> authCheck() {
        return ResponseEntity.ok().build();
    }
}

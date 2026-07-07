package com.cinema.user_service.controller;

import com.cinema.Enum.SuccessMessage;
import com.cinema.controller.BaseController;
import com.cinema.dto.response.APIResponse;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.http.HeaderNames;
import com.cinema.http.RequestAuthUtils;
import com.cinema.user_service.dto.request.CustomerRankUpsertRequest;
import com.cinema.user_service.dto.response.CustomerRankResponse;
import com.cinema.user_service.services.CustomerRankService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/customer-ranks")
@RequiredArgsConstructor
public class CustomerRankController extends BaseController {

    private final CustomerRankService customerRankService;

    @PostMapping
    public ResponseEntity<APIResponse<ActionMessageResponse>> createRank(
            @Valid @RequestBody CustomerRankUpsertRequest request,
            HttpServletRequest httpRequest) {
        RequestAuthUtils.requireRole(httpRequest, HeaderNames.ROLE_ADMIN);
        return created(SuccessMessage.CREATED, customerRankService.createRank(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> updateRank(
            @PathVariable UUID id,
            @Valid @RequestBody CustomerRankUpsertRequest request,
            HttpServletRequest httpRequest) {
        RequestAuthUtils.requireRole(httpRequest, HeaderNames.ROLE_ADMIN);
        return ok(SuccessMessage.UPDATED, customerRankService.updateRank(id, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<APIResponse<CustomerRankResponse>> getRankById(
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {
        RequestAuthUtils.requireAnyRole(httpRequest, HeaderNames.ROLE_ADMIN, HeaderNames.ROLE_MANAGER,
                HeaderNames.ROLE_STAFF);
        return ok(SuccessMessage.FETCHED, customerRankService.getRankById(id));
    }

    @GetMapping
    public ResponseEntity<APIResponse<List<CustomerRankResponse>>> listRanks(HttpServletRequest httpRequest) {
        RequestAuthUtils.requireAnyRole(httpRequest, HeaderNames.ROLE_ADMIN, HeaderNames.ROLE_MANAGER,
                HeaderNames.ROLE_STAFF);
        return ok(SuccessMessage.LISTED, customerRankService.listRanks());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> deleteRank(
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {
        RequestAuthUtils.requireRole(httpRequest, HeaderNames.ROLE_ADMIN);
        return ok(SuccessMessage.DELETED, customerRankService.deleteRank(id));
    }
}

package com.cinema.user_service.services;

import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.user_service.dto.request.CustomerRankUpsertRequest;
import com.cinema.user_service.dto.response.CustomerRankResponse;
import com.cinema.user_service.dto.response.CustomerRankSnapshot;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface CustomerRankService {
    ActionMessageResponse createRank(CustomerRankUpsertRequest request);

    ActionMessageResponse updateRank(UUID id, CustomerRankUpsertRequest request);

    CustomerRankResponse getRankById(UUID id);

    List<CustomerRankResponse> listRanks();

    ActionMessageResponse deleteRank(UUID id);

    CustomerRankSnapshot resolveRank(BigDecimal lifetimePaidAmount);
}

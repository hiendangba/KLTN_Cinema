package com.cinema.payment_service.services.impl;

import com.cinema.payment_service.config.VietQrProperties;
import com.cinema.payment_service.dto.response.VietQrBankResponse;
import com.cinema.payment_service.dto.response.VietQrBanksApiResponse;
import com.cinema.payment_service.entity.BankCatalog;
import com.cinema.payment_service.repository.BankCatalogRepository;
import com.cinema.payment_service.services.VietQrService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Service
public class VietQrServiceImpl implements VietQrService {
    private static final Logger log = LoggerFactory.getLogger(VietQrServiceImpl.class);

    private final RestClient vietQrRestClient;
    private final VietQrProperties vietQrProperties;
    private final BankCatalogRepository bankCatalogRepository;

    public VietQrServiceImpl(
            RestClient vietQrRestClient,
            VietQrProperties vietQrProperties,
            BankCatalogRepository bankCatalogRepository) {
        this.vietQrRestClient = vietQrRestClient;
        this.vietQrProperties = vietQrProperties;
        this.bankCatalogRepository = bankCatalogRepository;
    }

    @Override
    @Transactional
    public List<VietQrBankResponse> getBanks() {
        if (bankCatalogRepository.count() == 0) {
            synchronized (this) {
                if (bankCatalogRepository.count() == 0) {
                    syncBanksFromVietQr();
                }
            }
        }

        return bankCatalogRepository.findAllByOrderByIdAsc().stream()
                .map(this::toBankResponse)
                .toList();
    }

    @Override
    @Transactional
    public int syncBanksMonthly() {
        synchronized (this) {
            return syncBanksFromVietQr();
        }
    }

    private void validateCredentials() {
        if (!StringUtils.hasText(vietQrProperties.getBanksPath())) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "VietQR banks path is missing. Configure VIETQR_BANKS_PATH");
        }
        if (!StringUtils.hasText(vietQrProperties.getApiKey()) || !StringUtils.hasText(vietQrProperties.getSecretKey())) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "VietQR credentials are missing. Configure VIETQR_API_KEY and VIETQR_SECRET_KEY");
        }
    }

    private int syncBanksFromVietQr() {
        validateCredentials();
        try {
            VietQrBanksApiResponse upstream = vietQrRestClient.get()
                    .uri(vietQrProperties.getBanksPath())
                    .header("x-client-id", vietQrProperties.getSecretKey())
                    .header("x-api-key", vietQrProperties.getApiKey())
                    .retrieve()
                    .body(VietQrBanksApiResponse.class);

            if (upstream == null || upstream.getData() == null || upstream.getData().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "VietQR returned empty bank data");
            }

            int syncCount = 0;
            for (VietQrBankResponse bank : upstream.getData()) {
                BankCatalog entity = bankCatalogRepository.findByVietQrBankId(bank.getId())
                        .orElseGet(BankCatalog::new);
                entity.setVietQrBankId(bank.getId());
                entity.setName(bank.getName());
                entity.setCode(bank.getCode());
                entity.setBin(bank.getBin());
                entity.setShortName(bank.getShortName());
                entity.setLogo(bank.getLogo());
                entity.setTransferSupported(bank.getTransferSupported());
                entity.setLookupSupported(bank.getLookupSupported());
                bankCatalogRepository.save(entity);
                syncCount++;
            }
            log.info("Synced {} banks from VietQR", syncCount);
            return syncCount;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Cannot sync bank catalog from VietQR", ex);
        }
    }

    private VietQrBankResponse toBankResponse(BankCatalog entity) {
        VietQrBankResponse response = new VietQrBankResponse();
        response.setId(entity.getVietQrBankId());
        response.setName(entity.getName());
        response.setCode(entity.getCode());
        response.setBin(entity.getBin());
        response.setShortName(entity.getShortName());
        response.setLogo(entity.getLogo());
        response.setTransferSupported(entity.getTransferSupported());
        response.setLookupSupported(entity.getLookupSupported());
        return response;
    }
}

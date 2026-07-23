package com.cinema.payment_service.scheduler;

import com.cinema.payment_service.services.VietQrService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class VietQrBankSyncScheduler {
    private static final Logger log = LoggerFactory.getLogger(VietQrBankSyncScheduler.class);

    private final VietQrService vietQrService;

    public VietQrBankSyncScheduler(VietQrService vietQrService) {
        this.vietQrService = vietQrService;
    }

    @Scheduled(cron = "${vietqr.sync-cron:0 0 3 1 * *}")
    public void syncBankCatalogMonthly() {
        try {
            int synced = vietQrService.syncBanksMonthly();
            log.info("Monthly VietQR sync completed. Synced {} banks.", synced);
        } catch (Exception ex) {
            log.error("Monthly VietQR sync failed", ex);
        }
    }
}

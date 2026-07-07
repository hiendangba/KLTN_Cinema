package com.cinema.payment_service.repository;

import com.cinema.payment_service.entity.PaymentTransactionPromotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PaymentTransactionPromotionRepository extends JpaRepository<PaymentTransactionPromotion, UUID> {

    List<PaymentTransactionPromotion> findAllByPaymentTransactionId(UUID paymentTransactionId);

    List<PaymentTransactionPromotion> findAllByPaymentTransactionIdOrderByApplyOrderAsc(UUID paymentTransactionId);

    List<PaymentTransactionPromotion> findAllByPaymentTransactionIdIn(Collection<UUID> paymentTransactionIds, Sort sort);

    @Query(value = """
            select count(*)
            from payment_transaction_promotion ptp
            join payment_transaction pt on pt.id = ptp.payment_transaction_id
            where ptp.promotion_id = :promotionId
              and pt.paid_at is not null
            """, nativeQuery = true)
    long countReachedPaidUsageByPromotionId(@Param("promotionId") UUID promotionId);

    @Query(value = """
            select case when count(*) > 0 then true else false end
            from payment_transaction_promotion ptp
            join payment_transaction pt on pt.id = ptp.payment_transaction_id
            where ptp.promotion_id = :promotionId
              and pt.user_id = :userId
              and pt.paid_at is not null
            """, nativeQuery = true)
    boolean existsReachedPaidUsageByPromotionIdAndUserId(@Param("promotionId") UUID promotionId,
                                                         @Param("userId") UUID userId);

    @Query(value = """
            select ptp.promotion_id as promotionId, count(*) as usedCount
            from payment_transaction_promotion ptp
            join payment_transaction pt on pt.id = ptp.payment_transaction_id
            where ptp.promotion_id in (:promotionIds)
              and pt.paid_at is not null
            group by ptp.promotion_id
            """, nativeQuery = true)
    List<PromotionUsageSummary> summarizeReachedPaidUsageByPromotionIds(@Param("promotionIds") Collection<UUID> promotionIds);

    void deleteByPaymentTransactionId(UUID paymentTransactionId);

    interface PromotionUsageSummary {
        UUID getPromotionId();

        long getUsedCount();
    }
}

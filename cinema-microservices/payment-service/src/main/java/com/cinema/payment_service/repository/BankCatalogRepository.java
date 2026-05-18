package com.cinema.payment_service.repository;

import com.cinema.payment_service.entity.BankCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankCatalogRepository extends JpaRepository<BankCatalog, Long> {
    List<BankCatalog> findAllByOrderByIdAsc();

    Optional<BankCatalog> findByVietQrBankId(Integer vietQrBankId);
}

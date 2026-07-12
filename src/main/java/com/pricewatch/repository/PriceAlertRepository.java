package com.pricewatch.repository;

import com.pricewatch.model.PriceAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PriceAlertRepository extends JpaRepository<PriceAlert, Long> {
    List<PriceAlert> findByUserEmailOrderByCreatedAtDesc(String userEmail);

    Optional<PriceAlert> findByIdAndUserEmail(Long id, String userEmail);
}

package com.pricewatch.repository;

import com.pricewatch.model.Price;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PriceRepository extends JpaRepository<Price, Long> {
    List<Price> findByProductIdOrderByRecordedAtDesc(Long productId);

    Optional<Price> findTopByProductIdOrderByAmountAsc(Long productId);

    Optional<Price> findTopByProductIdOrderByAmountDesc(Long productId);
}

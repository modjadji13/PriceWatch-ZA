package com.pricewatch.repository;

import com.pricewatch.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByCategoryIgnoreCase(String category);

    Optional<Product> findByNameIgnoreCaseAndCategoryIgnoreCase(String name, String category);
}

package com.pricewatch.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "prices")
public class Price {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String store;

    @Column(nullable = false)
    private double amount;

    @Column(nullable = false)
    private LocalDateTime recordedAt;

    // The exact matched item behind this price, captured so a deal's title, image
    // and discount all derive from one stable item rather than the broad search
    // term. productUrl is the store's own product link and is the most reliable
    // key for "same item at the same store over time"; itemName is the fallback
    // key when a store exposes no link. Nullable: rows predating this and the
    // agent-import path carry no item identity.
    @Column(name = "item_name")
    private String itemName;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "product_url")
    private String productUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = true)
    @JsonIgnore
    private Product product;

    public Price() {
    }

    public Price(String store, double amount, LocalDateTime recordedAt, Product product) {
        this.store = store;
        this.amount = amount;
        this.recordedAt = recordedAt;
        this.product = product;
    }

    public Price(String store, double amount, LocalDateTime recordedAt, Product product,
                 String itemName, String imageUrl, String productUrl) {
        this.store = store;
        this.amount = amount;
        this.recordedAt = recordedAt;
        this.product = product;
        this.itemName = itemName;
        this.imageUrl = imageUrl;
        this.productUrl = productUrl;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStore() {
        return store;
    }

    public void setStore(String store) {
        this.store = store;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(LocalDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getProductUrl() {
        return productUrl;
    }

    public void setProductUrl(String productUrl) {
        this.productUrl = productUrl;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }
}

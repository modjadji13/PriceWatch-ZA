package com.pricewatch.controller;

import com.pricewatch.model.PriceAlert;
import com.pricewatch.repository.PriceAlertRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {
    private final PriceAlertRepository priceAlertRepository;

    public AlertController(PriceAlertRepository priceAlertRepository) {
        this.priceAlertRepository = priceAlertRepository;
    }

    public record CreateAlertRequest(String userEmail, String productName, Double thresholdAmount) {
    }

    @GetMapping
    public List<PriceAlert> list(@RequestParam String userEmail) {
        return priceAlertRepository.findByUserEmailOrderByCreatedAtDesc(userEmail);
    }

    @PostMapping
    public ResponseEntity<PriceAlert> create(@RequestBody CreateAlertRequest request) {
        if (isBlank(request.userEmail()) || isBlank(request.productName())
            || request.thresholdAmount() == null || request.thresholdAmount() <= 0) {
            return ResponseEntity.badRequest().build();
        }

        PriceAlert alert = new PriceAlert(
            request.userEmail(),
            request.productName(),
            request.thresholdAmount(),
            true,
            LocalDateTime.now());
        return ResponseEntity.ok(priceAlertRepository.save(alert));
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<PriceAlert> toggle(@PathVariable Long id, @RequestParam String userEmail) {
        return priceAlertRepository.findByIdAndUserEmail(id, userEmail)
            .map(alert -> {
                alert.setActive(!alert.isActive());
                return ResponseEntity.ok(priceAlertRepository.save(alert));
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @RequestParam String userEmail) {
        return priceAlertRepository.findByIdAndUserEmail(id, userEmail)
            .map(alert -> {
                priceAlertRepository.delete(alert);
                return ResponseEntity.noContent().<Void>build();
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

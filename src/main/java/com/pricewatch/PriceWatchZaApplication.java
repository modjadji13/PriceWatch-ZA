package com.pricewatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class PriceWatchZaApplication {
    public static void main(String[] args) {
        SpringApplication.run(PriceWatchZaApplication.class, args);
    }
}

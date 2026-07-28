package com.pricewatch.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Widens the prices item/image/url columns to TEXT on startup. They were first
 * created as varchar(255), but product names and URLs routinely exceed that, so
 * price-history inserts failed on databases created before the entity mapped
 * them as TEXT. Hibernate's ddl-auto=update never widens an existing column, so
 * this heals such databases in place. Idempotent and cheap: it only alters a
 * column that is still a bounded varchar, and never fails startup.
 */
@Component
public class PriceSchemaMigration implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(PriceSchemaMigration.class);
    private static final List<String> COLUMNS = List.of("item_name", "image_url", "product_url");

    private final JdbcTemplate jdbcTemplate;

    public PriceSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String column : COLUMNS) {
            try {
                // character_maximum_length is null for TEXT and set for varchar(n).
                Integer maxLength = jdbcTemplate.queryForObject(
                    "select character_maximum_length from information_schema.columns "
                        + "where table_name = 'prices' and column_name = ?",
                    Integer.class, column);
                if (maxLength != null) {
                    // Column name comes from the fixed whitelist above, not user input.
                    jdbcTemplate.execute("ALTER TABLE prices ALTER COLUMN " + column + " TYPE text");
                    logger.info("Migrated prices.{} from varchar({}) to TEXT", column, maxLength);
                }
            } catch (Exception e) {
                logger.warn("Skipping TEXT migration for prices.{}: {}", column, e.getMessage());
            }
        }
    }
}

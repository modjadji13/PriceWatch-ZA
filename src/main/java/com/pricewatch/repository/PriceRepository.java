package com.pricewatch.repository;

import com.pricewatch.model.Price;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PriceRepository extends JpaRepository<Price, Long> {
    List<Price> findByProductIdOrderByRecordedAtDesc(Long productId);

    Optional<Price> findTopByProductIdOrderByAmountAsc(Long productId);

    Optional<Price> findTopByProductIdOrderByAmountDesc(Long productId);

    // Per (product, store, item): the cheapest price seen recently vs the cheapest
    // seen over the prior baseline window, for one stable item — not the broad
    // search term. item_key is the store's product URL when present (the reliable
    // "same item at the same store over time" key) and the item name otherwise, so
    // a recent minimum is only ever compared against the SAME item's own prior
    // minimum. Rows with no captured item identity (older history, agent imports)
    // have a blank item_key and are excluded, since their drops cannot be trusted.
    // Columns: product_id, name, category, store, item_name, image_url,
    // current_min, prior_min. current_min/prior_min may be null when an item has
    // data in only one of the two windows; the service treats those as "no deal".
    @Query(value = """
        select pr.product_id,
               p.name,
               p.category,
               pr.store,
               max(pr.item_name) as item_name,
               max(pr.image_url) filter (where pr.image_url is not null and pr.image_url <> '') as image_url,
               min(pr.amount) filter (where pr.recorded_at >= :recentSince) as current_min,
               min(pr.amount) filter (where pr.recorded_at < :recentSince and pr.recorded_at >= :baselineSince) as prior_min
        from prices pr
        join products p on p.id = pr.product_id
        where pr.recorded_at >= :baselineSince
          and coalesce(nullif(pr.product_url, ''), pr.item_name, '') <> ''
        group by pr.product_id, p.name, p.category, pr.store,
                 coalesce(nullif(pr.product_url, ''), pr.item_name)
        """, nativeQuery = true)
    List<Object[]> findDealCandidates(
        @Param("recentSince") LocalDateTime recentSince,
        @Param("baselineSince") LocalDateTime baselineSince);
}

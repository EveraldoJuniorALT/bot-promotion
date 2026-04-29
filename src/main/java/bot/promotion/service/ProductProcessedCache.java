package bot.promotion.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProductProcessedCache {
    private final Map<String, CachedProductData> productCache = new ConcurrentHashMap<>();
    private final Set<String> secondaryGroupCache = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Clock clock;

    @Autowired
    public ProductProcessedCache(Clock clock) {
        this.clock = clock;
    }

    @AllArgsConstructor
    @Getter
    public static class CachedProductData {
        private List<String> affiliateLinks;
        private BigDecimal coinPercentage;
        private LocalDateTime cachedAt;
    }

    public CachedProductData getCachedProduct(String productId) {
        CachedProductData cacheProduct = productCache.get(productId);
        if (cacheProduct == null) return null;

        LocalDateTime now = LocalDateTime.now();
        long hoursDiff = ChronoUnit.HOURS.between(cacheProduct.getCachedAt(), now);
        if (hoursDiff >= 8) {
            productCache.remove(productId);
            return null;
        }

        if (cacheProduct.getCachedAt().toLocalDate().isBefore(LocalDate.now())) {
            productCache.remove(productId);
            return null;
        }
        return cacheProduct;
    }

    public void saveToCache(String productId, List<String> affiliateLinks, BigDecimal coinPercentage) {
        if (productCache.containsKey(productId)) return;

        productCache.put(productId, new CachedProductData(
                affiliateLinks,
                coinPercentage,
                LocalDateTime.now(clock)
        ));
    }

    public boolean isSecondaryGroupProcessed(String productId) {
        return secondaryGroupCache.contains(productId);
    }

    public void markSecondaryGroupAsProcessed(String productId) {
        secondaryGroupCache.add(productId);
    }
}

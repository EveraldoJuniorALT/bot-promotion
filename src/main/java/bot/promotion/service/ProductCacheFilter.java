package bot.promotion.service;

import bot.promotion.dto.HotProduct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ProductCacheFilter {
    private final Clock clock;
    private Map<String, HotProduct> oldListProducts = new HashMap<>();
    private LocalDateTime cacheTime;
    private static final Duration CACHE_DURATION = Duration.ofHours(6);

    @Autowired
    public ProductCacheFilter(Clock clock) {
        this.clock = clock;
        this.cacheTime = LocalDateTime.now(clock);
    }

    public List<HotProduct> compareAndFilter(List<HotProduct> newList) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (oldListProducts.isEmpty()) {
            updateCache(newList, now);
            return newList;
        }

        if (Duration.between(cacheTime, now).compareTo(CACHE_DURATION) >= 0) {
            updateCache(newList, now);
            return newList;
        }

        List<HotProduct> productsToProcess = new ArrayList<>();
        for (HotProduct newProduct : newList) {
            HotProduct oldProduct = oldListProducts.get(newProduct.getProductId());
            if (oldProduct == null) {
                productsToProcess.add(newProduct);
            }

            if (isCheaper(newProduct, oldProduct)) {
                productsToProcess.add(newProduct);
            }
        }
        return productsToProcess;
    }

    private void updateCache(List<HotProduct> newList, LocalDateTime currentTime) {
        this.oldListProducts = newList.stream()
                .collect(Collectors.toMap(HotProduct::getProductId, Function.identity(), (p1, p2) -> p2));
        this.cacheTime = currentTime;
    }

    private boolean isCheaper(HotProduct newProduct, HotProduct oldProduct) {
        try {
            BigDecimal newPrice = new BigDecimal(newProduct.getSalePriceApp());
            BigDecimal oldPrice = new BigDecimal(oldProduct.getSalePriceApp());
            return newPrice.compareTo(oldPrice) < 0;
        } catch (Exception e) {
            System.out.println("Error comparing prices " + e.getMessage());
            return false;
        }
    }
}

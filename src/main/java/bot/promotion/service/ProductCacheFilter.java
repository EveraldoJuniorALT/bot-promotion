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
    private final FinalPriceService finalPriceService;
    private final NotificationService notify;
    private final Clock clock;
    private Map<String, HotProduct> oldListProducts = new HashMap<>();
    private LocalDateTime cacheTime;
    private static final Duration CACHE_DURATION = Duration.ofHours(12);

    @Autowired
    public ProductCacheFilter(FinalPriceService finalPriceService, NotificationService notify, Clock clock) {
        this.finalPriceService = finalPriceService;
        this.notify = notify;
        this.clock = clock;
        this.cacheTime = LocalDateTime.now(clock);
    }

    public List<HotProduct> compareAndFilter(List<HotProduct> newList) {
        LocalDateTime now = LocalDateTime.now(clock);
        /*
        * First time initialization of the cache
        * If returns nothing on first run to avoid reaching
        * the API Request limit and being blocked for a few seconds.
        */
        if (oldListProducts.isEmpty()) {
            updateCache(newList, now);
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
                oldListProducts.put(newProduct.getProductId(), newProduct);
                continue;
            }

            if (isCheaper(newProduct, oldProduct)) {
                productsToProcess.add(newProduct);
            }
        }
        return productsToProcess;
    }

    private void updateCache(List<HotProduct> newList, LocalDateTime currentTime) {
        this.oldListProducts = newList.stream()
                .collect(Collectors.toMap(HotProduct::getProductId, Function.identity(), (p1, p2) -> p1));
        this.cacheTime = currentTime;
    }

    private boolean isCheaper(HotProduct newProduct, HotProduct oldProduct) {
        try {
            BigDecimal newPrice = finalPriceService.calculateFinalPrice(newProduct, newProduct.getAffiliateLink());
            BigDecimal oldPrice = finalPriceService.calculateFinalPrice(oldProduct, oldProduct.getAffiliateLink());

            if (newPrice.compareTo(oldPrice) >= 0) {
                return false;
            }

            BigDecimal difference = newPrice.subtract(oldPrice);
            BigDecimal minDiscount = new BigDecimal("0.50");

            return difference.compareTo(minDiscount) >= 0;
        } catch (Exception e) {
            notify.sendErrorMessage("Error comparing prices ", e);
            return false;
        }
    }
}

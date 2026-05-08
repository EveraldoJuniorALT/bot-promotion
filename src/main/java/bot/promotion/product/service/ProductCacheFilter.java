package bot.promotion.product.service;

import bot.promotion.product.dto.HotProduct;
import bot.promotion.product.dto.SkuProduct;
import bot.promotion.product.service.domain.ChooseBetterSku;
import bot.promotion.telegram.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProductCacheFilter {
    private final FinalPriceService finalPriceService;
    private final NotificationService notify;
    private final Clock clock;
    private final ChooseBetterSku chooseBetterSku;

    private Map<String, HotProduct> oldListProducts = new HashMap<>();
    private final Map<String, SkuProduct> oldListSkuProduct = new HashMap<>();
    private static final Duration CACHE_DURATION_HOURS = Duration.ofHours(12);

    private LocalDateTime cacheTime;
    private LocalDate cacheDateToday;

    public List<HotProduct> simpleFilter(List<HotProduct> products) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (oldListProducts.isEmpty()) {
            updateCache(products, now);
            return products;
        }

        if (cacheDateToday != now.toLocalDate() || Duration.between(cacheTime, now).compareTo(CACHE_DURATION_HOURS) >= 0) {
            updateCache(products, now);
            return products;
        }

        List<HotProduct> productsToProcess = new ArrayList<>();
        for (HotProduct newProduct : products) {
            HotProduct oldProduct = oldListProducts.get(newProduct.getProductId());
            if (oldProduct == null) {
                productsToProcess.add(newProduct);
                oldListProducts.put(newProduct.getProductId(), newProduct);
            }
        }
        return productsToProcess;
    }

    public SkuProduct compareAndFilter(HotProduct hotProduct, List<SkuProduct> skuAllProducts) {
        List<SkuProduct> productsToProcess = new ArrayList<>();
        for (SkuProduct newSkuProduct : skuAllProducts) {
            SkuProduct oldSkuProduct = oldListSkuProduct.get(newSkuProduct.getSkuId());
            if (oldSkuProduct == null) {
                productsToProcess.add(newSkuProduct);
                oldListSkuProduct.put(newSkuProduct.getSkuId(), newSkuProduct);
                continue;
            }

            if (isCheaper(hotProduct, newSkuProduct, oldSkuProduct)) {
                productsToProcess.add(newSkuProduct);
            }
        }
        return chooseBetterSku.chooseSkuProduct(productsToProcess);
    }

    private void updateCache(List<HotProduct> newList, LocalDateTime currentTime) {
        this.oldListProducts = newList.stream()
                .collect(Collectors.toMap(HotProduct::getProductId, Function.identity(), (p1, p2) -> p1));
        this.oldListSkuProduct.clear();
        this.cacheTime = currentTime;
        this.cacheDateToday = currentTime.toLocalDate();
    }

    private boolean isCheaper(HotProduct hotProduct, SkuProduct newSkuProduct, SkuProduct oldSkuProduct) {
        try {
            BigDecimal fixedCoinDiscount = new BigDecimal("1.00");
            BigDecimal newPrice = finalPriceService.calculateFinalPrice(hotProduct, newSkuProduct, fixedCoinDiscount);
            BigDecimal oldPrice = finalPriceService.calculateFinalPrice(hotProduct, oldSkuProduct, fixedCoinDiscount);

            if (newPrice.compareTo(oldPrice) >= 0) {
                return false;
            }

            BigDecimal difference = oldPrice.subtract(newPrice);
            BigDecimal minDiscount = new BigDecimal("0.50");

            return difference.compareTo(minDiscount) >= 0;
        } catch (Exception e) {
            notify.sendErrorMessage("Error comparing prices ", e);
            return false;
        }
    }
}

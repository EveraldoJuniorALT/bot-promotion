package bot.promotion.service;

import bot.promotion.client.AliexpressApiClient;
import bot.promotion.client.FetchShippingInfo;
import bot.promotion.client.SkuProductInfo;
import bot.promotion.core.util.BrandAndModel;
import bot.promotion.core.util.BrandsAndModelsFilter;
import bot.promotion.dto.*;
import bot.promotion.entity.Product;
import bot.promotion.entity.ProductVariant;
import bot.promotion.service.persistence.ProductPersistenceManager;
import bot.promotion.telegram.formatter.TelegramMessageFormatter;
import bot.promotion.telegram.service.NotificationService;
import bot.promotion.telegram.service.TelegramSenderService;
import bot.promotion.validator.PublishEligibilityValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final AliexpressApiClient fetchHotProducts;
    private final TelegramSenderService telegramSenderService;
    private final TelegramMessageFormatter formatter;
    private final ProductUrlService urlService;
    private final SkuProductInfo skuProductInfo;
    private final ProductCacheFilter productCacheFilter;
    private final BrandsAndModelsFilter brandsModels;
    private final FetchShippingInfo shippingInfo;
    private final NotificationService notify;
    private final FinalPriceService finalPriceService;
    private final AliexpressCoinService aliexpressCoinService;
    private final PublishEligibilityValidator publishEligibility;
    private final ProductProcessedCache productProcessedCache;
    private final Clock clock;
    private final ProductPersistenceManager persistenceManager;

    public void fetchHotProducts() {
        List<BrandAndModel> brandsAndModels = brandsModels.getBrandsAndModels();
        List<HotProduct> productsAfterFiltration = new ArrayList<>();

        for (BrandAndModel brands : brandsAndModels) {
            String brand = brands.getBrands();
            List<String> acceptedModels = brands.getModelsAccepted();
            List<String> excludedModels = brands.getModelsExcluded();

            productsAfterFiltration.addAll(fetchProductsForKeyword(brand, acceptedModels, excludedModels));
        }
        processHotProducts(productsAfterFiltration);
    }

    private List<HotProduct> fetchProductsForKeyword(String brand, List<String> acceptedModels, List<String> excludedModels) {
        List<HotProduct> allProducts = new ArrayList<>();
        processToFetchHotProducts(brand, allProducts);

        filterAllProducts(allProducts, acceptedModels, excludedModels);
        return allProducts;
    }

    private void processToFetchHotProducts(String keyword, List<HotProduct> allProducts) {
        int currentPage = 1;
        while (true) {
            HotProductResponse responseApi = fetchHotProducts.getHotProduct(currentPage, keyword);
            if (responseApi == null ||
                    responseApi.getRespResult() == null ||
                    responseApi.getRespResult().getResult() == null ||
                    responseApi.getRespResult().getResult().getProductsList() == null ||
                    responseApi.getRespResult().getResult().getProductsList().isEmpty()) {
                break;
            }
            allProducts.addAll(responseApi.getRespResult().getResult().getProductsList());
            currentPage++;
        }
    }

    private void filterAllProducts(List<HotProduct> products, List<String> models, List<String> excludedModels) {
        Set<String> seenProductIds = new HashSet<>();

        products.removeIf(product ->
                isInvalidProduct(product) ||
                        !isUnique(product, seenProductIds) ||
                        matchesRequiredModels(product, models) ||
                        matchesExcludedModels(product, excludedModels)
        );
    }

    private boolean isInvalidProduct(HotProduct product) {
        if (product.getProductId() == null || product.getProductId().isBlank()) return true;
        if (product.getSalePriceApp() == null || product.getSalePriceApp().isBlank()) return true;
        if (product.getSkuId() == null || product.getSkuId().isBlank()) return true;
        if (product.getImageUrl() == null || product.getImageUrl().isBlank()) return true;

        return isLowRated(product);
    }

    private boolean isLowRated(HotProduct product) {
        String rate = product.getEvaluateRate();
        if (rate == null || rate.isBlank()) return true;
        try {
            double rateValue = Double.parseDouble(rate.replace("%", ""));
            return rateValue < 80;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private boolean isUnique(HotProduct product, Set<String> seenIds) {
        return seenIds.add(product.getProductId());
    }

    private boolean matchesRequiredModels(HotProduct product, List<String> models) {
        if (models == null || models.isEmpty()) return false;
        String titleLower = product.getProductTitle().toLowerCase();
        return models.stream().noneMatch(titleLower::contains);
    }

    private boolean matchesExcludedModels(HotProduct product, List<String> excludedModels) {
        if (excludedModels == null || excludedModels.isEmpty()) return false;
        String titleLower = product.getProductTitle().toLowerCase();
        return excludedModels.stream().anyMatch(titleLower::contains);
    }

    private void processHotProducts(List<HotProduct> products) {
        if (products == null || products.isEmpty()) return;

        List<HotProduct> filteredProducts = productCacheFilter.simpleFilter(products);
        if (filteredProducts.isEmpty()) return;

        List<String> productIds = filteredProducts.stream()
                .map(HotProduct::getProductId)
                .toList();

        List<Product> existingDbProducts = persistenceManager.findProductsBatch(productIds);
        Map<String, Product> dBProductsMap = new HashMap<>();
        if (!existingDbProducts.isEmpty()) {
            dBProductsMap = existingDbProducts.stream().collect(Collectors.toMap(Product::getProductId, existingProduct -> existingProduct));
        }

        List<HotProduct> dBExistingProducts = new ArrayList<>();
        List<HotProduct> dBNoExistingProducts = new ArrayList<>();

        for (HotProduct hp : filteredProducts) {
            if (dBProductsMap.containsKey(hp.getProductId())) {
                dBExistingProducts.add(hp);
                continue;
            }
            dBNoExistingProducts.add(hp);
        }

        if (!dBExistingProducts.isEmpty()) {
            processExistingDbProducts(dBExistingProducts, dBProductsMap);
        }
        if (!dBNoExistingProducts.isEmpty()) {
            processNoExistingDbProducts(dBNoExistingProducts);
        }
    }

    private void processExistingDbProducts(List<HotProduct> hotProductsDbExisting, Map<String, Product> dBProductsMap) {
        for (HotProduct hotProduct : hotProductsDbExisting) {
            Product productEntity = dBProductsMap.get(hotProduct.getProductId());
            if (productEntity == null) continue;

            List<SkuProduct> skuAllProduct = getOrBuildSku(hotProduct);
            SkuProduct bestSkuProduct = productCacheFilter.compareAndFilter(hotProduct, skuAllProduct);
            if (bestSkuProduct == null) continue;
            processProductToPublish(hotProduct, productEntity, bestSkuProduct, skuAllProduct);
        }
    }

    private void processProductToPublish(HotProduct hotProduct, Product productEntity, SkuProduct bestSkuProduct, List<SkuProduct> skuAllProduct) {
        boolean isPostedIn72hOrLess = postedIn72hOrLess(productEntity);
        boolean isToday = isPostedToday(productEntity);
        List<String> affiliateLinks = isPostedIn72hOrLess ? productEntity.getAffiliateLinks() : urlService.createCoinUrl(hotProduct.getProductId());
        if (affiliateLinks == null || affiliateLinks.isEmpty()) return;

        BigDecimal discountCoinValue = isToday ? productEntity.getDiscountCoinValue() : aliexpressCoinService.processLink(affiliateLinks.getFirst());
        if (discountCoinValue == null) return;

        BigDecimal averagePrice = getAveragePrice(productEntity, bestSkuProduct);
        BigDecimal currentPrice = finalPriceService.calculateFinalPrice(hotProduct, bestSkuProduct, discountCoinValue);
        if (isDataPriceValid(averagePrice, currentPrice)) return;

        boolean isEligible = publishEligibility.isEligibleForPublishing(currentPrice, averagePrice, isToday);
        if (isEligible) {
            publishProduct(hotProduct, bestSkuProduct, affiliateLinks, discountCoinValue, true);
            persistenceManager.updateProduct(productEntity, hotProduct, skuAllProduct, affiliateLinks, discountCoinValue);
        }
    }

    // Checks if it was posted in the last 72 hours or less, to reduce requests
    private boolean postedIn72hOrLess(Product productEntity) {
        if (productEntity.getLastPostedOn() == null) return false;

        LocalDateTime lastUpdate = productEntity.getLastPostedOn();
        LocalDateTime now = LocalDateTime.now(clock);

        return Duration.between(lastUpdate, now).toHours() <= 72;
    }

    private boolean isPostedToday(Product productEntity) {
        if (productEntity.getLastPostedOn() == null) return false;

        LocalDate lastUpdate = productEntity.getLastPostedOn().toLocalDate();
        LocalDate today = LocalDate.now(clock);

        return lastUpdate != null && lastUpdate.equals(today);
    }

    /*
     * This method is responsible for processing products that do not exist in the database.
     * Only a few basic data are created, and they will be published in a secondary group,
     * where it will be available for analysis whether they will be saved in the database,
     * and at another time treated and published as a promotion.
     * This is part of one of the rules and thus speeds up the processing of products.
     */
    private void processNoExistingDbProducts(List<HotProduct> hotProductsNoDbExisting) {
        for (HotProduct hp : hotProductsNoDbExisting) {
            if (productProcessedCache.isSecondaryGroupProcessed(hp.getProductId())) continue;

            List<String> affiliateLinks = List.of(hp.getProductLinkPc());// Sets a default product link to avoid delays in processing products without much importance
            SkuProduct skuProduct = buildSkuProduct(hp).getFirst();

            BigDecimal discountCoinValue = new BigDecimal("1.00");// Sets a generic default value to avoid delays in processing products without much importance

            publishProduct(hp, skuProduct, affiliateLinks, discountCoinValue, false);
            productProcessedCache.markSecondaryGroupAsProcessed(hp.getProductId());
        }
    }

    private boolean isDataPriceValid(BigDecimal averagePrice, BigDecimal currentPrice) {
        return averagePrice == null || currentPrice == null;
    }

    private List<SkuProduct> getOrBuildSku(HotProduct product) {
        List<SkuProduct> skuProducts = processToFetchSkuProducts(product);
        if (skuProducts != null && !skuProducts.isEmpty()) {
            skuProducts.forEach(skuProduct -> {
                if (isImageMissing(skuProduct)) {
                    skuProduct.setSkuImage(product.getImageUrl());
                }
            });
            return skuProducts;
        }
        return buildSkuProduct(product);
    }

    private List<SkuProduct> processToFetchSkuProducts(HotProduct product) {
        SkuProductResponse response = skuProductInfo.getSkuProduct(product.getProductId());
        if (response != null &&
                response.getRespResult() != null &&
                response.getRespResult().getResult() != null &&
                response.getRespResult().getResult().getSkuProductsList() != null &&
                !response.getRespResult().getResult().getSkuProductsList().isEmpty()) {
            return response.getRespResult().getResult().getSkuProductsList();
        }
        notify.sendWarningMessage("No SKU products found for product ID: " + product.getProductId());
        return null;
    }

    private boolean isImageMissing(SkuProduct skuProduct) {
        return skuProduct.getSkuImage() == null || skuProduct.getSkuImage().isBlank();
    }

    private List<SkuProduct> buildSkuProduct(HotProduct product) {
        ShippingInfo shippingInfo = processToFetchShippingInfo(product);

        SkuProduct sku = new SkuProduct();
        sku.setSkuId(product.getSkuId());
        sku.setSalePrice(product.getSalePriceApp());
        sku.setSkuImage(product.getImageUrl());
        sku.setModelo("default");
        sku.setSkuProperties("default");
        if (shippingInfo != null && !shippingInfo.getShipFromCountry().isBlank()) {
            sku.setShipFromCountry(shippingInfo.getShipFromCountry());
        }
        if (shippingInfo != null && !shippingInfo.getShippingFee().isBlank()) {
            sku.setShippingFees(shippingInfo.getShippingFee());
        }

        List<SkuProduct> skuList = new ArrayList<>();
        skuList.add(sku);
        return skuList;
    }


    private ShippingInfo processToFetchShippingInfo(HotProduct product) {
        ShippingInfoResponse response = shippingInfo.getShippingInfo(product);
        if (response != null &&
                response.getRespResult() != null &&
                response.getRespResult().getShippingInfo() != null) {
            return response.getRespResult().getShippingInfo();
        }
        notify.sendWarningMessage("No shipping info found for product ID: " + product.getProductId());
        return null;
    }

    private BigDecimal getAveragePrice(Product productEntity, SkuProduct bestSku) {
        /*
         * First, it checks if a variant with the same SKU ID exists
         * otherwise, it takes the lowest average price of all existing variants
         */
        ProductVariant variant = productEntity.getVariants().stream()
                .filter(v -> v.getSkuId().equals(bestSku.getSkuId()))
                .findFirst()
                .orElse(null);
        if (variant != null) return variant.getAveragePrice();

        return productEntity.getVariants().stream()
                .map(ProductVariant::getAveragePrice)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private void publishProduct(HotProduct product, SkuProduct skuProduct, List<String> affiliateLinks, BigDecimal coinPercentageDiscount, boolean isPriority) {
        telegramSenderService.sendPhotoMessage(skuProduct.getSkuImage(),
                formatter.formatMessage(product, skuProduct, affiliateLinks, coinPercentageDiscount), isPriority);
    }
}

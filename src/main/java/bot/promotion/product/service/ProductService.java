package bot.promotion.product.service;

import bot.promotion.aliexpress.client.AliexpressApiClient;
import bot.promotion.aliexpress.client.FetchShippingInfo;
import bot.promotion.aliexpress.client.SkuProductInfo;
import bot.promotion.aliexpress.dto.HotProductResponse;
import bot.promotion.aliexpress.dto.ShippingInfoResponse;
import bot.promotion.aliexpress.dto.SkuProductResponse;
import bot.promotion.aliexpress.service.AliexpressCoinService;
import bot.promotion.core.util.BrandAndModel;
import bot.promotion.core.util.BrandsAndModelsFilter;
import bot.promotion.product.dto.HotProduct;
import bot.promotion.product.dto.ShippingInfo;
import bot.promotion.product.dto.SkuProduct;
import bot.promotion.product.entity.Product;
import bot.promotion.product.entity.ProductVariant;
import bot.promotion.product.service.persistence.ProductPersistenceManager;
import bot.promotion.telegram.formatter.TelegramMessageFormatter;
import bot.promotion.telegram.service.NotificationService;
import bot.promotion.telegram.service.TelegramSenderService;
import bot.promotion.product.validator.PublishEligibilityValidator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
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
    private final Executor apiExecutor;

    public ProductService(AliexpressApiClient fetchHotProducts,
                          TelegramSenderService telegramSenderService,
                          TelegramMessageFormatter formatter,
                          ProductUrlService urlService,
                          SkuProductInfo skuProductInfo,
                          ProductCacheFilter productCacheFilter,
                          BrandsAndModelsFilter brandsModels,
                          FetchShippingInfo shippingInfo,
                          NotificationService notify,
                          FinalPriceService finalPriceService,
                          AliexpressCoinService aliexpressCoinService,
                          PublishEligibilityValidator publishEligibility,
                          ProductProcessedCache productProcessedCache,
                          Clock clock,
                          ProductPersistenceManager persistenceManager,
                          @Qualifier("apiExecutor") Executor apiExecutor) {
        this.fetchHotProducts = fetchHotProducts;
        this.telegramSenderService = telegramSenderService;
        this.formatter = formatter;
        this.urlService = urlService;
        this.skuProductInfo = skuProductInfo;
        this.productCacheFilter = productCacheFilter;
        this.brandsModels = brandsModels;
        this.shippingInfo = shippingInfo;
        this.notify = notify;
        this.finalPriceService = finalPriceService;
        this.aliexpressCoinService = aliexpressCoinService;
        this.publishEligibility = publishEligibility;
        this.productProcessedCache = productProcessedCache;
        this.clock = clock;
        this.persistenceManager = persistenceManager;
        this.apiExecutor = apiExecutor;
    }

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

        productCacheFilter.filterAllProducts(allProducts, acceptedModels, excludedModels);
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

    private void processHotProducts(List<HotProduct> products) {
        if (products == null || products.isEmpty()) {
            notify.sendWarningMessage("HotProducts is empty in product service in line 128");
            return;
        }

        List<HotProduct> filteredProducts = productCacheFilter.simpleFilter(products);
        if (filteredProducts.isEmpty()) return;

        List<String> productIds = filteredProducts.stream()
                .map(HotProduct::getProductId)
                .toList();

        List<Product> existingDbProducts = persistenceManager.findProductsBatch(productIds);
        Map<String, Product> dBProductsMap = existingDbProducts.stream().collect(Collectors.toMap(Product::getProductId, existingProduct -> existingProduct));

        List<HotProduct> slowLaneProducts = new ArrayList<>();
        List<HotProduct> fastLaneProducts = new ArrayList<>();

        filteredProducts.forEach(hotProduct -> {
            if (dBProductsMap.containsKey(hotProduct.getProductId())) {
                slowLaneProducts.add(hotProduct);
                return;
            }
            fastLaneProducts.add(hotProduct);
        });

        List<CompletableFuture<Void>> fastLaneFutures = fastLaneProducts.stream()
                .map(hp -> CompletableFuture.runAsync(() -> {
                    try {
                        processNoExistingDbProduct(hp);
                    } catch (Exception e) {
                        notify.sendErrorMessage("fast lane async processing fails for product ID " + hp.getProductId(), e);
                    }
                }, apiExecutor))
                .toList();

        List<CompletableFuture<Void>> slowLaneFutures = slowLaneProducts.stream()
                .map(hp -> CompletableFuture.runAsync(() -> {
                    try {
                        processExistingDbProduct(hp, dBProductsMap);
                    } catch (Exception e) {
                        notify.sendErrorMessage("slow lane async processing fails for product ID " + hp.getProductId(), e);
                    }
                }, apiExecutor))
                .toList();

        List<CompletableFuture<Void>> allFutures = new ArrayList<>();
        allFutures.addAll(fastLaneFutures);
        allFutures.addAll(slowLaneFutures);

        CompletableFuture.allOf(allFutures.toArray(new CompletableFuture[0])).join();
        notify.sendInfoMessage("Automatic channel processing was successful");
    }


    private void processExistingDbProduct(HotProduct hotProduct, Map<String, Product> dBProductsMap) {
        Product productEntity = dBProductsMap.get(hotProduct.getProductId());
        if (productEntity == null) return;

        List<SkuProduct> skuAllProduct = getOrBuildSku(hotProduct);
        SkuProduct bestSkuProduct = productCacheFilter.compareAndFilter(hotProduct, skuAllProduct);
        if (bestSkuProduct == null) return;

        processProductToPublish(hotProduct, productEntity, bestSkuProduct, skuAllProduct);
    }

    private void processProductToPublish(HotProduct hotProduct, Product productEntity, SkuProduct bestSkuProduct, List<SkuProduct> skuAllProduct) {
        boolean isPostedIn72hOrLess = postedIn72hOrLess(productEntity);
        boolean isToday = isPostedToday(productEntity);
        List<String> affiliateLinks = isPostedIn72hOrLess ? productEntity.getAffiliateLinks() : urlService.createCoinUrl(hotProduct.getProductId());
        if (affiliateLinks == null || affiliateLinks.isEmpty()) return;

        BigDecimal discountCoinValue = resolveDiscountValue(isToday, productEntity, affiliateLinks.getFirst());
        // Colocar em paralelo os dois serviços, discountCoinValue e getAvera
        BigDecimal averagePrice = getAveragePrice(productEntity, bestSkuProduct);
        BigDecimal currentPrice = finalPriceService.calculateFinalPrice(hotProduct, bestSkuProduct, discountCoinValue);
        if (isDataPriceValid(averagePrice, currentPrice)) return;

        boolean isEligible = publishEligibility.isEligibleForPublishing(currentPrice, averagePrice, isToday);
        if (isEligible) {
            publishProduct(hotProduct, bestSkuProduct, affiliateLinks, discountCoinValue, true);
            persistenceManager.updateProduct(hotProduct.getProductId(), hotProduct, skuAllProduct, affiliateLinks, discountCoinValue);
        }
    }

    private BigDecimal resolveDiscountValue(boolean isToday, Product productEntity, String affiliateLink) {
        if (isToday) return productEntity.getDiscountCoinValue();

        try {
            return aliexpressCoinService.processLink(affiliateLink, false).join();
        } catch (Exception e) {
            notify.sendErrorMessage("Asynchronous failure when extracting coin to product id: " + productEntity.getProductId(), e);
            return null;
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
    private void processNoExistingDbProduct(HotProduct hp) {
        if (productProcessedCache.isSecondaryGroupProcessed(hp.getProductId())) return;

        List<String> affiliateLinks = List.of(hp.getProductLinkPc());// Sets a default product link to avoid delays in processing products without much importance
        SkuProduct skuProduct = buildSkuProduct(hp).getFirst();

        BigDecimal discountCoinValue = new BigDecimal("1.00");// Sets a generic default value to avoid delays in processing products without much importance

        publishProduct(hp, skuProduct, affiliateLinks, discountCoinValue, false);
        productProcessedCache.markSecondaryGroupAsProcessed(hp.getProductId());

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

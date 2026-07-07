package bot.promotion.telegram.service;

import bot.promotion.aliexpress.client.FetchProductDetail;
import bot.promotion.aliexpress.client.FetchShippingInfo;
import bot.promotion.aliexpress.client.SkuProductInfo;
import bot.promotion.aliexpress.dto.HotProductResponse;
import bot.promotion.aliexpress.dto.ShippingInfoResponse;
import bot.promotion.aliexpress.dto.SkuProductResponse;
import bot.promotion.product.dto.HotProduct;
import bot.promotion.product.dto.ShippingInfo;
import bot.promotion.product.dto.SkuProduct;
import bot.promotion.aliexpress.service.AliexpressCoinService;
import bot.promotion.product.service.ProductProcessedCache;
import bot.promotion.product.service.ProductUrlService;
import bot.promotion.product.service.domain.ChooseBetterSku;
import bot.promotion.product.service.persistence.ProductPersistenceManager;
import bot.promotion.telegram.formatter.TelegramMessageFormatter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.User;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class ProductTelegramService {
    private final SkuProductInfo skuProductInfo;
    private final FetchProductDetail fetchProductDetail;
    private final TelegramSenderService telegramSenderService;
    private final TelegramMessageFormatter formatter;
    private final ProductUrlService urlService;
    private final AliexpressCoinService coinService;
    private final FetchShippingInfo shippingInfo;
    private final NotificationService notify;
    private final ProductProcessedCache productProcessedCache;
    private final ChooseBetterSku chooseBetterSku;
    private final ProductPersistenceManager persistenceManager;
    private final Executor telegramExecutor;

    public ProductTelegramService(
            SkuProductInfo skuProductInfo,
            FetchProductDetail fetchProductDetail,
            TelegramSenderService telegramSenderService,
            TelegramMessageFormatter formatter,
            ProductUrlService urlService,
            AliexpressCoinService coinService,
            FetchShippingInfo shippingInfo,
            NotificationService notify,
            ProductProcessedCache productProcessedCache,
            ChooseBetterSku chooseBetterSku,
            ProductPersistenceManager persistenceManager,
            @Qualifier("telegramExecutor") Executor telegramExecutor) {
        this.skuProductInfo = skuProductInfo;
        this.fetchProductDetail = fetchProductDetail;
        this.telegramSenderService = telegramSenderService;
        this.formatter = formatter;
        this.urlService = urlService;
        this.coinService = coinService;
        this.shippingInfo = shippingInfo;
        this.notify = notify;
        this.productProcessedCache = productProcessedCache;
        this.chooseBetterSku = chooseBetterSku;
        this.persistenceManager = persistenceManager;
        this.telegramExecutor = telegramExecutor;
    }


    public void processSaveProductUrl(String productId) {
        CompletableFuture.runAsync(() -> {
            try {
                createParameters(productId, true);
            } catch (Exception e) {
                notify.sendErrorMessage("Erro assíncrono ao processar /save para o produto ID: " + productId, e);
            }
        }, telegramExecutor);
    }

    // With boolean expression "shouldPublish", we can control if we want to just send the product info or also save it to the database.
    public void sendProductInfo(String productId) {
        CompletableFuture.runAsync(() -> {
            try {
                createParameters(productId, false);
            } catch (Exception e) {
                notify.sendErrorMessage("Erro assíncrono ao processar /post para o produto ID: " + productId, e);
            }
        }, telegramExecutor);
    }

    public void processDefaultProductUrl(String productId, User userShared, String chatId, Integer messageId) {
        CompletableFuture.runAsync(() -> {
            try {
                String formatText = formatter.createDefaultMessageText(productId, userShared);
                telegramSenderService.sendTextMessage(formatText, chatId, messageId);
                telegramSenderService.deleteUserMessage(messageId, chatId);
            } catch (Exception e) {
                notify.sendErrorMessage("Erro assíncrono ao processar default para o produto ID: " + productId, e);
            }
        }, telegramExecutor);
    }

    private void createParameters(String productId, boolean shouldSave) {
        HotProduct productDetail = fetchAndValidateProduct(productId);
        if (productDetail == null) return;

        ProductProcessedCache.CachedProductData cachedData = productProcessedCache.getCachedProduct(productId);

        List<String> affiliateLinks = (cachedData != null) ? cachedData.getAffiliateLinks() : createAndValidateLinks(productId);
        if (affiliateLinks == null || affiliateLinks.isEmpty()) return;

        BigDecimal coinPercentageDiscount = (cachedData != null) ? cachedData.getCoinPercentage() : getDiscountWithRetry(affiliateLinks.getFirst());
        if (coinPercentageDiscount == null) return;

        /*
         * Save affiliateLinks and coinPercentageDiscount in cache to avoid unnecessary calls to external services.
         * This is especially useful for the coin percentage discount, as it requires a more complex and time-consuming process
         */
        saveCacheProduct(productId, affiliateLinks, coinPercentageDiscount);

        List<SkuProduct> skuProducts = getOrBuildSku(productDetail);
        if (skuProducts == null || skuProducts.isEmpty()) {
            notify.sendWarningMessage("No SKU to process for publishing for product ID in line 123: " + productId);
            return;
        }

        publishAndUpdateProduct(productId, skuProducts, affiliateLinks, coinPercentageDiscount, productDetail, shouldSave);
    }

    private HotProduct fetchAndValidateProduct(String productId) {
        HotProduct productDetail = processToFetchProductDetail(productId);
        if (productDetail == null) {
            notify.sendWarningMessage("Process stopped: No product detail found for product ID in line 133 on ProductTelegramService: " + productId);
            return null;
        }
        return productDetail;
    }

    private List<String> createAndValidateLinks(String productId) {
        List<String> affiliateLinks = urlService.createCoinUrl(productId);
        if (affiliateLinks == null || affiliateLinks.isEmpty()) {
            notify.sendWarningMessage("Process stopped: No affiliate created for product ID in line 142 on ProductTelegramService: " + productId);
            return null;
        }
        return affiliateLinks;
    }

    private BigDecimal getDiscountWithRetry(String link) {
        try {
            return coinService.processLink(link).join();
        } catch (Exception e) {
            notify.sendErrorMessage("Error extracting discount for link in line 152 on ProductTelegramService: " + link, e);
            return new BigDecimal(1);
        }
    }

    private void saveCacheProduct(String productId, List<String> affiliateLinks, BigDecimal coinPercentage) {
        productProcessedCache.saveToCache(productId, affiliateLinks, coinPercentage);
    }

    private void publishAndUpdateProduct(String productId, List<SkuProduct> skuProducts, List<String> affiliateLinks, BigDecimal coinPercentageDiscount, HotProduct productDetail, boolean shouldSave) {
        if (shouldSave) {
            persistenceManager.saveProduct(productDetail);
        }
        SkuProduct betterSku = chooseBetterSku.chooseSkuProduct(skuProducts);
        boolean existsInDb = persistenceManager.existsInDb(productId);
        publishProduct(productDetail, betterSku, affiliateLinks, coinPercentageDiscount, existsInDb);

        if (existsInDb) {
            persistenceManager.updateProduct(productId, productDetail, skuProducts, affiliateLinks, coinPercentageDiscount);
        }
    }

    private List<SkuProduct> getOrBuildSku(HotProduct productDetail) {
        List<SkuProduct> skuProducts = processToFetchProductSku(productDetail.getProductId());
        if (skuProducts != null && !skuProducts.isEmpty()) {
            skuProducts.forEach(skuProduct -> {
                if (isImageMissing(skuProduct)) {
                    skuProduct.setSkuImage(productDetail.getImageUrl());
                }
            });
            return skuProducts;
        }

        if (productDetail.getSkuId() == null || productDetail.getSkuId().isBlank()) return Collections.emptyList();

        return buildSkuProduct(productDetail);
    }

    private List<SkuProduct> buildSkuProduct(HotProduct productDetail) {
        ShippingInfo shippingInfo = processToFetchShippingInfo(productDetail);

        SkuProduct sku = new SkuProduct();
        sku.setSkuId(productDetail.getSkuId());
        sku.setSalePrice(productDetail.getSalePriceApp());
        sku.setSkuImage(productDetail.getImageUrl());
        sku.setModelo("default");
        sku.setSkuProperties("default");
        if (shippingInfo != null && !shippingInfo.getShipFromCountry().isBlank()) {
            sku.setShipFromCountry(shippingInfo.getShipFromCountry());
        }
        if (shippingInfo != null && !shippingInfo.getShippingFee().isBlank()) {
            sku.setShippingFees(shippingInfo.getShippingFee());
        }

        ArrayList<SkuProduct> skuList = new ArrayList<>();
        skuList.add(sku);
        return skuList;
    }

    private boolean isImageMissing(SkuProduct sku) {
        return sku.getSkuImage() == null || sku.getSkuImage().isBlank();
    }

    private HotProduct processToFetchProductDetail(String productId) {
        HotProductResponse productDetailResponse = fetchProductDetail.productDetail(productId);

        if (productDetailResponse != null &&
                productDetailResponse.getRespResult() != null &&
                productDetailResponse.getRespResult().getResult() != null &&
                productDetailResponse.getRespResult().getResult().getProductsList() != null &&
                !productDetailResponse.getRespResult().getResult().getProductsList().isEmpty()) {
            return productDetailResponse.getRespResult().getResult().getProductsList().getFirst();
        }
        notify.sendWarningMessage("No product detail found for product ID in line 225 on ProductTelegramService: " + productId);
        return null;
    }

    private List<SkuProduct> processToFetchProductSku(String productId) {
        SkuProductResponse skuInfo = skuProductInfo.getSkuProduct(productId);
        if (skuInfo != null &&
                skuInfo.getRespResult() != null &&
                skuInfo.getRespResult().getResult() != null &&
                skuInfo.getRespResult().getResult().getSkuProductsList() != null &&
                !skuInfo.getRespResult().getResult().getSkuProductsList().isEmpty()) {
            return skuInfo.getRespResult().getResult().getSkuProductsList();
        }
        notify.sendWarningMessage("No Sku product info found for product ID in line 238 on ProductTelegramService: " + productId);
        return null;
    }

    private ShippingInfo processToFetchShippingInfo(HotProduct productDetail) {
        ShippingInfoResponse shippingResponse = shippingInfo.getShippingInfo(productDetail);
        if (shippingResponse != null &&
                shippingResponse.getRespResult() != null &&
                shippingResponse.getRespResult().getShippingInfo() != null) {
            return shippingResponse.getRespResult().getShippingInfo();
        }
        notify.sendWarningMessage("No shipping info found for product ID in line 249 on ProductTelegramService: " + productDetail.getProductId());
        return null;
    }

    private void publishProduct(HotProduct productDetail, SkuProduct skuProduct, List<String> affiliateLinks, BigDecimal coinPercentageDiscount, boolean isPriority) {
        telegramSenderService.sendPhotoMessage(skuProduct.getSkuImage(),
                formatter.formatMessage(productDetail, skuProduct, affiliateLinks, coinPercentageDiscount), isPriority);
    }
}
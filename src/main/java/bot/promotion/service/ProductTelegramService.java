package bot.promotion.service;

import bot.promotion.client.FetchProductDetail;
import bot.promotion.client.FetchShippingInfo;
import bot.promotion.client.SkuProductInfo;
import bot.promotion.dto.*;
import bot.promotion.entity.PriceHistory;
import bot.promotion.entity.Product;
import bot.promotion.entity.ProductVariant;
import bot.promotion.repository.PriceHistoryRepository;
import bot.promotion.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ProductTelegramService {
    private final SkuProductInfo skuProductInfo;
    private final FetchProductDetail fetchProductDetail;
    private final TelegramReceiveAndPost telegramReceiveAndPost;
    private final TelegramMessageFormatter formatter;
    private final ProductUrlService urlService;
    private final ProductRepository productRepository;
    private final AliexpressCoinService coinService;
    private final FinalPriceService finalPriceService;
    private final TransactionTemplate transactionTemplate;
    private final PriceHistoryRepository priceHistoryRepository;
    private final FetchShippingInfo shippingInfo;
    private final NotificationService notify;

    private static final Pattern FIRST_WORD_PATTERN = Pattern.compile("^([^\\s-_]+)");
    private static final Set<String> COMMON_COLORS = Set.of(
            "black", "white", "red", "blue", "green", "yellow", "purple", "pink",
            "orange", "brown", "gray", "grey", "silver", "gold", "beige", "navy",
            "preto", "branco", "vermelho", "azul", "verde", "amarelo", "roxo", "rosa",
            "laranja", "marrom", "cinza", "prata", "dourado", "bege"
    );

    @Autowired
    public ProductTelegramService(SkuProductInfo skuProductInfo, FetchProductDetail fetchProductDetail, @Lazy TelegramReceiveAndPost telegramReceiveAndPost,
                                  TelegramMessageFormatter telegramMessageFormatter, ProductUrlService urlService, ProductRepository productRepository,
                                  AliexpressCoinService aliexpressCoinService, FinalPriceService finalPriceService, TransactionTemplate transactionTemplate,
                                  PriceHistoryRepository priceHistoryRepository, FetchShippingInfo shippingInfo, @Lazy NotificationService notify) {
        this.skuProductInfo = skuProductInfo;
        this.fetchProductDetail = fetchProductDetail;
        this.telegramReceiveAndPost = telegramReceiveAndPost;
        this.formatter = telegramMessageFormatter;
        this.urlService = urlService;
        this.productRepository = productRepository;
        this.coinService = aliexpressCoinService;
        this.finalPriceService = finalPriceService;
        this.transactionTemplate = transactionTemplate;
        this.priceHistoryRepository = priceHistoryRepository;
        this.shippingInfo = shippingInfo;
        this.notify = notify;
    }

    public void processSaveProductUrl(String productId) {
        createParameters(productId, true);
    }

    // With boolean expression "shouldPublish", we can control if we want to just send the product info or also save it to the database.
    public void sendProductInfo(String productId) {
        createParameters(productId, false);
    }

    private void createParameters(String productId, boolean shouldSave) {
        HotProduct productDetail = processToFetchProductDetail(productId);
        if (productDetail == null) {
            notify.sendWarningMessage("Couldn't continue because no product detail found for product ID: " + productId);
            return;
        }

        List<String> affiliateLinks = urlService.createCoinUrl(productId);
        if (affiliateLinks == null || affiliateLinks.isEmpty()) {
            notify.sendWarningMessage("Couldn't continue because no affiliate link could be created for product ID: " + productId);
            return;
        }

        BigDecimal coinPercentageDiscount = coinService.processLink(affiliateLinks.getFirst());
        if (coinPercentageDiscount == null) {
            coinPercentageDiscount = coinService.processLink(affiliateLinks.getFirst());
        }

        if (coinPercentageDiscount == null || coinPercentageDiscount.compareTo(BigDecimal.ZERO) <= 0) {
            notify.sendWarningMessage("Couldn't continue because no coin percentage discount could be extracted for product ID: " + productId);
            return;
        }

        List<SkuProduct> skuProducts = new ArrayList<>();
        if (shouldSave) {
            skuProducts = createEntity(productId, affiliateLinks.getFirst(), coinPercentageDiscount, productDetail);
            if (skuProducts == null || skuProducts.isEmpty()) {
                notify.sendWarningMessage("The product with ID " + productId + " couldn't continue and published because no product sku was found.");
                return;
            }
        }

        publishAndUpdateProduct(productId, skuProducts, affiliateLinks, coinPercentageDiscount, productDetail);
    }

    private List<SkuProduct> createEntity(String productId, String affiliateLink, BigDecimal coinPercentageDiscount, HotProduct productDetail) {
        List<SkuProduct> skusToProcess = getOrBuildSku(productDetail);
        if (skusToProcess.isEmpty()) {
            notify.sendWarningMessage("No SKU to process for product ID in line 114: " + productId);
            return null;
        }

        try {
            transactionTemplate.execute(status -> {
                Product product = createProductEntity(productId, affiliateLink, coinPercentageDiscount);
                forEachVariant(productDetail, skusToProcess, product, coinPercentageDiscount);

                productRepository.save(product);
                updateAveragesForVariant(product);
                return null;
            });
        } catch (Exception e) {
            notify.sendErrorMessage("CRITICAL ERROR: Failed to save database entity for Product ID " + productId, e);
        }
        return skusToProcess;
    }

    private void publishAndUpdateProduct(String productId, List<SkuProduct> skusToProcess, List<String> affiliateLinks, BigDecimal coinPercentageDiscount, HotProduct productDetail) {
        if (skusToProcess == null || skusToProcess.isEmpty()) skusToProcess = getOrBuildSku(productDetail);

        if (skusToProcess.isEmpty()) {
            notify.sendWarningMessage("No SKU to process for publishing for product ID in line 138: " + productId);
            return;
        }
        List<SkuProduct> allSkus = skusToProcess;
        try {
            transactionTemplate.execute(status -> {

                Optional<Product> productOptional = productRepository.findByProductId(productId);
                if (productOptional.isEmpty()) {
                    chooseBestProduct(productDetail, allSkus, affiliateLinks, coinPercentageDiscount, false);
                    notify.sendWarningMessage("Product with ID " + productId + " not found in the database to update after publishing.");
                    return null;
                }
                chooseBestProduct(productDetail, allSkus, affiliateLinks, coinPercentageDiscount, true);
                //isPriority is used to determine witch group the product will be published in on telegram.

                Product product = productOptional.get();
                // Update the fields to always have the last published affiliate link and coin discount
                product.setAffiliateLink(affiliateLinks.getFirst());
                product.setDiscountCoinValue(coinPercentageDiscount);
                product.setLastPostedOn(LocalDateTime.now());
                forEachVariant(productDetail, allSkus, product, coinPercentageDiscount);

                productRepository.save(product);
                updateAveragesForVariant(product);
                return null;
            });
        } catch (Exception e) {
            notify.sendErrorMessage("CRITICAL ERROR: Failed to save database entity for Product ID " + productId, e);
        }
    }

    private Product createProductEntity(String productId, String affiliateLink, BigDecimal coinPercentageDiscount) {
        Product product = productRepository.findByProductId(productId)
                .orElse(new Product());

        if (product.getProductId() == null) {
            product.setProductId(productId);
        }
        product.setAffiliateLink(affiliateLink);
        product.setDiscountCoinValue(coinPercentageDiscount);
        return product;
    }

    private ProductVariant createProductVariantEntity(Product product, SkuProduct skuProduct) {
        if (product.getVariants() == null) {
            product.setVariants(new ArrayList<>());
        }
        return product.getVariants().stream()
                .filter(variant -> variant.getSkuId().equals(skuProduct.getSkuId()))
                .findFirst()
                .orElseGet(() -> {
                    ProductVariant newVariant = new ProductVariant();
                    newVariant.setSkuId(skuProduct.getSkuId());
                    return newVariant;
                });
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

    private void forEachVariant(HotProduct productDetail, List<SkuProduct> skusToProcess, Product product, BigDecimal coinPercentageDiscount) {
        for (SkuProduct sku : skusToProcess) {
            ProductVariant variant = createProductVariantEntity(product, sku);
            variant.setSkuProperties(sku.getSkuProperties());

            BigDecimal finalPrice = finalPriceService.calculateFinalPrice(productDetail, sku, coinPercentageDiscount);
            PriceHistory priceHistory = new PriceHistory();
            priceHistory.setPrice(finalPrice);
            priceHistory.setCapturedDate(LocalDateTime.now());

            variant.addPriceHistory(priceHistory);
            if (!product.getVariants().contains(variant)) {
                product.addVariant(variant);
            }
        }
    }

    private void updateAveragesForVariant(Product product) {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

        for (ProductVariant variant : product.getVariants()) {
            BigDecimal average = priceHistoryRepository.calculateAveragePrice(variant.getId(), thirtyDaysAgo);

            if (average != null) {
                average = average.setScale(2, RoundingMode.HALF_UP);
                variant.setAveragePrice(average);
            }
        }
        productRepository.save(product);
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
        notify.sendWarningMessage("No product detail found for product ID in line 277: " + productId);
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
        notify.sendWarningMessage("No Sku product info found for product ID in line 290: " + productId);
        return null;
    }

    private ShippingInfo processToFetchShippingInfo(HotProduct productDetail) {
        ShippingInfoResponse shippingResponse = shippingInfo.getShippingInfo(productDetail);
        if (shippingResponse != null &&
                shippingResponse.getRespResult() != null &&
                shippingResponse.getRespResult().getShippingInfo() != null) {
            return shippingResponse.getRespResult().getShippingInfo();
        }
        notify.sendWarningMessage("No shipping info found for product ID in line 301: " + productDetail.getProductId());
        return null;
    }

    private void chooseBestProduct(HotProduct productDetail, List<SkuProduct> skuAllProducts, List<String> affiliateLinks, BigDecimal coinPercentageDiscount, boolean isPriority) {
        if (skuAllProducts.isEmpty()) return;

        if (skuAllProducts.size() == 1) {
            publishProduct(productDetail, skuAllProducts.getFirst(), affiliateLinks, coinPercentageDiscount, isPriority);
            return;
        }

        Map<String, Optional<SkuProduct>> groupedByCheapest = skuAllProducts.stream()
                .collect(Collectors.groupingBy(
                        SkuProduct -> simplifiedGroupkey(SkuProduct.getModelo()),
                        Collectors.minBy(getBestVariantComparator())
                ));

        List<SkuProduct> bestVariantsOfEachModel = groupedByCheapest.values().stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted(getBestVariantComparator())
                .toList();

        if (bestVariantsOfEachModel.isEmpty()) return;

        publishProduct(productDetail, bestVariantsOfEachModel.getFirst(), affiliateLinks, coinPercentageDiscount, isPriority);
    }

    private String simplifiedGroupkey(String title) {
        if (title == null || title.isBlank()) {
            return "unknown";
        }
        Matcher matcher = FIRST_WORD_PATTERN.matcher(title);
        if (!matcher.find()) {
            return title;
        }
        String titleFormated = matcher.group(1).toLowerCase();

        if (COMMON_COLORS.contains(titleFormated)) {
            return "color";
        }
        return titleFormated;
    }

    private Comparator<SkuProduct> getBestVariantComparator() {
        return Comparator
                .comparingInt((SkuProduct sku) -> isShippedFromBrazil(sku) ? 0 : 1)
                .thenComparingDouble(this::extractShippingFee)
                .thenComparingDouble(this::extractSalePrice);
    }

    private double extractShippingFee(SkuProduct sku) {
        if (sku.getShippingFees() == null || sku.getShippingFees().isEmpty()) return 0.0;
        try {
            return Double.parseDouble(sku.getShippingFees());
        } catch (NumberFormatException e) {
            return Double.MAX_VALUE;
        }
    }

    private double extractSalePrice(SkuProduct sku) {
        if (sku.getSalePrice() == null) return Double.MAX_VALUE;
        try {
            return Double.parseDouble(sku.getSalePrice());
        } catch (NumberFormatException e) {
            return Double.MAX_VALUE;
        }
    }

    private boolean isShippedFromBrazil(SkuProduct sku) {
        return sku.getShipFromCountry() != null && "BR".equals(sku.getShipFromCountry().trim());
    }

    private void publishProduct(HotProduct productDetail, SkuProduct skuProduct, List<String> affiliateLinks, BigDecimal coinPercentageDiscount, boolean isPriority) {
        telegramReceiveAndPost.sendPhotoMessage(skuProduct.getSkuImage(),
                formatter.formatMessage(productDetail, skuProduct, affiliateLinks, coinPercentageDiscount), isPriority);
    }
}
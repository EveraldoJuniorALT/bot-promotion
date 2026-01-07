package bot.promotion.service;

import bot.promotion.client.FetchProductDetail;
import bot.promotion.client.SkuProductInfo;
import bot.promotion.dto.*;
import bot.promotion.model.PriceHistory;
import bot.promotion.model.Product;
import bot.promotion.model.ProductVariant;
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

    private static final Pattern FIRST_WORD_PATTERN = Pattern.compile("^([^\\s-_]+)");
    private static final Set<String> COMMON_COLORS = Set.of(
            "black", "white", "red", "blue", "green", "yellow", "purple", "pink",
            "orange", "brown", "gray", "grey", "silver", "gold", "beige", "navy",
            "preto", "branco", "vermelho", "azul", "verde", "amarelo", "roxo", "rosa",
            "laranja", "marrom", "cinza", "prata", "dourado", "bege"
    );
    private final PriceHistoryRepository priceHistoryRepository;

    @Autowired
    public ProductTelegramService(SkuProductInfo skuProductInfo, FetchProductDetail fetchProductDetail, @Lazy TelegramReceiveAndPost telegramReceiveAndPost,
                                  TelegramMessageFormatter telegramMessageFormatter, ProductUrlService urlService, ProductRepository productRepository,
                                  AliexpressCoinService aliexpressCoinService, FinalPriceService finalPriceService, TransactionTemplate transactionTemplate,
                                  PriceHistoryRepository priceHistoryRepository) {
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
    }

    public void processSaveProductUrl(String productId) {
        createParameters(productId, false);
    }

    //With boolean expression "shouldPublish", I can choose if I want to publish the product or just save it to the database.
    public void sendProductInfo(String productId) {
        createParameters(productId, true);
    }

    private void createParameters(String productId, boolean shouldPublish) {
        HotProduct productDetail = processToFetchProductDetail(productId);
        if (productDetail == null) {
            System.out.println("Couldn't be saved because no product detail found for product ID: " + productId);
            return;
        }

        String affiliateLink = urlService.createCoinUrl(productId);
        if (affiliateLink == null || affiliateLink.isBlank()) {
            System.out.println("Couldn't be saved because no affiliate link could be created for product ID: " + productId);
            return;
        }

        BigDecimal coinPercentageDiscount = coinService.processLink(affiliateLink);
        if (coinPercentageDiscount == null) {
            coinPercentageDiscount = coinService.processLink(affiliateLink);
        }

        if (coinPercentageDiscount == null || coinPercentageDiscount.compareTo(BigDecimal.ZERO) <= 0) {
            System.out.println("Couldn't be saved because no coin percentage discount could be extracted for product ID: " + productId);
            return;
        }

        if (shouldPublish) {
            publishAndUpdateProduct(productId, affiliateLink, coinPercentageDiscount, productDetail);
            return;
        }
        createEntity(productId, affiliateLink, coinPercentageDiscount, productDetail);
    }

    private void createEntity(String productId, String affiliateLink, BigDecimal coinPercentageDiscount, HotProduct productDetail) {
        List<SkuProduct> skusToProcess = getOrBuildSku(productDetail);
        if (skusToProcess.isEmpty()) {
            System.out.println("No SKU to process for product ID in line 104: " + productId);
            return;
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
            System.out.println("CRITICAL ERROR: Failed to save database entity for Product ID " + productId);
            System.out.println("Reason: " + e.getMessage());
        }
    }

    private void publishAndUpdateProduct(String productId, String affiliateLink, BigDecimal coinPercentageDiscount, HotProduct productDetail) {
        List<SkuProduct> skusToProcess = getOrBuildSku(productDetail);
        if (skusToProcess.isEmpty()) {
            System.out.println("No SKU to process for product ID in line 126: " + productId);
            return;
        }

        chooseBestProduct(productDetail, skusToProcess, affiliateLink, coinPercentageDiscount);

        try {
            transactionTemplate.execute(status -> {

                Optional<Product> productOptional = productRepository.findByProductId(productId);
                if (productOptional.isEmpty()) {
                    telegramReceiveAndPost.sendTextMessage("Product with ID " + productId + " not found in the database to update after publishing.");
                    return null;
                }
                Product product = productOptional.get();
                // Update the fields to always have the last published affiliate link and coin discount
                product.setAffiliateLink(affiliateLink);
                product.setDiscountCoinValue(coinPercentageDiscount);
                product.setLastPostedOn(LocalDateTime.now());
                forEachVariant(productDetail, skusToProcess, product, coinPercentageDiscount);

                productRepository.save(product);
                updateAveragesForVariant(product);
                return null;
            });
        } catch (Exception e) {
            System.out.println("CRITICAL ERROR: Failed to save database entity for Product ID " + productId);
            System.out.println("Reason: " + e.getMessage());
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
            List<SkuProduct> filteredSkuProducts = new ArrayList<>();
            for (SkuProduct skuProductToImage : skuProducts) {
                if (skuProductToImage.getSkuImage() == null || skuProductToImage.getSkuImage().isBlank()) {
                    skuProductToImage.setSkuImage(productDetail.getImageUrl());
                    filteredSkuProducts.add(skuProductToImage);
                }
            }
            return filteredSkuProducts.isEmpty() ? skuProducts : filteredSkuProducts;
        }

        if (productDetail.getSkuId() == null || productDetail.getSkuId().isBlank()) return Collections.emptyList();

        SkuProduct sku = new SkuProduct();
        sku.setSkuId(productDetail.getSkuId());
        sku.setSalePrice(productDetail.getSalePriceApp());
        sku.setSkuImage(productDetail.getImageUrl());
        sku.setModelo("default");
        sku.setSkuProperties("default");
        return List.of(sku);
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
        System.out.println("No product detail found for product ID in line 97: " + productId);
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
        System.out.println("No Sku product info found for product ID: " + productId);
        return null;
    }

    private void chooseBestProduct(HotProduct productDetail, List<SkuProduct> skuAllProducts, String affiliateLink, BigDecimal coinPercentageDiscount) {
        if (skuAllProducts.isEmpty()) return;

        Map<String, Optional<SkuProduct>> groupedByCheapest = skuAllProducts.stream()
                .collect(Collectors.groupingBy(
                        SkuProduct -> simplifiedGroupkey(SkuProduct.getModelo()),
                        Collectors.minBy(Comparator.comparing(SkuProduct::getSalePrice))
                ));

        List<SkuProduct> cheapestByGroup = groupedByCheapest.values().stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        publishProduct(productDetail, cheapestByGroup.getFirst(), affiliateLink, coinPercentageDiscount);
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

    private void publishProduct(HotProduct productDetail, SkuProduct skuProduct, String affiliateLink, BigDecimal coinPercentageDiscount) {
        try {
            telegramReceiveAndPost.sendPhotoMessage(skuProduct.getSkuImage(),
                    formatter.formatMessage(productDetail, skuProduct, affiliateLink, coinPercentageDiscount));
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Thread was interrupted during publishing product");
        }
    }
}
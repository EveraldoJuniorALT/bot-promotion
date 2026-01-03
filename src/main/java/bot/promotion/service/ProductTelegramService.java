package bot.promotion.service;

import bot.promotion.client.FetchProductDetail;
import bot.promotion.client.SkuProductInfo;
import bot.promotion.dto.*;
import bot.promotion.model.PriceHistory;
import bot.promotion.model.Product;
import bot.promotion.model.ProductVariant;
import bot.promotion.repository.PriceHistoryRepository;
import bot.promotion.repository.ProductRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

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
                                  AliexpressCoinService aliexpressCoinService, FinalPriceService finalPriceService, PriceHistoryRepository priceHistoryRepository) {
        this.skuProductInfo = skuProductInfo;
        this.fetchProductDetail = fetchProductDetail;
        this.telegramReceiveAndPost = telegramReceiveAndPost;
        this.formatter = telegramMessageFormatter;
        this.urlService = urlService;
        this.productRepository = productRepository;
        this.coinService = aliexpressCoinService;
        this.finalPriceService = finalPriceService;
        this.priceHistoryRepository = priceHistoryRepository;
    }

    @Transactional
    public void processProductUrl(String productUrl) {
        String productId = urlService.processUrlAndExtractId(productUrl);
        if (productId == null || productId.isBlank()) {
            System.out.println("It was not possible to extract the ID from the URL: " + productUrl);
            return;
        }

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
        createEntity(productId, affiliateLink, coinPercentageDiscount, productDetail);
    }

    private void createEntity(String productId, String affiliateLink, BigDecimal coinPercentageDiscount, HotProduct productDetail) {
        Product product = createProductEntity(productId, affiliateLink, coinPercentageDiscount);
        List<SkuProduct> skusToProcess = getOrBuildSku(productDetail);

        if (skusToProcess.isEmpty()) {
            System.out.println("No SKU to process for product ID: " + productId);
            return;
        }

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
        productRepository.save(product);
        updateAveragesForVariant(product);
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
        if (skuProducts != null && !skuProducts.isEmpty()) return skuProducts;

        if (productDetail.getSkuId() == null || productDetail.getSkuId().isBlank()) return Collections.emptyList();

        SkuProduct sku = new SkuProduct();
        sku.setSkuId(productDetail.getSkuId());
        sku.setSalePrice(productDetail.getSalePriceApp());
        sku.setSkuProperties("default");
        return List.of(sku);
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

    public void sendProductInfo(String productId) {
        HotProduct productDetail = processToFetchProductDetail(productId);
        if (productDetail == null) return;

        List<SkuProduct> skuProductsList = processToFetchProductSku(productId);

        if (skuProductsList == null || skuProductsList.isEmpty()) {
            publishProduct(productDetail);
            return;
        }
        /*
         * If Sku information is returned about the product, we publish the product with more information
         * otherwise, only the information obtained previously is published.
         */
        chooseBestProduct(productDetail, skuProductsList);
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

    private void chooseBestProduct(HotProduct productDetail, List<SkuProduct> skuAllProducts) {
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

        if (cheapestByGroup.size() == 1) {
            publishProduct(productDetail, cheapestByGroup.getFirst());
            return;
        }

        String firstPrice = cheapestByGroup.getFirst().getSalePrice();
        boolean allSamePrice = cheapestByGroup.stream().allMatch(SkuProduct -> Objects.equals(firstPrice, SkuProduct.getSalePrice()));

        if (allSamePrice) {
            publishProduct(productDetail, cheapestByGroup.getFirst());
            return;
        }

        for (Optional<SkuProduct> sku : groupedByCheapest.values()) {
            sku.ifPresent(skuProduct -> publishProduct(productDetail, skuProduct));
        }
    }

    private String simplifiedGroupkey(String title) {
        if (title == null || title.isBlank()) {
            return "unknown"; // I still don't know to solve this case
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

    private void publishProduct(HotProduct productDetail, SkuProduct skuProduct) {
        try {
            telegramReceiveAndPost.sendPhotoMessage(skuProduct.getSkuImage(),
                    formatter.formatMessage(productDetail, skuProduct,
                            urlService.createCoinUrl(productDetail.getProductId())));
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Thread was interrupted during publishing product");
        }
    }

    /*
     * Yes, I know there`s of duplicate code here.
     * I`ll refactor it soon.
     * But, for now, I need to deliver the feature.
     */
    private void publishProduct(HotProduct productDetail) {
        try {
            telegramReceiveAndPost.sendPhotoMessage(productDetail.getImageUrl(),
                    formatter.formatMessage(productDetail,
                            urlService.createCoinUrl(productDetail.getProductId())));
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Thread was interrupted during publishing product");
        }
    }
}
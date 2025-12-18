package bot.promotion.service;

import bot.promotion.client.FetchProductDetail;
import bot.promotion.client.SkuProductInfo;
import bot.promotion.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

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

    private static final Pattern FIRST_WORD_PATTERN = Pattern.compile("^([^\\s-_]+)");
    private static final Set<String> COMMON_COLORS = Set.of(
            "black", "white", "red", "blue", "green", "yellow", "purple", "pink",
            "orange", "brown", "gray", "grey", "silver", "gold", "beige", "navy",
            "preto", "branco", "vermelho", "azul", "verde", "amarelo", "roxo", "rosa",
            "laranja", "marrom", "cinza", "prata", "dourado", "bege"
    );

    @Autowired
    public ProductTelegramService(SkuProductInfo skuProductInfo, FetchProductDetail fetchProductDetail, @Lazy TelegramReceiveAndPost telegramReceiveAndPost, TelegramMessageFormatter telegramMessageFormatter, ProductUrlService urlService) {
        this.skuProductInfo = skuProductInfo;
        this.fetchProductDetail = fetchProductDetail;
        this.telegramReceiveAndPost = telegramReceiveAndPost;
        this.formatter = telegramMessageFormatter;
        this.urlService = urlService;
    }

    public void sendProductInfo(String productId) {
        HotProductResponse productDetailResponse = fetchProductDetail.productDetail(productId);
        if (productDetailResponse == null ||
                productDetailResponse.getRespResult() == null ||
                productDetailResponse.getRespResult().getResult() == null ||
                productDetailResponse.getRespResult().getResult().getProductsList() == null ||
                productDetailResponse.getRespResult().getResult().getProductsList().isEmpty()) {
            System.out.println("No product detail found for product ID: " + productId);
            return;
        }
        List<HotProduct> productDetail = productDetailResponse.getRespResult().getResult().getProductsList();

        SkuProductResponse skuInfo = skuProductInfo.getSkuProduct(productId, productDetail.getFirst().getSkuId());
        if (skuInfo != null &&
                skuInfo.getRespResult() != null &&
                skuInfo.getRespResult().getResult() != null &&
                skuInfo.getRespResult().getResult().getSkuProductsList() != null &&
                !skuInfo.getRespResult().getResult().getSkuProductsList().isEmpty()) {

            List<SkuProduct> skuProductsList = skuInfo.getRespResult().getResult().getSkuProductsList();
            chooseBestProduct(productDetail.getFirst(), skuProductsList);
            return;
        }
        publishProduct(productDetail.getFirst());
    }

    private void chooseBestProduct(HotProduct productDetail, List<SkuProduct> skuAllProducts) {
        if (skuAllProducts.isEmpty()) {
            return;
        }

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
                            urlService.coinUrl(productDetail.getProductId())));
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
                            urlService.coinUrl(productDetail.getProductId())));
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Thread was interrupted during publishing product");
        }
    }
}
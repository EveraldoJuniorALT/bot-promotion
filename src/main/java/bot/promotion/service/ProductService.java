package bot.promotion.service;

import bot.promotion.client.AliexpressApiClient;
import bot.promotion.client.SkuProductInfo;
import bot.promotion.config.BrandAndModel;
import bot.promotion.config.BrandsAndModelsFilter;
import bot.promotion.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ProductService {
    private final AliexpressApiClient apiClient;
    private final TelegramReceiveAndPost telegramReceiveAndPost;
    private final TelegramMessageFormatter formatter;
    private final ProductUrlService urlService;
    private final SkuProductInfo skuProductInfo;
    private final ProductCacheFilter productCacheFilter;
    private final BrandsAndModelsFilter brandsModels;

    private static final Pattern FIRST_WORD_PATTERN = Pattern.compile("^([^\\s-_]+)");
    private static final Set<String> COMMON_COLORS = Set.of(
            "black", "white", "red", "blue", "green", "yellow", "purple", "pink",
            "orange", "brown", "gray", "grey", "silver", "gold", "beige", "navy",
            "preto", "branco", "vermelho", "azul", "verde", "amarelo", "roxo", "rosa",
            "laranja", "marrom", "cinza", "prata", "dourado", "bege"
    );

    @Autowired
    public ProductService(AliexpressApiClient apiClient, TelegramReceiveAndPost telegramReceiveAndPost, TelegramMessageFormatter formatter,
                          ProductUrlService urlService, SkuProductInfo skuProductInfo, ProductCacheFilter productCacheFilter, BrandsAndModelsFilter brandsModels) {
        this.apiClient = apiClient;
        this.telegramReceiveAndPost = telegramReceiveAndPost;
        this.formatter = formatter;
        this.urlService = urlService;
        this.skuProductInfo = skuProductInfo;
        this.productCacheFilter = productCacheFilter;
        this.brandsModels = brandsModels;
    }

    public void fetchHotProducts() {
        List<BrandAndModel> brandsAndModels = brandsModels.getBrandsAndModels();
        List<HotProduct> productsAfterFiltration = new ArrayList<>();

        for (BrandAndModel brands : brandsAndModels) {
            String brand = brands.getBrands();
            List<String> acceptedModels = brands.getModelsAccepted();
            List<String> excludedModels = brands.getModelsExcluded();

            productsAfterFiltration.addAll(fetchProductsForKeyword(brand, acceptedModels, excludedModels));
            safeSleep(5000);
        }
        processHotProducts(productsAfterFiltration);
    }

    private List<HotProduct> fetchProductsForKeyword(String keyword, List<String> models, List<String> excludedModels) {
        List<HotProduct> allProducts = new ArrayList<>();
        int currentPage = 1;

        while (true) {
            HotProductResponse responseApi = apiClient.getHotProduct(currentPage, keyword);
            if (responseApi == null ||
                    responseApi.getRespResult() == null ||
                    responseApi.getRespResult().getResult() == null ||
                    responseApi.getRespResult().getResult().getProductsList() == null ||
                    responseApi.getRespResult().getResult().getProductsList().isEmpty()) {
                break;
            }
            allProducts.addAll(responseApi.getRespResult().getResult().getProductsList());
            currentPage++;
            safeSleep(4000);
        }
        filterAllProducts(allProducts, models, excludedModels);
        System.out.println("Total products after filtering: " + allProducts.size());
        return allProducts;

    }

    private void processHotProducts(List<HotProduct> products) {
        if (products == null || products.isEmpty()) return;

        List<HotProduct> processedProducts = productCacheFilter.compareAndFilter(products);

        if (!processedProducts.isEmpty()) {
            fetchSkuInfo(processedProducts);
        }
    }

    private void fetchSkuInfo(List<HotProduct> allProducts) {
        for (HotProduct product : allProducts) {
            SkuProductResponse skuInfo = skuProductInfo.getSkuProduct(product.getProductId());
            if (skuInfo != null &&
                    skuInfo.getRespResult() != null &&
                    skuInfo.getRespResult().getResult() != null &&
                    skuInfo.getRespResult().getResult().getSkuProductsList() != null &&
                    !skuInfo.getRespResult().getResult().getSkuProductsList().isEmpty()) {

                List<SkuProduct> skuAllProducts = skuInfo.getRespResult().getResult().getSkuProductsList();
                skuAllProducts.removeIf(skuproduct -> skuproduct.getSkuImage() == null || skuproduct.getSkuImage().isBlank());

                chooseBestProduct(product, skuAllProducts);
                safeSleep(10000);
                continue;
            }
            publishProduct(product);
            safeSleep(5000);
        }
    }

    private void chooseBestProduct(HotProduct product, List<SkuProduct> skuAllProducts) {
        if (skuAllProducts.isEmpty()) return;

        if (skuAllProducts.size() == 1) {
            publishProduct(product, skuAllProducts.getFirst());
            return;
        }

        Map<String, Optional<SkuProduct>> groupedByCheapest = skuAllProducts.stream()
                .collect(Collectors.groupingBy(
                        SkuProduct -> simplifiedGroupkey(SkuProduct.getModelo()), Collectors.minBy(
                                Comparator.comparing(SkuProduct::getSalePrice))));

        List<SkuProduct> cheapestByGroup = groupedByCheapest.values().stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        if (cheapestByGroup.size() == 1) {
            publishProduct(product, cheapestByGroup.getFirst());
            return;
        }

        String firstPrice = cheapestByGroup.getFirst().getSalePrice();
        boolean allSamePrice = cheapestByGroup.stream().allMatch(SkuProduct -> Objects.equals(firstPrice, SkuProduct.getSalePrice()));

        if (allSamePrice) {
            publishProduct(product, cheapestByGroup.getFirst());
            return;
        }

        for (Optional<SkuProduct> sku : groupedByCheapest.values()) {
            sku.ifPresent(skuProduct -> publishProduct(product, skuProduct));
        }
    }

    private String simplifiedGroupkey(String title) {
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

    private void publishProduct(HotProduct product, SkuProduct skuProduct) {
        telegramReceiveAndPost.sendPhotoMessage(skuProduct.getSkuImage(),
                formatter.formatMessage(product, skuProduct,
                        urlService.createCoinUrl(product.getProductId()), null));

    }

    private void publishProduct(HotProduct product) {
        telegramReceiveAndPost.sendPhotoMessage(product.getImageUrl(),
                formatter.formatMessage(product,
                        urlService.createCoinUrl(product.getProductId())));

    }

    private void safeSleep(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Thread was interrupted during sleep");
        }
    }

    private void filterAllProducts(List<HotProduct> products, List<String> models, List<String> excludedModels) {

        Set<String> seenProductIds = new HashSet<>();

        products.removeIf(product -> {
            if (product.getSalePriceApp() == null || product.getSalePriceApp().isBlank()) {
                return true;
            }

            if (product.getEvaluateRate() == null || product.getEvaluateRate().isBlank() || Double.parseDouble(product.getEvaluateRate().replace("%", "")) < 80) {
                return true;
            }

            if (!seenProductIds.add(product.getProductId())) {
                return true;
            }

            if (models == null || models.isEmpty()) {
                return false;
            }

            String titleLower = product.getProductTitle().toLowerCase();
            return models.stream().noneMatch(titleLower::contains);
        });

        products.removeIf(product -> {
            String titleLower = product.getProductTitle().toLowerCase();
            return excludedModels.stream().anyMatch(titleLower::contains);
        });
    }
}

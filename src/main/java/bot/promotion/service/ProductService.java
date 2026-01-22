package bot.promotion.service;

import bot.promotion.client.AliexpressApiClient;
import bot.promotion.client.FetchShippingInfo;
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
    private final AliexpressApiClient fetchHotProducts;
    private final TelegramReceiveAndPost telegramReceiveAndPost;
    private final TelegramMessageFormatter formatter;
    private final ProductUrlService urlService;
    private final SkuProductInfo skuProductInfo;
    private final ProductCacheFilter productCacheFilter;
    private final BrandsAndModelsFilter brandsModels;
    private final FetchShippingInfo shippingInfo;

    private static final Pattern FIRST_WORD_PATTERN = Pattern.compile("^([^\\s-_]+)");
    private static final Set<String> COMMON_COLORS = Set.of(
            "black", "white", "red", "blue", "green", "yellow", "purple", "pink",
            "orange", "brown", "gray", "grey", "silver", "gold", "beige", "navy",
            "preto", "branco", "vermelho", "azul", "verde", "amarelo", "roxo", "rosa",
            "laranja", "marrom", "cinza", "prata", "dourado", "bege"
    );

    @Autowired
    public ProductService(AliexpressApiClient fetchHotProducts, TelegramReceiveAndPost telegramReceiveAndPost, TelegramMessageFormatter formatter,
                          ProductUrlService urlService, SkuProductInfo skuProductInfo, ProductCacheFilter productCacheFilter, BrandsAndModelsFilter brandsModels, FetchShippingInfo shippingInfo) {
        this.fetchHotProducts = fetchHotProducts;
        this.telegramReceiveAndPost = telegramReceiveAndPost;
        this.formatter = formatter;
        this.urlService = urlService;
        this.skuProductInfo = skuProductInfo;
        this.productCacheFilter = productCacheFilter;
        this.brandsModels = brandsModels;
        this.shippingInfo = shippingInfo;
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
            safeSleep(4000);
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

        List<HotProduct> processedProducts = productCacheFilter.compareAndFilter(products);

        if (!processedProducts.isEmpty()) {
            fetchSkuInfo(processedProducts);
        }
    }

    private void fetchSkuInfo(List<HotProduct> allProducts) {
        for (HotProduct product : allProducts) {
            List<SkuProduct> skuAllProducts = getOrBuildSku(product);
            chooseBestProduct(product, skuAllProducts);
            safeSleep(10000);
        }
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
        System.out.println("No SKU products found for product ID: " + product.getProductId());
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
        System.out.println("No shipping info found for product ID: " + product.getProductId());
        return null;
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

    private void safeSleep(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Thread was interrupted during sleep");
        }
    }

}

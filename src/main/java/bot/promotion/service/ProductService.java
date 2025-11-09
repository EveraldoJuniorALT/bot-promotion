package bot.promotion.service;

import bot.promotion.client.AliexpressApiClient;
import bot.promotion.client.SkuProductInfo;
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
    private final TelegramService telegramService;
    private final TelegramMessageFormatter formatter;
    private final ProductUrlService urlService;
    private final SkuProductInfo skuProductInfo;
    private final ProductCacheFilter productCacheFilter;

    private static final Pattern FIRST_WORD_PATTERN = Pattern.compile("^([^\\s-_]+)");
    private static final Set<String> COMMON_COLORS = Set.of(
            "black", "white", "red", "blue", "green", "yellow", "purple", "pink",
            "orange", "brown", "gray", "grey", "silver", "gold", "beige", "navy",
            "preto", "branco", "vermelho", "azul", "verde", "amarelo", "roxo", "rosa",
            "laranja", "marrom", "cinza", "prata", "dourado", "bege"
    );

    @Autowired
    public ProductService(AliexpressApiClient apiClient, TelegramService telegramService, TelegramMessageFormatter formatter,
                          ProductUrlService urlService, SkuProductInfo skuProductInfo, ProductCacheFilter productCacheFilter) {
        this.apiClient = apiClient;
        this.telegramService = telegramService;
        this.formatter = formatter;
        this.urlService = urlService;
        this.skuProductInfo = skuProductInfo;
        this.productCacheFilter = productCacheFilter;
    }

    public void fetchHotProducts() {
        int currentPage = 1;
        HotProductResponse responseApi = apiClient.getHotProduct(currentPage);

        if (responseApi.getRespResult().getResult().getProductsList() == null) {
            System.out.println("No products found on the first page.");
            return;
        }

        List<HotProduct> allProducts = new ArrayList<>(responseApi.getRespResult().getResult().getProductsList());

        while (true) {
            try {
                responseApi = apiClient.getHotProduct(currentPage);
                if (responseApi == null ||
                        responseApi.getRespResult() == null ||
                        responseApi.getRespResult().getResult() == null ||
                        responseApi.getRespResult().getResult().getProductsList() == null ||
                        responseApi.getRespResult().getResult().getProductsList().isEmpty()) {
                    System.out.println("No products found on page " + currentPage);
                    break;
                }
                allProducts.addAll(responseApi.getRespResult().getResult().getProductsList());
                currentPage++;

                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Thread was interrupted during paging");
            }
        }
        System.out.println("Total products before filtering: " + allProducts.size());
        filterAllProducts(allProducts);
        System.out.println("Total products after filtering: " + allProducts.size());

        List<HotProduct> processedProducts = productCacheFilter.compareAndFilter(allProducts);

        if (!processedProducts.isEmpty()) {
            fetchSkuInfo(allProducts);
        }
    }

    private void fetchSkuInfo(List<HotProduct> allProducts) {
        for (HotProduct product : allProducts) {
            try {
                SkuProductResponse skuInfo = skuProductInfo.getSkuProduct(product.getProductId(), product.getSkuId());
                if (skuInfo != null &&
                        skuInfo.getRespResult() != null &&
                        skuInfo.getRespResult().getResult() != null &&
                        skuInfo.getRespResult().getResult().getSkuProductsList() != null) {

                    List<SkuProduct> skuAllProducts = skuInfo.getRespResult().getResult().getSkuProductsList();
                    skuAllProducts.removeIf(skuproduct -> skuproduct.getSkuImage() == null || skuproduct.getSkuImage().isBlank());

                    chooseBestProduct(product, skuAllProducts);
                }
                publishProduct(product);

                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Thread was interrupted during fetching SKU info");
            }
        }
    }

    private void chooseBestProduct(HotProduct product, List<SkuProduct> skuAllProducts) {
        if (skuAllProducts.isEmpty()) {
            return;
        }

        if (skuAllProducts.size() == 1) {
            publishProduct(product, skuAllProducts.getFirst());
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
        try {
            telegramService.sendPhotoMessage(skuProduct.getSkuImage(),
                    formatter.formatMessage(product, skuProduct,
                            urlService.coinUrl(product.getProductId())));
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Thread was interrupted during publishing product");
        }

    }

    private void publishProduct(HotProduct product) {
        try {
            telegramService.sendPhotoMessage(product.getImageUrl(),
                    formatter.formatMessage(product,
                            urlService.coinUrl(product.getProductId())));
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Thread was interrupted during publishing product");
        }

    }

    private void filterAllProducts(List<HotProduct> products) {
        List<String> palavrasChave = List.of("arzopa", "xiaomi", "delux", "attack shark", "aula",
                "baseus", "qcy", "mchose", "netac", "xraydisk", "kingspec", "movespeed", "ajazz", "suporte de monitor", "suporte para monitor",
                "braço articulado", "ryzen", "8bitdo", "easysmx", "akko", "kootion", "epomaker", "magcubic", "ugreen", "kodak", "upsiren",
                "fifine", "deepcool", "teucer", "machenike", "rapoo", "tcl", "binnune", "veekos", "nacodex", "aoc");

        Set<String> seenProductIds = new HashSet<>();

        products.removeIf(product -> {
            if (product.getSalePriceApp() == null || product.getSalePriceApp().isBlank()) {
                return true;
            }

            if (product.getEvaluateRate() == null || product.getEvaluateRate().isBlank() || Double.parseDouble(product.getEvaluateRate().replace("%", "")) < 90) {
                return true;
            }

            if (!seenProductIds.add(product.getProductId())) {
                return true;
            }

            String titleLower = product.getProductTitle().toLowerCase();
            return palavrasChave.stream().noneMatch(titleLower::contains);
        });
    }
}

package bot.promotion.util;

import bot.promotion.dto.SkuProduct;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ChooseBetterSku {
    private static final Pattern FIRST_WORD_PATTERN = Pattern.compile("^([^\\s-_]+)");
    private static final Set<String> COMMON_COLORS = Set.of(
            "black", "white", "red", "blue", "green", "yellow", "purple", "pink",
            "orange", "brown", "gray", "grey", "silver", "gold", "beige", "navy",
            "preto", "branco", "vermelho", "azul", "verde", "amarelo", "roxo", "rosa",
            "laranja", "marrom", "cinza", "prata", "dourado", "bege"
    );

    public static SkuProduct chooseSkuProduct(List<SkuProduct> skuAllProducts) {
        if (skuAllProducts.isEmpty()) return null;
        if (skuAllProducts.size() == 1) return skuAllProducts.getFirst();
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

        return bestVariantsOfEachModel.getFirst();
    }

    private static String simplifiedGroupkey(String title) {
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

    private static Comparator<SkuProduct> getBestVariantComparator() {
        return Comparator
                .comparingInt((SkuProduct sku) -> isShippedFromBrazil(sku) ? 0 : 1)
                .thenComparingDouble(ChooseBetterSku::extractShippingFee)
                .thenComparingDouble(ChooseBetterSku::extractSalePrice);
    }

    private static double extractShippingFee(SkuProduct sku) {
        if (sku.getShippingFees() == null || sku.getShippingFees().isEmpty()) return 0.0;
        try {
            return Double.parseDouble(sku.getShippingFees());
        } catch (NumberFormatException e) {
            return Double.MAX_VALUE;
        }
    }

    private static double extractSalePrice(SkuProduct sku) {
        if (sku.getSalePrice() == null) return Double.MAX_VALUE;
        try {
            return Double.parseDouble(sku.getSalePrice());
        } catch (NumberFormatException e) {
            return Double.MAX_VALUE;
        }
    }

    private static boolean isShippedFromBrazil(SkuProduct sku) {
        return sku.getShipFromCountry() != null && "BR".equals(sku.getShipFromCountry().trim());
    }
}

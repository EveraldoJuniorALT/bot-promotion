package bot.promotion.enums;

import java.math.BigDecimal;

public enum PriceTrend {
    CHEAPER,
    SAME,
    MORE_EXPENSIVE;

    public static PriceTrend evaluatePrice(BigDecimal currentPrice, BigDecimal averagePrice) {
        int comparison = currentPrice.compareTo(averagePrice);
        if (comparison < 0) return CHEAPER;

        if (comparison == 0) return SAME;

        return MORE_EXPENSIVE;
    }
}

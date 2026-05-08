package bot.promotion.product.validator;

import bot.promotion.core.enums.PriceTrend;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PublishEligibilityValidator {
    private static final BigDecimal MAX_ACCEPTABLE_INCREASE_MULTIPLIER = new BigDecimal("1.05");

    public boolean isEligibleForPublishing(BigDecimal currentPrice, BigDecimal averagePrice, boolean isPostedToday) {
        PriceTrend trend = PriceTrend.evaluatePrice(currentPrice, averagePrice);
        return switch (trend) {
          case CHEAPER -> true;
          case SAME -> !isPostedToday;
          case MORE_EXPENSIVE -> !isPostedToday && isIncreaseWithinLimit(currentPrice, averagePrice);
        };
    }

    private boolean isIncreaseWithinLimit(BigDecimal currentPrice, BigDecimal averagePrice) {
        BigDecimal maxAcceptablePrice = averagePrice.multiply(MAX_ACCEPTABLE_INCREASE_MULTIPLIER);
        return currentPrice.compareTo(maxAcceptablePrice) <= 0;
    }
}

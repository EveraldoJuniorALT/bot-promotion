package bot.promotion.service;

import bot.promotion.dto.HotProduct;
import bot.promotion.model.Coupon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
public class TelegramMessageFormatter {
    private final FinalPriceService finalPriceService;

    @Autowired
    public TelegramMessageFormatter(FinalPriceService finalPriceService) {
        this.finalPriceService = finalPriceService;
    }

    public String formatMessage(HotProduct product, String affiliateLink) {

        StringBuilder message = new StringBuilder();
        message.append("🔥 *").append(product.getProductTitle()).append("* 🔥\n\n");
        message.append("💰 *Price:* ").append(finalPriceService.calculateFinalPrice(product)).append("\n");

        List<Coupon> coupons = finalPriceService.couponListAvailable(product);
        if (!coupons.isEmpty()) {
            message.append("🎟️ *Cupom:* ");
            for (Coupon coupon : coupons) {
                message.append(coupon.getCouponCode()).append(" ou");
            }
        }
        message.append(" + ").append(product.getPromotionCode().getCodePromotion());
        message.append(" + Moedas no APP \n\n");
        message.append("🔗 ").append(affiliateLink).append("\n");
        message.append("\n");

        return message.toString();
    }
}

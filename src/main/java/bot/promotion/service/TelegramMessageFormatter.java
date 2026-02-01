package bot.promotion.service;

import bot.promotion.dto.HotProduct;
import bot.promotion.dto.SkuProduct;
import bot.promotion.entity.Coupon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


@Component
public class TelegramMessageFormatter {
    private final FinalPriceService finalPriceService;

    @Autowired
    public TelegramMessageFormatter(FinalPriceService finalPriceService) {
        this.finalPriceService = finalPriceService;
    }

    public String formatMessage(HotProduct product, SkuProduct skuProduct, List<String> affiliateLinks, BigDecimal coinPercentageDiscount) {
        String finalPrice = coinPercentageDiscount != null ? String.valueOf(finalPriceService.calculateFinalPrice(product, skuProduct, coinPercentageDiscount)) :
                String.valueOf(finalPriceService.calculateFinalPrice(product, skuProduct, affiliateLinks.getFirst()));

        StringBuilder message = new StringBuilder();
        message.append("🔥 ").append(product.getProductTitle()).append("\n\n");
        message.append("💰 Valor: R$ ").append(finalPrice).append("\n\n");

        List<Coupon> coupons = finalPriceService.couponListAvailable(skuProduct);
        boolean hasPromoCode = product.getPromotionCode() != null && product.getPromotionCode().getCodePromotion() != null && !product.getPromotionCode().getCodePromotion().isBlank();

        if (!coupons.isEmpty() || hasPromoCode) {
            message.append("🎟️ Cupom: ");

            List<String> codes = new ArrayList<>();
            if (!coupons.isEmpty()) {
                String aliexpressCoupons = coupons.stream()
                        .map(c -> "<code>" + c.getCouponCode().trim() + "</code>")
                        .limit(2)
                        .collect(Collectors.joining(" ou "));
                codes.add(aliexpressCoupons);
            }
            if (hasPromoCode) {
                codes.add("<code>" + product.getPromotionCode().getCodePromotion().trim() + "</code>");
            }
            message.append(String.join(" + ", codes));
        }
        message.append(" + Moedas \n\n");
        message.append("🔗 ").append(affiliateLinks.getFirst()).append("\n\n");
        message.append("❗ Super desconto Apenas no APP, após abrir o link, o produto vai estar na 1ª posição da pág de moedas. \n\n");
        message.append("Link para PC sem super desconto: ").append(affiliateLinks.getLast()).append("\n\n");
        message.append("🚀 Grupo de Ofertas: ").append("https://t.me/GarimpDeOfertas").append("\n\n");

        return message.toString();
    }
}

package bot.promotion.telegram.formatter;

import bot.promotion.product.dto.HotProduct;
import bot.promotion.product.dto.SkuProduct;
import bot.promotion.product.entity.Coupon;
import bot.promotion.product.service.FinalPriceService;
import bot.promotion.product.service.ProductUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.User;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


@Component
@RequiredArgsConstructor
public class TelegramMessageFormatter {
    private final FinalPriceService finalPriceService;
    private final ProductUrlService productUrlService;

    public String formatMessage(HotProduct product, SkuProduct skuProduct, List<String> affiliateLinks, BigDecimal coinPercentageDiscount) {
        if (affiliateLinks == null || affiliateLinks.isEmpty()) return null;

        String finalPrice = String.valueOf(finalPriceService.calculateFinalPrice(product, skuProduct, coinPercentageDiscount));
        String coins = finalPriceService.getCoinNumber(skuProduct, coinPercentageDiscount);

        return createMessage(product, skuProduct, affiliateLinks, finalPrice, coins);
    }

    private String createMessage(HotProduct product, SkuProduct skuProduct, List<String> affiliateLinks, String finalPrice, String coins) {
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
        message.append(" + ").append(coins).append(" Moedas \n\n");
        message.append("🔗 ").append(affiliateLinks.getFirst()).append("\n\n");
        message.append("❗ Super desconto Apenas no APP, após abrir o link, o produto vai estar na 1ª posição da pág de moedas. \n\n");
        message.append("Link para PC sem super desconto: ").append(affiliateLinks.getLast()).append("\n\n");
        message.append("🚀 Grupo de Ofertas: ").append("https://t.me/GarimpDeOfertas").append("\n\n");

        return message.toString();
    }

    public String createDefaultMessageText(String productId, User userShared) {
        StringBuilder stringBuilder = new StringBuilder();
        List<String> links = productUrlService.createCoinUrl(productId);
        if (links == null || links.isEmpty()) return null;

        stringBuilder.append("@").append(verifyUserName(userShared) ? userShared.getUserName() : userShared.getFirstName()).append(" compartilhou um link:\n\n");
        stringBuilder.append("Link com super descontos, apenas no APP❗❗").append("\n");
        stringBuilder.append("✅ ").append(links.getFirst()).append("\n\n");
        stringBuilder.append("Para pc, sem super descontos❗❗").append("\n");
        stringBuilder.append("🔗 ").append(links.getLast()).append("\n\n");
        stringBuilder.append("🚀 Grupo de Ofertas: ").append("https://t.me/GarimpDeOfertas").append("\n\n");
        return stringBuilder.toString();
    }

    private boolean verifyUserName(User userShared) {
        return userShared.getUserName() != null && !userShared.getUserName().isEmpty();
    }
}

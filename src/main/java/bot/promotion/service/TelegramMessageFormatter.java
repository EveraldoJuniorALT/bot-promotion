package bot.promotion.service;

import bot.promotion.dto.HotProduct;
import bot.promotion.dto.SkuProduct;
import bot.promotion.model.Coupon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
    /*
    * Yes, I know there`s a lot of duplicate code here.
    * I`ll refactor it soon.
    * But, for now, I need to deliver the feature.
    */
    public String formatMessage(HotProduct product, SkuProduct skuProduct, String affiliateLink) {

        StringBuilder message = new StringBuilder();
        message.append("🔥 ").append(product.getProductTitle()).append("\n\n");
        message.append("💰 Valor: ").append(finalPriceService.calculateFinalPrice(product, skuProduct)).append("\n\n");

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
        message.append("🔗 ").append(affiliateLink).append("\n\n");
        message.append("❗ Apenas no APP, após abrir o link, o produto vai estar na 1ª posição da pág de moedas. \n\n");
        message.append("🚀 Grupo de Ofertas: ").append("https://t.me/GarimpDeOfertas").append("\n\n");

        return message.toString();
    }
    /*
    *I`m use this method when I don`t have skuProduct info
    * So, I use only product info to format the message
    */
    public String formatMessage(HotProduct product, String affiliateLink) {

        StringBuilder message = new StringBuilder();
        message.append("🔥 ").append(product.getProductTitle()).append("\n\n");
        message.append("💰 Valor: ").append(finalPriceService.calculateFinalPrice(product)).append("\n\n");

        List<Coupon> coupons = finalPriceService.couponListAvailable(product);
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
        message.append("🔗 ").append(affiliateLink).append("\n\n");
        message.append("❗ Apenas no APP, após abrir o link, o produto vai estar na 1ª posição da pág de moedas. \n\n");
        message.append("🚀 Grupo de Ofertas: ").append("https://t.me/GarimpDeOfertas").append("\n\n");

        return message.toString();
    }

}

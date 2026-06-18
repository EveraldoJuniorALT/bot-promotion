package bot.promotion.product.service;

import bot.promotion.aliexpress.client.CotacaoRequest;
import bot.promotion.product.dto.HotProduct;
import bot.promotion.product.dto.SkuProduct;
import bot.promotion.product.entity.Coupon;
import bot.promotion.product.service.domain.CouponManager;
import bot.promotion.telegram.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class FinalPriceService {
    private final CouponManager couponManager;
    // Voltará a entrar em vigor com a RT27
    //private static final BigDecimal IMPORT_DUTY_RATE = new BigDecimal("0.20"); // 20% import duty rate for <= $50 products
    private static final BigDecimal IMPORT_DUTY_RATE_OVER = new BigDecimal("0.28"); // 28% import duty rate for > $50 products
    private static final BigDecimal ICMS_RATE = new BigDecimal("0.20"); // 20% ICMS tax rate
    private static final Pattern VALUE_PROMO_CODE = Pattern.compile("BRL (\\d+\\.\\d+) off");
    private final CotacaoRequest cotacao;
    private final NotificationService notify;

    public String getCoinNumber(SkuProduct skuProduct, BigDecimal discountCoinValue) {
        BigDecimal valueProduct = new BigDecimal(skuProduct.getSalePrice());
        BigDecimal discountValueCoinBRL;
        try {
            discountValueCoinBRL = valueProduct.multiply(discountCoinValue).
                    divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
        } catch (ArithmeticException e) {
            notify.sendErrorMessage("Error calculate coin discount: ", e);
            return "Moedas";
        }

        BigDecimal cotacaoAtual = cotacao.getCachedCotacao();
        BigDecimal discountValueCoinUSD = discountValueCoinBRL.divide(cotacaoAtual, 2, RoundingMode.HALF_UP);
        BigDecimal coin = discountValueCoinUSD.multiply(new BigDecimal(100));

        return String.format("%.0f", coin);
    }

    public BigDecimal calculateFinalPrice(HotProduct product, SkuProduct skuProduct, BigDecimal extraDiscountCoins) {
        BigDecimal afterDiscount = ProductPriceWithCouponAndCoin(product, skuProduct, extraDiscountCoins);

        if (skuProduct.getShipFromCountry() != null &&
                !skuProduct.getShipFromCountry().isBlank() &&
                skuProduct.getShipFromCountry().equals("BR")) {
            return afterDiscount.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal limiteUSD = new BigDecimal("50.00");
        BigDecimal cotacaoAtual = cotacao.getCachedCotacao();
        BigDecimal limiteBRL = limiteUSD.multiply(cotacaoAtual);

        if (afterDiscount.compareTo(limiteBRL) <= 0) {
            //Regra de tributação antiga, mas que voltará a entrar em vigor com a RT27

            /*BigDecimal importDuty = afterDiscount.multiply(IMPORT_DUTY_RATE);
            BigDecimal icmsTax = (afterDiscount.add(importDuty)).multiply(ICMS_RATE);
            BigDecimal finalPrice = afterDiscount.add(importDuty).add(icmsTax);*/

            BigDecimal icmsTax = afterDiscount.multiply(ICMS_RATE);
            BigDecimal finalPrice = afterDiscount.add(icmsTax);
            return finalPrice.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal importDuty = afterDiscount.multiply(IMPORT_DUTY_RATE_OVER);
        BigDecimal icmsTax = afterDiscount.add(importDuty).multiply(ICMS_RATE);
        BigDecimal finalPrice = afterDiscount.add(importDuty).add(icmsTax);

        return finalPrice.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal ProductPriceWithCouponAndCoin(HotProduct product, SkuProduct skuProduct, BigDecimal extraDiscountCoins) {
        // Gross value of product
        BigDecimal valueProduct = new BigDecimal(skuProduct.getSalePrice());
        double valuePromotionCode = 0.0;
        if (product.getPromotionCode() != null && product.getPromotionCode().getCodeValue() != null) {
            Matcher matcher = VALUE_PROMO_CODE.matcher(product.getPromotionCode().getCodeValue());
            if (matcher.find()) {
                String codeValue = matcher.group(1);
                valuePromotionCode = Double.parseDouble(codeValue);
            }
        }

        // Value of product with discount of seller
        BigDecimal discountedProductValue = valueProduct.subtract(BigDecimal.valueOf(valuePromotionCode));

        List<Coupon> couponsAvailable = couponManager.getCouponAvailable(skuProduct);
        Optional<Coupon> coupons = couponsAvailable.stream()
                .max(Comparator.comparing(Coupon::getDiscount));

        // Value of product with discount of platform
        if (coupons.isPresent()) {
            discountedProductValue = discountedProductValue.subtract(BigDecimal.valueOf(coupons.get().getDiscount()));
        }

        if (extraDiscountCoins != null && extraDiscountCoins.compareTo(BigDecimal.ZERO) > 0) {
            try {
                BigDecimal discountValueCoin = valueProduct.multiply(extraDiscountCoins)
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

                discountedProductValue = discountedProductValue.subtract(discountValueCoin);
            } catch (ArithmeticException e) {
                notify.sendErrorMessage("Error calculate coin discount: ", e);
            }
        }

        if (skuProduct.getShippingFees() != null && !skuProduct.getShippingFees().isBlank()) {
            BigDecimal shippingFees = new BigDecimal(skuProduct.getShippingFees());
            return discountedProductValue.add(shippingFees);
        }

        return discountedProductValue;
    }
}

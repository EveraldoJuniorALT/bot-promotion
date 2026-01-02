package bot.promotion.service;

import bot.promotion.client.CotacaoRequest;
import bot.promotion.dto.HotProduct;
import bot.promotion.dto.SkuProduct;
import bot.promotion.model.Coupon;
import bot.promotion.repository.CouponRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.String.format;

@Service
public class FinalPriceService {
    private final CouponRepository couponRepository;
    private static final BigDecimal IMPORT_DUTY_RATE = new BigDecimal("0.20"); // 20% import duty rate for <= $50 products
    private static final BigDecimal IMPORT_DUTY_RATE_OVER = new BigDecimal("0.28"); // 28% import duty rate for > $50 products
    private static final BigDecimal ICMS_RATE = new BigDecimal("0.20"); // 20% ICMS tax rate
    private static final Pattern VALUE_PROMO_CODE = Pattern.compile("BRL (\\d+\\.\\d+) off");
    private final CotacaoRequest cotacao;
    private final AliexpressCoinService aliexpressCoinService;

    @Autowired
    public FinalPriceService(CouponRepository couponRepository, CotacaoRequest cotacaoRequest, AliexpressCoinService aliexpressCoinService) {
        this.couponRepository = couponRepository;
        this.cotacao = cotacaoRequest;
        this.aliexpressCoinService = aliexpressCoinService;
    }

    /*
     * Yes, I know there`s a lot of duplicate code here.
     * I`ll refactor it soon.
     * But, for now, I need to deliver the feature.
     */

    public String calculateFinalPrice(HotProduct product, SkuProduct skuProduct, String affiliateLink) {
        BigDecimal afterDiscount = ProductPriceWithCouponAndCoin(product, skuProduct, affiliateLink);

        if (skuProduct.getShipFromCountry().equals("BR")) {
            return format("R$ %.2f", afterDiscount);
        }

        BigDecimal limiteUSD = new BigDecimal("50.00");
        BigDecimal cotacaoAtual = new BigDecimal(cotacao.getCachedCotacao());
        BigDecimal limiteBRL = limiteUSD.multiply(cotacaoAtual);

        if (afterDiscount.compareTo(limiteBRL) <= 0) {
            BigDecimal importDuty = afterDiscount.multiply(IMPORT_DUTY_RATE);
            BigDecimal icmsTax = (afterDiscount.add(importDuty)).multiply(ICMS_RATE);
            BigDecimal finalPrice = afterDiscount.add(importDuty).add(icmsTax);
            return format("R$ %.2f", finalPrice);
        }

        BigDecimal importDuty = afterDiscount.multiply(IMPORT_DUTY_RATE_OVER);
        BigDecimal icmsTax = afterDiscount.add(importDuty).multiply(ICMS_RATE);
        BigDecimal finalPrice = afterDiscount.add(importDuty).add(icmsTax);

        return format("R$ %.2f", finalPrice);
    }

    public String calculateFinalPrice(HotProduct product, String affiliateLink) {
        BigDecimal afterDiscount = ProductPriceWithCouponAndCoin(product, affiliateLink);

        if (product.getOriginalCurrency().equals("BRL")) {
            return afterDiscount.setScale(2, RoundingMode.HALF_UP).toString();
        }

        BigDecimal limiteUSD = new BigDecimal("50.00");
        BigDecimal cotacaoAtual = new BigDecimal(cotacao.getCachedCotacao());
        BigDecimal limiteBRL = limiteUSD.multiply(cotacaoAtual);

        if (afterDiscount.compareTo(limiteBRL) <= 0) {
            BigDecimal importDuty = afterDiscount.multiply(IMPORT_DUTY_RATE);
            BigDecimal icmsTax = (afterDiscount.add(importDuty)).multiply(ICMS_RATE);
            BigDecimal finalPrice = afterDiscount.add(importDuty).add(icmsTax);
            return finalPrice.setScale(2, RoundingMode.HALF_UP).toString();
        }

        BigDecimal importDuty = afterDiscount.multiply(IMPORT_DUTY_RATE_OVER);
        BigDecimal icmsTax = afterDiscount.add(importDuty).multiply(ICMS_RATE);
        BigDecimal finalPrice = afterDiscount.add(importDuty).add(icmsTax);

        return finalPrice.setScale(2, RoundingMode.HALF_UP).toString();
    }

    public BigDecimal calculateFinalPrice(HotProduct product, SkuProduct skuProduct, BigDecimal extraDiscountCoins) {
        BigDecimal afterDiscount = ProductPriceWithCouponAndCoin(product, skuProduct, extraDiscountCoins);

        if (product.getOriginalCurrency().equals("BRL")) {
            return afterDiscount.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal limiteUSD = new BigDecimal("50.00");
        BigDecimal cotacaoAtual = new BigDecimal(cotacao.getCachedCotacao());
        BigDecimal limiteBRL = limiteUSD.multiply(cotacaoAtual);

        if (afterDiscount.compareTo(limiteBRL) <= 0) {
            BigDecimal importDuty = afterDiscount.multiply(IMPORT_DUTY_RATE);
            BigDecimal icmsTax = (afterDiscount.add(importDuty)).multiply(ICMS_RATE);
            BigDecimal finalPrice = afterDiscount.add(importDuty).add(icmsTax);
            return finalPrice.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal importDuty = afterDiscount.multiply(IMPORT_DUTY_RATE_OVER);
        BigDecimal icmsTax = afterDiscount.add(importDuty).multiply(ICMS_RATE);
        BigDecimal finalPrice = afterDiscount.add(importDuty).add(icmsTax);

        return finalPrice.setScale(2, RoundingMode.HALF_UP);
    }

    /*
     * With skuProduct info I can get a more precise coupon list
     * since the skuProduct contains info about the product models
     */
    public List<Coupon> couponListAvailable(SkuProduct skuProduct) {
        Double valueProduct = Double.parseDouble(skuProduct.getSalePrice());
        List<Coupon> couponsAvailable = couponRepository.findAllByMinimumSpendLessThanEqual(valueProduct);

        if (couponsAvailable.isEmpty()) {
            return List.of();
        }

        return couponsAvailable.stream()
                .sorted(Comparator.comparing(Coupon::getDiscount).reversed())
                .collect(Collectors.toList());
    }

    /*
     * I`m use this method when I don`t have skuProduct info
     * So, I use only product info to get the coupon list
     */
    public List<Coupon> couponListAvailable(HotProduct product) {
        Double valueProduct = Double.parseDouble(product.getSalePriceApp());
        List<Coupon> couponsAvailable = couponRepository.findAllByMinimumSpendLessThanEqual(valueProduct);

        if (couponsAvailable.isEmpty()) {
            return List.of();
        }

        return couponsAvailable.stream()
                .sorted(Comparator.comparing(Coupon::getDiscount).reversed())
                .collect(Collectors.toList());
    }

    private BigDecimal ProductPriceWithCouponAndCoin(HotProduct product, SkuProduct skuProduct, String affiliateLink) {

        BigDecimal valueProduct = new BigDecimal(skuProduct.getSalePrice());
        double valuePromotionCode = 0.0;
        if (product.getPromotionCode() != null && product.getPromotionCode().getCodeValue() != null) {
            Matcher matcher = VALUE_PROMO_CODE.matcher(product.getPromotionCode().getCodeValue());
            if (matcher.find()) {
                String codeValue = matcher.group(1);
                valuePromotionCode = Double.parseDouble(codeValue);
            }
        }

        List<Coupon> couponsAvailable = couponListAvailable(skuProduct);
        Optional<Coupon> coupons = couponsAvailable.stream()
                .max(Comparator.comparing(Coupon::getDiscount));


        BigDecimal discountedProductValue = valueProduct.subtract(BigDecimal.valueOf(valuePromotionCode));

        if (coupons.isPresent()) {
            discountedProductValue = discountedProductValue.subtract(BigDecimal.valueOf(coupons.get().getDiscount()));
        }

        if (skuProduct.getShippingFees() != null && !skuProduct.getShippingFees().isBlank()) {
            BigDecimal shippingFees = new BigDecimal(skuProduct.getShippingFees());
            discountedProductValue = discountedProductValue.add(shippingFees);
        }

        /*
         * We guarantee a second attempt to obtain the extra discount coins percentage
         * before returning the value without coin discount
         */
        BigDecimal extraDiscountCoins = aliexpressCoinService.processLink(affiliateLink);
        if (extraDiscountCoins == null || extraDiscountCoins.compareTo(BigDecimal.ZERO) <= 0) {
            extraDiscountCoins = aliexpressCoinService.processLink(affiliateLink);
        }
        if (extraDiscountCoins == null || extraDiscountCoins.compareTo(BigDecimal.ZERO) <= 0) return discountedProductValue;

        try {
            BigDecimal discountValueCoin = valueProduct.multiply(extraDiscountCoins)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

            return discountedProductValue.subtract(discountValueCoin);
        } catch (ArithmeticException e) {
            System.out.println("Error calculate coin discount: " + e.getMessage());
            return discountedProductValue;
        }

    }

    private BigDecimal ProductPriceWithCouponAndCoin(HotProduct product, String affiliateLink) {

        BigDecimal valueProduct = new BigDecimal(product.getSalePriceApp());
        double valuePromotionCode = 0.0;
        if (product.getPromotionCode() != null && product.getPromotionCode().getCodeValue() != null) {
            Matcher matcher = VALUE_PROMO_CODE.matcher(product.getPromotionCode().getCodeValue());
            if (matcher.find()) {
                String codeValue = matcher.group(1);
                valuePromotionCode = Double.parseDouble(codeValue);
            }
        }

        List<Coupon> couponsAvailable = couponListAvailable(product);
        Optional<Coupon> coupons = couponsAvailable.stream()
                .max(Comparator.comparing(Coupon::getDiscount));

        BigDecimal discountedProductValue = valueProduct.subtract(BigDecimal.valueOf(valuePromotionCode));

        if (coupons.isPresent()) {
            discountedProductValue = discountedProductValue.subtract(BigDecimal.valueOf(coupons.get().getDiscount()));
        }

        /*
         * We guarantee a second attempt to obtain the extra discount coins percentage
         * before returning the value without coin discount
         */
        BigDecimal extraDiscountCoins = aliexpressCoinService.processLink(affiliateLink);
        if (extraDiscountCoins == null) {
            extraDiscountCoins = aliexpressCoinService.processLink(affiliateLink);
        }
        if (extraDiscountCoins == null || extraDiscountCoins.compareTo(BigDecimal.ZERO) <= 0) return discountedProductValue;

        try {
            BigDecimal discountValueCoin = valueProduct.multiply(extraDiscountCoins)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

            return discountedProductValue.subtract(discountValueCoin);
        } catch (ArithmeticException e) {
            System.out.println("Error calculate coin discount: " + e.getMessage());
            return discountedProductValue;
        }
    }

    private BigDecimal ProductPriceWithCouponAndCoin(HotProduct product, SkuProduct skuProduct, BigDecimal extraDiscountCoins) {

        BigDecimal valueProduct = new BigDecimal(skuProduct.getSalePrice());
        double valuePromotionCode = 0.0;
        if (product.getPromotionCode() != null && product.getPromotionCode().getCodeValue() != null) {
            Matcher matcher = VALUE_PROMO_CODE.matcher(product.getPromotionCode().getCodeValue());
            if (matcher.find()) {
                String codeValue = matcher.group(1);
                valuePromotionCode = Double.parseDouble(codeValue);
            }
        }

        List<Coupon> couponsAvailable = couponListAvailable(skuProduct);
        Optional<Coupon> coupons = couponsAvailable.stream()
                .max(Comparator.comparing(Coupon::getDiscount));

        BigDecimal discountedProductValue = valueProduct.subtract(BigDecimal.valueOf(valuePromotionCode));

        if (coupons.isPresent()) {
            discountedProductValue = discountedProductValue.subtract(BigDecimal.valueOf(coupons.get().getDiscount()));
        }

        if (skuProduct.getShippingFees() != null && !skuProduct.getShippingFees().isBlank()) {
            BigDecimal shippingFees = new BigDecimal(skuProduct.getShippingFees());
            discountedProductValue = discountedProductValue.add(shippingFees);
        }
        /*
         * Returns the value without coin discount so the product can be published
         * allowing me to correct ir manually
         */
        if (extraDiscountCoins == null) return discountedProductValue;
        try {
            BigDecimal discountValueCoin = valueProduct.multiply(extraDiscountCoins)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

            return discountedProductValue.subtract(discountValueCoin);
        } catch (ArithmeticException e) {
            System.out.println("Error calculate coin discount: " + e.getMessage());
            return discountedProductValue;
        }
    }
}

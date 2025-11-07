package bot.promotion.service;

import bot.promotion.dto.HotProduct;
import bot.promotion.dto.SkuProduct;
import bot.promotion.model.Coupon;
import bot.promotion.repository.CouponRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
    private final CotacaoService cotacao;

    @Autowired
    public FinalPriceService(CouponRepository couponRepository, CotacaoService cotacaoService) {
        this.couponRepository = couponRepository;
        this.cotacao = cotacaoService;
    }

    /*
     * Yes, I know there`s a lot of duplicate code here.
     * I`ll refactor it soon.
     * But, for now, I need to deliver the feature.
     */

    public String calculateFinalPrice(HotProduct product, SkuProduct skuProduct) {
        BigDecimal afterDiscount = ProductPriceWithCoupon(product, skuProduct);

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

    public String calculateFinalPrice(HotProduct product) {
        BigDecimal afterDiscount = ProductPriceWithCoupon(product);

        if (product.getOriginalCurrency().equals("BRL")) {
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

    private BigDecimal ProductPriceWithCoupon(HotProduct product, SkuProduct skuProduct) {

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
        return discountedProductValue;
    }

    private BigDecimal ProductPriceWithCoupon(HotProduct product) {

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
        return discountedProductValue;
    }
}

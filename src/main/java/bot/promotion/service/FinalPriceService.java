package bot.promotion.service;

import bot.promotion.dto.HotProduct;
import bot.promotion.model.Coupon;
import bot.promotion.repository.CouponRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.lang.String.format;

@Service
public class FinalPriceService {
    private final CouponRepository couponRepository;
    
    @Autowired
    public FinalPriceService(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }
    
    public String calculateFinalPrice(HotProduct product) {
        Double valueProduct = Double.parseDouble(product.getSalePriceApp());
        Optional<Double> valuePromotionCode = Optional.of(Double.parseDouble(product.getPromotionCode().getCodeValue()));

        List<Coupon> couponsAvailable = couponRepository.findByEndTimeAfter(LocalDateTime.now());
        Optional<Coupon> coupons = couponsAvailable.stream()
                .filter(coupon -> valueProduct.compareTo(coupon.getMinimumSpend()) >= 0)
                .max(Comparator.comparing(Coupon::getDiscount));

        double discountedProductValue = valueProduct - valuePromotionCode.orElse(0.0);
        if (coupons.isPresent()) {
            discountedProductValue -= coupons.get().getDiscount();
        }
        double finalAmount = discountedProductValue * Double.parseDouble(product.getTaxRate());

        return format("%.2f", finalAmount);
    }
    
    public List<Coupon> couponListAvailable(HotProduct product) {
        Double valueProduct = Double.parseDouble(product.getSalePriceApp());
        
        List<Coupon> couponsAvailable = couponRepository.findByEndTimeAfter(LocalDateTime.now());

        return couponsAvailable.stream()
                .filter(coupon -> valueProduct.compareTo(coupon.getMinimumSpend()) >= 0)
                .sorted(Comparator.comparing(Coupon::getDiscount).reversed())
                .collect(Collectors.toList());
    }
}

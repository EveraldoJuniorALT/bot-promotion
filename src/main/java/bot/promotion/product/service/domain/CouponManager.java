package bot.promotion.product.service.domain;

import bot.promotion.product.dto.SkuProduct;
import bot.promotion.product.entity.Coupon;
import bot.promotion.product.repository.CouponRepository;
import bot.promotion.telegram.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CouponManager {
    private final CouponRepository couponRepo;
    private final NotificationService notify;
    private List<Coupon> allCoupons;
    private final Clock clock;

    public void fetchAllCoupons() {
        allCoupons = couponRepo.findAllCouponsByTime(LocalDateTime.now(clock));
        if (allCoupons == null || allCoupons.isEmpty()) {
            notify.sendWarningMessage("Coupons is null or empty in line 26 of CouponManager");
        }
    }

    public List<Coupon> getCouponAvailable(SkuProduct skuProduct) {
        if (allCoupons == null || allCoupons.isEmpty()) return List.of();


        Double valueProduct = Double.parseDouble(skuProduct.getSalePrice());
        return allCoupons.stream()
                .filter(c -> c.getMinimumSpend().compareTo(valueProduct) <= 0)
                .sorted(Comparator.comparing(Coupon::getDiscount).reversed())
                .toList();
    }
}

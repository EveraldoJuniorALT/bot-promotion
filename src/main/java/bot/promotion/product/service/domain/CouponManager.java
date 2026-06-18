package bot.promotion.product.service.domain;

import bot.promotion.product.dto.SkuProduct;
import bot.promotion.product.entity.Coupon;
import bot.promotion.product.repository.CouponRepository;
import bot.promotion.telegram.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CouponManager {
    private final CouponRepository couponRepo;
    private final NotificationService notify;
    private List<Coupon> allCoupons;

    public void fetchAllCoupons() {
        allCoupons = couponRepo.findAll();
    }

    public List<Coupon> getCouponAvailable(SkuProduct skuProduct) {
        if (allCoupons == null || allCoupons.isEmpty()) {
            notify.sendWarningMessage("Coupons is null or empty in line 26 of CouponManager");
            return null;
        }

        Double valueProduct = Double.parseDouble(skuProduct.getSalePrice());
        return allCoupons.stream()
                .filter(c -> c.getMinimumSpend().compareTo(valueProduct) <= 0)
                .sorted(Comparator.comparing(Coupon::getDiscount).reversed())
                .toList();
    }
}

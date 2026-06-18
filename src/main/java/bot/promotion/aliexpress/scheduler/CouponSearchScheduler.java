package bot.promotion.aliexpress.scheduler;

import bot.promotion.product.service.domain.CouponManager;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CouponSearchScheduler {
    private final CouponManager couponManager;

    @Scheduled(fixedRateString = "PT18M", initialDelayString = "PT35S")
    public void scheduleCouponSearch() {
        couponManager.fetchAllCoupons();
    }
}

package bot.promotion;

import bot.promotion.product.dto.SkuProduct;
import bot.promotion.product.entity.Coupon;
import bot.promotion.product.service.domain.CouponManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

@ActiveProfiles("dev")
@SpringBootTest
public class TestSyntax {
    @Autowired
    CouponManager couponManager;

    @BeforeEach
    void setUp() {
        couponManager.fetchAllCoupons();
    }

    @Test
    void testSyntax() {
        SkuProduct skuProduct = new SkuProduct();
        skuProduct.setSalePrice("480.00");

        List<Coupon> coupons = couponManager.getCouponAvailable(skuProduct);
        for (Coupon coupon : coupons) {
            System.out.println(coupon);
        }
    }
}

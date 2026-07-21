package bot.promotion.product.repository;

import bot.promotion.product.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, String> {

    @Query("SELECT C FROM Coupon C WHERE (:time BETWEEN C.startTime AND IFNULL(C.endTime, :time))")
    List<Coupon> findAllCouponsByTime(LocalDateTime time);
}

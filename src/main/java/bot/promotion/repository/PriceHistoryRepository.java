package bot.promotion.repository;

import bot.promotion.entity.PriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;


public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {
    List<PriceHistory> findByVariantIdOrderByCapturedDateDesc(Long variantId);
    @Query("SELECT CAST(AVG(ph.price) AS BigDecimal) FROM PriceHistory ph WHERE ph.variant.id = :variantId AND ph.capturedDate >= :days")
    BigDecimal calculateAveragePrice(@Param("variantId") Long variantId, @Param("days") LocalDateTime days);
}

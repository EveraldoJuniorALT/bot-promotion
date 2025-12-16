package bot.promotion.repository;

import bot.promotion.model.PriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {
    List<PriceHistory> findByVariantIdOrderByCapturedDateDesc(Long variantId);
}

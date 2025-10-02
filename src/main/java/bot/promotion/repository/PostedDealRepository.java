package bot.promotion.repository;

import bot.promotion.model.PostedDeal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostedDealRepository extends JpaRepository<PostedDeal, String> {
}

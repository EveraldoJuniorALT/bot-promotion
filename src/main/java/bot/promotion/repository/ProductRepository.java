package bot.promotion.repository;

import bot.promotion.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByProductId(String productId);
    boolean existsByProductId(String productId);
    List<Product> findAllByProductIdIn(List<String> productIds);
}

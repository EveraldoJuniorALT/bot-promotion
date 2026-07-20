package bot.promotion.product.repository;

import bot.promotion.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByProductId(String productId);
    boolean existsByProductId(String productId);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.variants WHERE p.productId IN :productIds")
    List<Product> findAllByProductIdIn(List<String> productIds);
}

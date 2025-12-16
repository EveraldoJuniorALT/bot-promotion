package bot.promotion.repository;

import bot.promotion.model.Product;
import bot.promotion.model.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    Optional<ProductVariant> findByProductAndSkuId(Product product, String skuId);
}

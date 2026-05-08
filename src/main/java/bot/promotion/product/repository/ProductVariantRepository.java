package bot.promotion.product.repository;

import bot.promotion.product.entity.Product;
import bot.promotion.product.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    Optional<ProductVariant> findByProductAndSkuId(Product product, String skuId);
}

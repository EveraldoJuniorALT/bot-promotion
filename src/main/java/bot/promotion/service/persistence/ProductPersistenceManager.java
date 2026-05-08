package bot.promotion.service.persistence;

import bot.promotion.dto.HotProduct;
import bot.promotion.dto.SkuProduct;
import bot.promotion.entity.PriceHistory;
import bot.promotion.entity.Product;
import bot.promotion.entity.ProductVariant;
import bot.promotion.repository.PriceHistoryRepository;
import bot.promotion.repository.ProductRepository;
import bot.promotion.service.FinalPriceService;
import bot.promotion.telegram.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductPersistenceManager {
    private final Clock clock;
    private final ProductRepository productRepository;
    private final FinalPriceService priceService;
    private final PriceHistoryRepository priceHistoryRepository;
    private final NotificationService notify;

    @Transactional
    public void saveProduct(HotProduct hotProduct) {
        Product product = findProductById(hotProduct.getProductId())
                .orElse(new Product());

        if (product.getProductId() == null) {
            product.setProductId(hotProduct.getProductId());
        }
        productRepository.save(product);
    }

    @Async
    @Transactional
    public void updateProduct(Product product, HotProduct hotProduct, List<SkuProduct> skuProducts, List<String> affiliateLinks, BigDecimal discountCoinValue) {
        try {
            product.setAffiliateLinkApp(affiliateLinks.getFirst());
            product.setAffiliateLinkPc(affiliateLinks.getLast());
            product.setDiscountCoinValue(discountCoinValue);
            product.setLastPostedOn(LocalDateTime.now(clock));
            updateVariants(hotProduct, skuProducts, product, discountCoinValue);
            productRepository.saveAndFlush(product);
            updateAveragePrice(product);
        } catch (Exception e) {
            notify.sendErrorMessage("CRITICAL ERROR: Failed to update database entity in line 47 for Product ID " + hotProduct.getProductId(), e);
        }
    }

    private void updateVariants(HotProduct hotProduct, List<SkuProduct> skuProducts, Product product, BigDecimal coinPercentageDiscount) {
        for (SkuProduct sku : skuProducts) {
            ProductVariant variant = createProductVariantEntity(product, sku);
            variant.setSkuProperties(sku.getSkuProperties());

            BigDecimal finalPrice = priceService.calculateFinalPrice(hotProduct, sku, coinPercentageDiscount);
            PriceHistory priceHistory = new PriceHistory();
            priceHistory.setPrice(finalPrice);
            priceHistory.setCapturedDate(LocalDateTime.now(clock));

            variant.addPriceHistory(priceHistory);
            if (!product.getVariants().contains(variant)) {
                product.addVariant(variant);
            }
        }
    }

    private ProductVariant createProductVariantEntity(Product product, SkuProduct sku) {
        if (product.getVariants() == null) {
            product.setVariants(new ArrayList<>());
        }
        return product.getVariants().stream()
                .filter(variant -> variant.getSkuId().equals(sku.getSkuId()))
                .findFirst()
                .orElseGet(() -> {
                    ProductVariant newVariant = new ProductVariant();
                    newVariant.setSkuId(sku.getSkuId());
                    return newVariant;
                });
    }

    private void updateAveragePrice(Product product) {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now(clock).minusDays(30);
        for (ProductVariant variant : product.getVariants()) {
            BigDecimal average = priceHistoryRepository.calculateAveragePrice(variant.getId(), thirtyDaysAgo);
            if (average != null && average.compareTo(BigDecimal.ZERO) > 0) {
                variant.setAveragePrice(average.setScale(2, RoundingMode.HALF_UP));
            }
        }
        productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public Optional<Product> findProductById(String productId) {
        return productRepository.findByProductId(productId);
    }

    @Transactional(readOnly = true)
    public boolean existsInDb(String productId) {
        return productRepository.existsByProductId(productId);
    }

    @Transactional(readOnly = true)
    public List<Product> findProductsBatch(List<String> productIds) {
        return productRepository.findAllByProductIdIn(productIds);
    }
}

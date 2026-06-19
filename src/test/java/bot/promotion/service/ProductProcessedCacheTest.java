package bot.promotion.service;

import bot.promotion.product.service.ProductProcessedCache;
import bot.promotion.product.service.ProductProcessedCache.CachedProductData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProductProcessedCacheTest {

    private ProductProcessedCache cache;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("must save and retrieve a valid product")
    void shouldSaveAndRetrieveValidProduct() {
        String productId = "123";
        List<String> links = List.of("https://link.com");
        BigDecimal discount = BigDecimal.TEN;

        cache.saveToCache(productId, links, discount);
        CachedProductData result = cache.getCachedProduct(productId);

        assertNotNull(result);
        assertEquals(links, result.getAffiliateLinks());
        assertEquals(discount, result.getCoinPercentage());
    }

    @Test
    @DisplayName("Do not overwrite the cache if the ID already exists.")
    void shouldNotOverwriteExistingCache() {
        String productId = "123";
        cache.saveToCache(productId, List.of("Link A"), BigDecimal.ONE);

        cache.saveToCache(productId, List.of("Link B"), BigDecimal.TEN);

        CachedProductData result = cache.getCachedProduct(productId);

        assertEquals("Link A", result.getAffiliateLinks().getFirst()); // Mantém o original
        assertEquals(BigDecimal.ONE, result.getCoinPercentage());
    }

    @Test
    @DisplayName("It should be removed from the cache if more than 8 hours have passed")
    void shouldExpireAfter12Hours() throws Exception {
        String productId = "expired-time";
        cache.saveToCache(productId, List.of("link"), BigDecimal.ONE);

        modifyCachedTime(productId, LocalDateTime.now().minusHours(13));

        CachedProductData result = cache.getCachedProduct(productId);

        assertNull(result, "Deveria retornar null pois expirou o tempo");
        assertNull(getInternalMap().get(productId), "Deveria ter removido do mapa interno");
    }

    @Test
    @DisplayName("You should remove it from the cache if the day has changed.")
    void shouldExpireIfDifferentDay() throws Exception {
        String productId = "expired-date";
        cache.saveToCache(productId, List.of("link"), BigDecimal.ONE);

        modifyCachedTime(productId, LocalDateTime.now().minusDays(1));

        CachedProductData result = cache.getCachedProduct(productId);

        assertNull(result, "Deveria retornar null pois mudou o dia");
    }

    @Test
    @DisplayName("Keep it in the cache if 7 hours and 59 minutes have passed.")
    void shouldKeepCacheIfJustUnder12Hours() throws Exception {
        String productId = "valid-limit";
        cache.saveToCache(productId, List.of("link"), BigDecimal.ONE);

        modifyCachedTime(productId, LocalDateTime.now().minusHours(7).minusMinutes(59));

        CachedProductData result = cache.getCachedProduct(productId);

        assertNotNull(result, "Ainda deveria ser válido");
    }

    private void modifyCachedTime(String productId, LocalDateTime newTime) throws Exception {
        Map<String, CachedProductData> map = getInternalMap();
        CachedProductData data = map.get(productId);

        if (data != null) {
            Field timeField = CachedProductData.class.getDeclaredField("cachedAt");
            timeField.setAccessible(true);
            timeField.set(data, newTime);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, CachedProductData> getInternalMap() throws Exception {
        Field mapField = ProductProcessedCache.class.getDeclaredField("productCache");
        mapField.setAccessible(true);
        return (Map<String, CachedProductData>) mapField.get(cache);
    }
}
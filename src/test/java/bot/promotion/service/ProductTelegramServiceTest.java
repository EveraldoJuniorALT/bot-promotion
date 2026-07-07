package bot.promotion.service;

import bot.promotion.aliexpress.client.FetchProductDetail;
import bot.promotion.aliexpress.client.FetchShippingInfo;
import bot.promotion.aliexpress.client.SkuProductInfo;
import bot.promotion.aliexpress.dto.HotProductResponse;
import bot.promotion.aliexpress.dto.SkuProductResponse;
import bot.promotion.aliexpress.service.AliexpressCoinService;
import bot.promotion.product.dto.HotProduct;
import bot.promotion.product.dto.SkuProduct;
import bot.promotion.product.entity.Product;
import bot.promotion.product.repository.PriceHistoryRepository;
import bot.promotion.product.repository.ProductRepository;
import bot.promotion.product.service.FinalPriceService;
import bot.promotion.product.service.ProductProcessedCache;
import bot.promotion.product.service.ProductUrlService;
import bot.promotion.telegram.formatter.TelegramMessageFormatter;
import bot.promotion.telegram.service.NotificationService;
import bot.promotion.telegram.service.ProductTelegramService;
import bot.promotion.telegram.service.TelegramSenderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductTelegramServiceTest {

    @Mock
    private SkuProductInfo skuProductInfo;
    @Mock
    private FetchProductDetail fetchProductDetail;
    @Mock
    private TelegramSenderService telegramSenderService;
    @Mock
    private TelegramMessageFormatter formatter;
    @Mock
    private ProductUrlService urlService;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private AliexpressCoinService coinService;
    @Mock
    private FinalPriceService finalPriceService;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private PriceHistoryRepository priceHistoryRepository;
    @Mock
    private FetchShippingInfo shippingInfo;
    @Mock
    private NotificationService notify;
    @Mock
    private ProductProcessedCache productProcessedCache;

    @InjectMocks
    private ProductTelegramService service;

    @Test
    @DisplayName("Should generate links, retrieve coin discount and save them to the cache")
    void shouldProcessFullFlowWhenCacheMiss() {
        String productId = "12345";

        when(productProcessedCache.getCachedProduct(productId)).thenReturn(null);

        HotProduct mockProduct = createMockHotProduct(productId);
        when(fetchProductDetail.productDetail(productId)).thenReturn(createProductResponse(mockProduct));

        List<String> links = List.of("https://coin-url.com", "https://normal.com");
        when(urlService.createCoinUrl(productId)).thenReturn(links);

        BigDecimal discount = new BigDecimal("15.5");
        //when(coinService.processLink(anyString())).thenReturn(discount);

        setupPersistenceMocks(productId);

        service.processSaveProductUrl(productId);

        verify(urlService).createCoinUrl(productId);
        verify(coinService).processLink(links.getFirst());
        verify(productProcessedCache).saveToCache(eq(productId), eq(links), eq(discount));
        verify(telegramSenderService).sendPhotoMessage(any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("Should use cached data and avoid external calls")
    void shouldUseCacheAndSkipExternalCalls() {
        String productId = "12345";

        List<String> cachedLinks = List.of("https://cached-link.com");
        BigDecimal cachedDiscount = new BigDecimal("20.0");

        ProductProcessedCache.CachedProductData cachedData =
                new ProductProcessedCache.CachedProductData(cachedLinks, cachedDiscount, LocalDateTime.now());

        when(productProcessedCache.getCachedProduct(productId)).thenReturn(cachedData);

        HotProduct mockProduct = createMockHotProduct(productId);
        when(fetchProductDetail.productDetail(productId)).thenReturn(createProductResponse(mockProduct));

        setupPersistenceMocks(productId);

        service.processSaveProductUrl(productId);

        verify(urlService, never()).createCoinUrl(anyString());
        verify(coinService, never()).processLink(anyString());
        verify(telegramSenderService).sendPhotoMessage(any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("Should try to get the discount again if first attempt fails")
    void shouldRetryCoinExtraction() {
        String productId = "999";
        when(productProcessedCache.getCachedProduct(productId)).thenReturn(null);
        when(fetchProductDetail.productDetail(productId)).thenReturn(createProductResponse(createMockHotProduct(productId)));
        when(urlService.createCoinUrl(productId)).thenReturn(List.of("https://link.com"));

        /*when(coinService.processLink(anyString()))
                .thenReturn(new BigDecimal("-1"))
                .thenReturn(new BigDecimal("10"));*/

        setupPersistenceMocks(productId);

        service.processSaveProductUrl(productId);

        verify(coinService, times(2)).processLink(anyString());
        verify(notify).sendInfoMessage(contains("Retrying"));
    }

    @Test
    @DisplayName("Should stop if fetchAndValidateProduct returns null")
    void shouldStopIfProductNotFound() {
        String productId = "invalid-id";

        when(fetchProductDetail.productDetail(productId)).thenReturn(null);

        service.processSaveProductUrl(productId);

        verify(productProcessedCache, never()).getCachedProduct(any());
        verify(urlService, never()).createCoinUrl(any());
        verify(notify).sendWarningMessage(contains("Process stopped: No product detail found for product ID: "));
    }

    private void setupPersistenceMocks(String productId) {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(Mockito.mock(TransactionStatus.class));
        });

        SkuProductResponse skuResponse = new SkuProductResponse();
        SkuProductResponse.MainResponse res = new SkuProductResponse.MainResponse();
        SkuProductResponse.Result innerRes = new SkuProductResponse.Result();

        SkuProduct sku = new SkuProduct();
        sku.setSkuId("SKU-1");
        sku.setSalePrice("100.00");
        innerRes.setSkuProductsList(List.of(sku));
        res.setResult(innerRes);
        skuResponse.setRespResult(res);

        when(skuProductInfo.getSkuProduct(productId)).thenReturn(skuResponse);
        when(productRepository.findByProductId(productId)).thenReturn(Optional.of(new Product()));
    }

    private HotProduct createMockHotProduct(String id) {
        HotProduct p = new HotProduct();
        p.setProductId(id);
        p.setProductTitle("Test Product");
        p.setSalePriceApp("100.00");
        p.setImageUrl("img.jpg");
        p.setSkuId("SKU-" + id);
        return p;
    }

    private HotProductResponse createProductResponse(HotProduct product) {
        HotProductResponse r = new HotProductResponse();
        HotProductResponse.MainResponse rr = new HotProductResponse.MainResponse();
        HotProductResponse.Result rrr = new HotProductResponse.Result();
        rrr.setProductsList(List.of(product));
        rr.setResult(rrr);
        r.setRespResult(rr);
        return r;
    }
}
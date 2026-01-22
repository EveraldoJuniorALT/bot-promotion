package bot.promotion.service;

import bot.promotion.client.AliexpressApiClient;
import bot.promotion.client.FetchShippingInfo;
import bot.promotion.client.SkuProductInfo;
import bot.promotion.config.BrandAndModel;
import bot.promotion.config.BrandsAndModelsFilter;
import bot.promotion.dto.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock
    private AliexpressApiClient fetchHotProducts;
    @Mock
    private TelegramReceiveAndPost telegramReceiveAndPost;
    @Mock
    private TelegramMessageFormatter formatter;
    @Mock
    private ProductUrlService urlService;
    @Mock
    private SkuProductInfo skuProductInfo;
    @Mock
    private ProductCacheFilter productCacheFilter;
    @Mock
    private BrandsAndModelsFilter brandsModels;
    @Mock
    private FetchShippingInfo shippingInfo;

    @InjectMocks
    ProductService productService;

    @Test
    @DisplayName("Should publish a valid product that has passed all filters")
    void fetchHotProducts() {
        BrandAndModel xiaomiConfig = new BrandAndModel("xiaomi", List.of("redmi"), List.of());
        BrandAndModel baseusConfig = new BrandAndModel("baseus", List.of("fone"), Collections.emptyList());
        when(brandsModels.getBrandsAndModels()).thenReturn(List.of(xiaomiConfig, baseusConfig));

        // Xiaomi response
        when(fetchHotProducts.getHotProduct(eq(1), eq("xiaomi"))).thenReturn(creatResponseApiProduct(creatMockProducts()));
        when(fetchHotProducts.getHotProduct(eq(2), eq("xiaomi"))).thenReturn(creatResponseApiProduct(Collections.emptyList()));

        // Baseus response
        when(fetchHotProducts.getHotProduct(eq(1), eq("baseus"))).thenReturn(creatResponseApiProduct(creatMockProducts()));
        when(fetchHotProducts.getHotProduct(eq(2), eq("baseus"))).thenReturn(creatResponseApiProduct(Collections.emptyList()));

        when(productCacheFilter.compareAndFilter(anyList())).thenReturn(creatMockProducts());

        when(skuProductInfo.getSkuProduct("111")).thenReturn(creatResponseApiSkuProduct(creatMockSkuProducts()));
        when(skuProductInfo.getSkuProduct("222")).thenReturn(creatResponseApiSkuProduct(creatMockSkuProducts()));

        when(urlService.createCoinUrl("111")).thenReturn("https://example.com/product/12345");
        when(urlService.createCoinUrl("222")).thenReturn("https://example.com/product/12345");

        when(formatter.formatMessage(any(), any(), anyString(), any())).thenReturn("Formatted Message");

        productService.fetchHotProducts();

        verify(telegramReceiveAndPost, times(2)).sendPhotoMessage(any(), eq("Formatted Message"));
    }

    @Test
    @DisplayName("Should create a SkuProduct with product information when fetchSkuProduct returns null/empty")
    void shouldCreatSkuProduct() {
        BrandAndModel xiaomiConfig = new BrandAndModel("xiaomi", List.of("redmi"), List.of());
        BrandAndModel baseusConfig = new BrandAndModel("baseus", List.of("fone"), Collections.emptyList());
        when(brandsModels.getBrandsAndModels()).thenReturn(List.of(xiaomiConfig, baseusConfig));

        // Xiaomi response
        when(fetchHotProducts.getHotProduct(eq(1), eq("xiaomi"))).thenReturn(creatResponseApiProduct(creatMockProducts()));
        when(fetchHotProducts.getHotProduct(eq(2), eq("xiaomi"))).thenReturn(creatResponseApiProduct(Collections.emptyList()));

        // Baseus response
        when(fetchHotProducts.getHotProduct(eq(1), eq("baseus"))).thenReturn(creatResponseApiProduct(creatMockProducts()));
        when(fetchHotProducts.getHotProduct(eq(2), eq("baseus"))).thenReturn(creatResponseApiProduct(Collections.emptyList()));

        when(productCacheFilter.compareAndFilter(anyList())).thenReturn(creatMockProducts());

        when(skuProductInfo.getSkuProduct("111")).thenReturn(null);
        when(skuProductInfo.getSkuProduct("222")).thenReturn(null);

        when(shippingInfo.getShippingInfo(any()))
                .thenReturn(creatResponseApiShippingInfo(creatMockShippingInfo(1)))
                .thenReturn(creatResponseApiShippingInfo(creatMockShippingInfo(2)));

        when(urlService.createCoinUrl("111")).thenReturn("https://example.com/product/12345");
        when(urlService.createCoinUrl("222")).thenReturn("https://example.com/product/12345");

        when(formatter.formatMessage(any(), any(), anyString(), any())).thenReturn("Formatted Message");

        productService.fetchHotProducts();

        ArgumentCaptor<SkuProduct> skuCaptor = ArgumentCaptor.forClass(SkuProduct.class);
        verify(formatter, times(2)).formatMessage(any(), skuCaptor.capture(), anyString(), any());
        verify(telegramReceiveAndPost, times(2)).sendPhotoMessage(any(), eq("Formatted Message"));

        List<SkuProduct> capturedSkus = skuCaptor.getAllValues();
        SkuProduct sku1 = capturedSkus.getFirst();
        Assertions.assertEquals("SKU-111", sku1.getSkuId());
        Assertions.assertEquals("default", sku1.getSkuProperties());
        Assertions.assertEquals("1000.00", sku1.getSalePrice());
        Assertions.assertEquals("BR", sku1.getShipFromCountry());
        Assertions.assertEquals("15.00", sku1.getShippingFees());
        Assertions.assertEquals("https://img-celular.com", sku1.getSkuImage());

        SkuProduct sku2 = capturedSkus.get(1);
        Assertions.assertEquals("SKU-222", sku2.getSkuId());
        Assertions.assertEquals("default", sku2.getSkuProperties());
        Assertions.assertEquals("150.00", sku2.getSalePrice());
        Assertions.assertEquals("US", sku2.getShipFromCountry());
        Assertions.assertEquals("20.00", sku2.getShippingFees());
        Assertions.assertEquals("https://img-fone.com", sku2.getSkuImage());
    }

    @Test
    @DisplayName("Should not publish any product with lower rating than 80%")
    void shouldNotPublish() {
        BrandAndModel brandsConfig = new BrandAndModel("baseus", List.of(), List.of());
        when(brandsModels.getBrandsAndModels()).thenReturn(List.of(brandsConfig));

        HotProduct lowRatedProduct = new HotProduct();
        lowRatedProduct.setProductId("999");
        lowRatedProduct.setProductTitle("Fone Baseus");
        lowRatedProduct.setSalePriceApp("50.00");
        lowRatedProduct.setSkuId("SKU-99");
        lowRatedProduct.setEvaluateRate("79%");
        when(fetchHotProducts.getHotProduct(eq(1), anyString())).thenReturn(creatResponseApiProduct(List.of(lowRatedProduct)));
        when(fetchHotProducts.getHotProduct(eq(2), anyString())).thenReturn(creatResponseApiProduct(Collections.emptyList()));

        productService.fetchHotProducts();

        verify(productCacheFilter, never()).compareAndFilter(anyList());
        verify(telegramReceiveAndPost, never()).sendPhotoMessage(anyString(), anyString());
    }

    private HotProductResponse creatResponseApiProduct(List<HotProduct> hotProducts) {
        HotProductResponse response = new HotProductResponse();
        HotProductResponse.MainResponse mainResponse = new HotProductResponse.MainResponse();
        HotProductResponse.Result result = new HotProductResponse.Result();
        result.setProductsList(hotProducts);
        mainResponse.setResult(result);
        response.setRespResult(mainResponse);
        return response;
    }

    private SkuProductResponse creatResponseApiSkuProduct(List<SkuProduct> skuProducts) {
        SkuProductResponse response = new SkuProductResponse();
        SkuProductResponse.MainResponse mainResponse = new SkuProductResponse.MainResponse();
        SkuProductResponse.Result result = new SkuProductResponse.Result();
        result.setSkuProductsList(skuProducts);
        mainResponse.setResult(result);
        response.setRespResult(mainResponse);
        return response;
    }

    private ShippingInfoResponse creatResponseApiShippingInfo(ShippingInfo shippingInfo) {
        ShippingInfoResponse response = new ShippingInfoResponse();
        ShippingInfoResponse.MainResponse mainResponse = new ShippingInfoResponse.MainResponse();
        mainResponse.setShippingInfo(shippingInfo);
        response.setRespResult(mainResponse);
        return response;
    }

    private List<HotProduct> creatMockProducts() {
        HotProduct smartPhone = new HotProduct();
        smartPhone.setProductId("111");
        smartPhone.setProductTitle("Xiaomi Redmi Note 12");
        smartPhone.setSalePriceApp("1000.00");
        smartPhone.setEvaluateRate("95%");
        smartPhone.setSkuId("SKU-111");
        smartPhone.setImageUrl("https://img-celular.com");

        HotProduct headPhone = new HotProduct();
        headPhone.setProductId("222");
        headPhone.setProductTitle("Fone Baseus Bluetooth");
        headPhone.setSalePriceApp("150.00");
        headPhone.setEvaluateRate("99%");
        headPhone.setSkuId("SKU-222");
        headPhone.setImageUrl("https://img-fone.com");

        return List.of(smartPhone, headPhone);
    }

    private List<SkuProduct> creatMockSkuProducts() {
        SkuProduct skuProduct1 = new SkuProduct();
        skuProduct1.setSkuId("SKU-123");
        skuProduct1.setSkuProperties("Color: black");
        skuProduct1.setModelo("black");
        skuProduct1.setSalePrice("100.00");
        skuProduct1.setShipFromCountry("BR");

        SkuProduct skuProduct2 = new SkuProduct();
        skuProduct2.setSkuId("SKU-456");
        skuProduct2.setSkuProperties("Color: white");
        skuProduct2.setModelo("white");
        skuProduct2.setSalePrice("110.00");
        skuProduct2.setShipFromCountry("US");
        return List.of(skuProduct1, skuProduct2);
    }

    private ShippingInfo creatMockShippingInfo(int skuIndex) {
        if (skuIndex == 1) {
            ShippingInfo shippingInfo = new ShippingInfo();
            shippingInfo.setShippingFee("15.00");
            shippingInfo.setShipFromCountry("BR");
            return shippingInfo;
        }
        ShippingInfo shippingInfo = new ShippingInfo();
        shippingInfo.setShippingFee("20.00");
        shippingInfo.setShipFromCountry("US");
        return shippingInfo;
    }
}

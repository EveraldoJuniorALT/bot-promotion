package bot.promotion.client;

import bot.promotion.aliexpress.client.FetchShippingInfo;
import bot.promotion.product.dto.HotProduct;
import bot.promotion.aliexpress.dto.ShippingInfoResponse;
import bot.promotion.telegram.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.global.iop.api.IopClient;
import com.global.iop.api.IopRequest;
import com.global.iop.api.IopResponse;
import com.global.iop.domain.Protocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FetchShippingInfoTest {
    @Mock
    private IopClient iopClient;

    private FetchShippingInfo fetchShippingInfo;
    private HotProduct mockProduct;
    @Mock
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        fetchShippingInfo = new FetchShippingInfo(objectMapper, iopClient, notificationService);

        mockProduct = new HotProduct();
        mockProduct.setProductId("12345");
        mockProduct.setSkuId("67890");
        mockProduct.setSalePriceApp("110.09");
    }

    @Test
    @DisplayName(("Should return ShippingInfoResponse when API call is successful"))
    void shouldReturnShippingInfoWhenApiCallIsSuccessful() throws Exception {
        String realJsonStructure = "{"
                + "\"resp_result\": {"
                +   "\"result\": {"
                +       "\"shipping_fee\": \"20.00\","
                +       "\"ship_from_country\": \"FR\""
                +   "}"
                + "}"
                + "}";

        IopResponse mockResponse = mock(IopResponse.class);

        when(mockResponse.isSuccess()).thenReturn(true);
        when(mockResponse.getGopResponseBody()).thenReturn(realJsonStructure);
        when(iopClient.execute(any(IopRequest.class), eq(Protocol.TOP))).thenReturn(mockResponse);

        ShippingInfoResponse response = fetchShippingInfo.getShippingInfo(mockProduct);
        assertNotNull(response);
        assertNotNull(response.getRespResult());
        assertNotNull(response.getRespResult().getShippingInfo());

        assertEquals("20.00", response.getRespResult().getShippingInfo().getShippingFee());
        assertEquals("FR", response.getRespResult().getShippingInfo().getShipFromCountry());

        verify(iopClient, times(1)).execute(any(IopRequest.class), eq(Protocol.TOP));
    }

    @Test
    @DisplayName("Should retry API call when first attempt fails")
    void shouldRetryWhenFirstCallFails() throws Exception {
        IopResponse failResponse = mock(IopResponse.class);
        when(failResponse.isSuccess()).thenReturn(false);

        IopResponse successResponse = mock(IopResponse.class);
        when(successResponse.isSuccess()).thenReturn(true);
        when(successResponse.getGopResponseBody()).thenReturn("{}");

        when(iopClient.execute(any(IopRequest.class), eq(Protocol.TOP)))
                .thenReturn(failResponse)
                .thenReturn(successResponse);

        fetchShippingInfo.getShippingInfo(mockProduct);
        verify(iopClient, times(2)).execute(any(IopRequest.class), eq(Protocol.TOP));
    }
}

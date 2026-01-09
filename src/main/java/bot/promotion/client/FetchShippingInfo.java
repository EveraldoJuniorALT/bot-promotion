package bot.promotion.client;

import bot.promotion.dto.HotProduct;
import bot.promotion.dto.ShippingInfoResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.global.iop.api.IopClient;
import com.global.iop.api.IopRequest;
import com.global.iop.api.IopResponse;
import com.global.iop.domain.Protocol;
import com.global.iop.util.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FetchShippingInfo {
    private final ObjectMapper objectMapper;
    private final IopClient iopClient;

    @Autowired
    public FetchShippingInfo(ObjectMapper objectMapper, IopClient iopClient) {
        this.objectMapper = objectMapper;
        this.iopClient = iopClient;
    }

    public ShippingInfoResponse getShippingInfo(HotProduct product) {
        if (product.getProductId() == null || product.getSkuId() == null) {
            System.out.println("Product ID or SKU ID is null in getShippingInfo");
            return null;
        }
        IopRequest request = getIopRequest(product);

        safeSleep(3000);
        IopResponse responseApi = executeRequest(request);
        if (!responseIsValid(responseApi)) {
            System.out.println("First attempt failed, retrying");
            safeSleep(5000);
            responseApi = executeRequest(request);
        }

        if (responseIsValid(responseApi)) {
            return parseResponse(responseApi);
        }

        System.out.println("Failed to fetch shipping info after retrying.");
        return null;
    }


    private void safeSleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Thread was interrupted during sleep: " + e.getMessage());
        }
    }

    private ShippingInfoResponse parseResponse(IopResponse response) {
        try {
            String jsonBody = response.getGopResponseBody();
            return objectMapper.readValue(jsonBody, ShippingInfoResponse.class);
        } catch (Exception e) {
            System.out.println("Error parsing shipping info response in line 70: " + e.getMessage());
            return null;
        }
    }

    private boolean responseIsValid(IopResponse response) {
        return response != null && response.isSuccess();
    }

    private IopResponse executeRequest(IopRequest request) {
        try {
            return iopClient.execute(request, Protocol.TOP);
        } catch (ApiException e) {
            System.out.println("Error executing API request in line 56: " + e.getMessage());
            return null;
        }
    }

    private IopRequest getIopRequest(HotProduct product) {
        IopRequest request = new IopRequest();
        request.setApiName("aliexpress.affiliate.product.shipping.get");
        request.addApiParameter("product_id", product.getProductId());
        request.addApiParameter("sku_id", product.getSkuId());
        request.addApiParameter("ship_to_country", "BR");
        request.addApiParameter("target_currency", "BRL");
        request.addApiParameter("target_sale_price", product.getSalePriceApp());
        request.addApiParameter("target_language", "PT");
        request.addApiParameter("tax_rate", "0.0");
        return request;
    }
}

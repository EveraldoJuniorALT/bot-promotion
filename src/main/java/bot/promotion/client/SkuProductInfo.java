package bot.promotion.client;

import bot.promotion.dto.SkuProductResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.global.iop.api.IopClient;
import com.global.iop.api.IopRequest;
import com.global.iop.api.IopResponse;
import com.global.iop.domain.Protocol;
import com.global.iop.util.ApiException;
import org.springframework.stereotype.Service;


@Service
public class SkuProductInfo {
    private final IopClient iopClient;
    private final ObjectMapper objectMapper;

    public SkuProductInfo(IopClient iopClient, ObjectMapper objectMapper) {
        this.iopClient = iopClient;
        this.objectMapper = objectMapper;
    }

    public SkuProductResponse getSkuProduct(String productId) {
        IopRequest request = getIopRequest(productId);

        safeSleep(5000);
        IopResponse responseApi = executeRequest(request);

        if (!responseIsValid(responseApi)) {
            System.out.println("First attempt failed, retrying");
            safeSleep(7000);
            responseApi = executeRequest(request);
        }

        if (responseIsValid(responseApi)) {
            return parseResponse(responseApi);
        }
        System.out.println("Failed to fetch SKU product details after retrying.");
        return null;
    }

    private boolean responseIsValid(IopResponse response) {
        return response != null && response.isSuccess();
    }

    private SkuProductResponse parseResponse(IopResponse responseApi) {
        try {
            String jsonBody = responseApi.getGopResponseBody();
            return objectMapper.readValue(jsonBody, SkuProductResponse.class);
        } catch (Exception e) {
            System.out.println("Error parsing SKU product response in line 59: " + e.getMessage());
            return null;
        }
    }

    private void safeSleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Thread was interrupted during sleep: " + e.getMessage());
        }
    }

    private IopResponse executeRequest(IopRequest request) {
        try {
            return iopClient.execute(request, Protocol.TOP);
        } catch (ApiException e) {
            System.out.println("Error executing API request in line 64: " + e.getMessage());
            return null;
        }
    }

    private IopRequest getIopRequest(String productId) {
        IopRequest request = new IopRequest();
        request.setApiName("aliexpress.affiliate.product.sku.detail.get");
        request.addApiParameter("ship_to_country", "BR");
        request.addApiParameter("product_id", productId);
        request.addApiParameter("target_currency", "BRL");
        request.addApiParameter("target_language", "EN");
        request.addApiParameter("need_deliver_info", "Yes");
        return request;
    }
}

package bot.promotion.client;

import bot.promotion.dto.SkuProductResponse;
import bot.promotion.service.NotificationService;
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
    private final NotificationService notify;

    public SkuProductInfo(IopClient iopClient, ObjectMapper objectMapper, NotificationService notify) {
        this.iopClient = iopClient;
        this.objectMapper = objectMapper;
        this.notify = notify;
    }

    public SkuProductResponse getSkuProduct(String productId) {
        IopRequest request = getIopRequest(productId);

        safeSleep(5000);
        IopResponse responseApi = executeRequest(request);

        if (!responseIsValid(responseApi)) {
            safeSleep(7000);
            responseApi = executeRequest(request);
        }

        if (responseIsValid(responseApi)) {
            return parseResponse(responseApi);
        }
        notify.sendWarningMessage("Failed to fetch SKU product details after retrying.");
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
            notify.sendErrorMessage("Error parsing SKU product response in line 53: ", e);
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
            notify.sendErrorMessage("Error executing API request in line 71: ", e);
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

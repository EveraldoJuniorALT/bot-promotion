package bot.promotion.client;

import bot.promotion.dto.HotProduct;
import bot.promotion.dto.ShippingInfoResponse;
import bot.promotion.telegram.service.NotificationService;
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
    private final NotificationService notify;
    private static final int MAX_ATTEMPTS = 15;

    @Autowired
    public FetchShippingInfo(ObjectMapper objectMapper, IopClient iopClient, NotificationService notify) {
        this.objectMapper = objectMapper;
        this.iopClient = iopClient;
        this.notify = notify;
    }

    public ShippingInfoResponse getShippingInfo(HotProduct product) {
        if (product.getProductId() == null || product.getSkuId() == null) {
            notify.sendWarningMessage("Product ID or SKU ID is null in getShippingInfo");
            return null;
        }
        IopRequest request = getIopRequest(product);
        int attempts = 0;
        while (attempts < MAX_ATTEMPTS) {
            attempts++;
            IopResponse responseApi = executeRequest(request);
            if (isCallLimitError(responseApi)) {
                notify.sendWarningMessage("Call Limit Exceeded, retrying");
                safeSleep(2000);
                continue;
            }

            if (responseIsValid(responseApi)) {
                return parseResponse(responseApi);
            }
            break;
        }

        notify.sendWarningMessage("Failed to fetch shipping info after retrying.");
        return null;
    }


    private void safeSleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            notify.sendErrorMessage("Thread was interrupted during sleep: ", e);
        }
    }

    private boolean responseIsValid(IopResponse response) {
        return response != null && response.isSuccess();
    }

    private ShippingInfoResponse parseResponse(IopResponse response) {
        try {
            String jsonBody = response.getGopResponseBody();
            return objectMapper.readValue(jsonBody, ShippingInfoResponse.class);
        } catch (Exception e) {
            notify.sendErrorMessage("Error parsing shipping info response in line 74: ", e);
            return null;
        }
    }

    private boolean isCallLimitError(IopResponse responseApi) {
        if (responseApi == null) return false;

        if ("ApiCallLimit".equalsIgnoreCase(responseApi.getGopErrorCode())) return true;
        return responseApi.getGopResponseBody() != null &&
                (responseApi.getGopResponseBody().toLowerCase().contains("apicalllimit") ||
                        responseApi.getGopResponseBody().toLowerCase().contains("api access frequency exceeds"));
    }

    private IopResponse executeRequest(IopRequest request) {
        try {
            return iopClient.execute(request, Protocol.TOP);
        } catch (ApiException e) {
            notify.sendErrorMessage("Error executing API request in line 92: ", e);
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

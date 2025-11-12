package bot.promotion.client;

import bot.promotion.dto.SkuProductResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.global.iop.api.IopClient;
import com.global.iop.api.IopRequest;
import com.global.iop.api.IopResponse;
import com.global.iop.domain.Protocol;
import com.global.iop.util.ApiException;
import org.springframework.stereotype.Component;


@Component
public class SkuProductInfo {
    private final IopClient iopClient;
    private final ObjectMapper objectMapper;

    public SkuProductInfo(IopClient iopClient, ObjectMapper objectMapper) {
        this.iopClient = iopClient;
        this.objectMapper = objectMapper;
    }

    public SkuProductResponse getSkuProduct(String productId, String skuID) {

        IopRequest request = new IopRequest();
        request.setApiName("aliexpress.affiliate.product.sku.detail.get");
        request.addApiParameter("ship_to_country", "BR");
        request.addApiParameter("product_id", productId);
        request.addApiParameter("target_currency", "BRL");
        request.addApiParameter("target_language", "EN");
        request.addApiParameter("need_deliver_info", "Yes");
        request.addApiParameter("sku_ids", skuID);

        try {
            Thread.sleep(5000);
            IopResponse response = iopClient.execute(request, Protocol.TOP);
            if (!response.isSuccess()) {
                try {
                    Thread.sleep(4000);
                    response = iopClient.execute(request, Protocol.TOP);
                    if (!response.isSuccess()) {
                        System.out.println("Error answer from API is null in line 41");
                        return null;
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    System.out.println("Thread was interrupted during getSkuProduct retry");
                    return null;
                } catch (ApiException e) {
                    System.out.println("Error get SKU product in line 49 on getSkuProduct on second try: " + e.getMessage());
                    return null;
                } catch (Exception e) {
                    System.out.println("Unexpected error in line 52 on getSkuProduct on second try: " + e.getMessage());
                    return null;
                }
            }
            String jsonBody = response.getGopResponseBody();
            return objectMapper.readValue(jsonBody, SkuProductResponse.class);
        } catch (ApiException e) {
            System.out.println("Error get SKU product in line 59 on getSkuProduct: " + e.getMessage());
            return null;
        } catch (Exception e) {
            System.out.println("Unexpected error in line 62 on getSkuProduct: " + e.getMessage());
            return null;
        }
    }
}

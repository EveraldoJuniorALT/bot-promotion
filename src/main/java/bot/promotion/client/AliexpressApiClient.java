package bot.promotion.client;

import bot.promotion.dto.HotProductResponse;
import bot.promotion.model.Token;
import bot.promotion.repository.TokenRepository;
import com.aliexpress.open.request.AliexpressAffiliateHotproductQueryRequest;
import com.aliexpress.open.response.AliexpressAffiliateHotproductQueryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.global.iop.api.IopClient;
import com.global.iop.util.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AliexpressApiClient {
    @Value("${aliexpress.app.tracking-id}")
    private String trackingId;

    private final IopClient iopClient;
    private final TokenRepository tokenRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public AliexpressApiClient(IopClient iopClient, TokenRepository tokenRepository, ObjectMapper objectMapper) {
        this.iopClient = iopClient;
        this.tokenRepository = tokenRepository;
        this.objectMapper = objectMapper;
    }

    public void getHotProduct(String fields) {
        Optional<Token> tokenDB = tokenRepository.findById("aliexpress_token");
        if (tokenDB.isEmpty()) {
            System.out.println("Response from DB about token not found");
            return;
        }
        String accessToken = tokenDB.get().getAccessToken();

        AliexpressAffiliateHotproductQueryRequest request = getAliexpressAffiliateHotproductQueryRequest();

        try {
            AliexpressAffiliateHotproductQueryResponse responseApi = iopClient.execute(request, accessToken);
            if (!responseApi.isSuccess()) {
                System.out.println("Error answer from API is null in line 52");
                return;
            }
            
            String jsonBody = responseApi.getGopResponseBody();
            objectMapper.readValue(jsonBody, HotProductResponse.class);

        } catch (ApiException e) {
            System.out.println("Error get hot products in line 60 on getHotProduct" + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error get hot products in line 63 on  getHotProduct" + e.getMessage());
        }
    }

    private AliexpressAffiliateHotproductQueryRequest getAliexpressAffiliateHotproductQueryRequest() {
        AliexpressAffiliateHotproductQueryRequest request = new AliexpressAffiliateHotproductQueryRequest();
        request.addApiParameter("category_ids", "7, 44");
        request.addApiParameter("min_sale_price", "20");
        request.addApiParameter("max_sale_price", "2000");
        request.addApiParameter("page_no", "1");
        request.addApiParameter("page_size", "50");
        request.addApiParameter("platform_product_type", "all");
        request.addApiParameter("sort", "SALE_PRICE_ASC" );
        request.addApiParameter("target_currency", "BRL");
        request.addApiParameter("target_language", "PT-BR");
        request.addApiParameter("tracking_id", trackingId);
        request.addApiParameter("ship_to_country", "BR");
        return request;
    }
}

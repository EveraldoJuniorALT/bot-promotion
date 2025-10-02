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

    public HotProductResponse getHotProduct(int pageNo) {
        Optional<Token> tokenDB = tokenRepository.findById("aliexpress_token");
        if (tokenDB.isEmpty()) {
            System.out.println("Response from DB about token not found");
            return null;
        }
        String accessToken = tokenDB.get().getAccessToken();
        if (accessToken == null) {
            System.out.println("Access token is null in line 41");
            return null;
        }
        AliexpressAffiliateHotproductQueryRequest request = getHotproductQueryRequest(pageNo);

        try {
            AliexpressAffiliateHotproductQueryResponse responseApi = iopClient.execute(request, accessToken);
            if (!responseApi.isSuccess()) {
                System.out.println("Error answer from API is null in line 49");
                return null;
            }

            String jsonBody = responseApi.getGopResponseBody();
            return objectMapper.readValue(jsonBody, HotProductResponse.class);

        } catch (ApiException e) {
            System.out.println("Error get hot products in line 57 on getHotProduct" + e.getMessage());
            return null;
        } catch (Exception e) {
            System.out.println("Error get hot products in line 60 on  getHotProduct" + e.getMessage());
            return null;
        }
    }

    private AliexpressAffiliateHotproductQueryRequest getHotproductQueryRequest(int pageNo) {
        AliexpressAffiliateHotproductQueryRequest request = new AliexpressAffiliateHotproductQueryRequest();
        request.setCategoryIds("7, 44");
        request.setFields("tax_rate,product_id,product_title,product_main_image_url,target_app_sale_price,promo_code_info");
        request.setMinSalePrice(20L);
        request.setMaxSalePrice(2000L);
        request.setPageNo(Long.valueOf(pageNo));
        request.setPageSize(50L);
        request.setPlatformProductType("ALL");
        request.setSort("SALE_PRICE_ASC");
        request.setTargetCurrency("BRL");
        request.setTargetLanguage("PT-BR");
        request.setTrackingId(trackingId);
        request.setShipToCountry("BR");
        return request;
    }
}

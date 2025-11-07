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

import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class AliexpressApiClient {
    @Value("${aliexpress.app.tracking-id}")
    private String trackingId;

    private final IopClient iopClient;
    private final TokenRepository tokenRepository;
    private final ObjectMapper objectMapper;
    private String cachedAccessToken;
    private LocalDateTime tokenFetchTime;
    private static final long CACHE_DURATION_MINUTES = 18;

    @Autowired
    public AliexpressApiClient(IopClient iopClient, TokenRepository tokenRepository, ObjectMapper objectMapper) {
        this.iopClient = iopClient;
        this.tokenRepository = tokenRepository;
        this.objectMapper = objectMapper;
    }

    public HotProductResponse getHotProduct(int pageNo) {
        String accessToken = getValidAccessToken();
        if (accessToken == null) {
            System.out.println("Access token is null in line 42");
            return null;
        }

        AliexpressAffiliateHotproductQueryRequest request = getHotproductQueryRequest(pageNo);

        try {
            AliexpressAffiliateHotproductQueryResponse responseApi = iopClient.execute(request, accessToken);
            if (!responseApi.isSuccess()) {
                System.out.println("Error answer from API is null in line 50");
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

    private synchronized String getValidAccessToken() {
        LocalDateTime now = LocalDateTime.now();
        if (cachedAccessToken == null || tokenFetchTime == null || tokenFetchTime.isBefore(now.minusMinutes(CACHE_DURATION_MINUTES))) {
            try {
                Optional<Token> tokenDB = tokenRepository.findById("aliexpress_token");
                if (tokenDB.isEmpty()) {
                    System.out.println("Token not found in DB");
                    return null;
                }

                if (tokenDB.get().getAccessToken() == null) {
                    System.out.println("Access token is null in line 80");
                    return null;
                }

                this.cachedAccessToken = tokenDB.get().getAccessToken();
                this.tokenFetchTime = now;
            } catch (Exception e) {
                System.out.println("Error fetching access token: " + e.getMessage());
                return this.cachedAccessToken;
            }
        }
        return this.cachedAccessToken;
    }

    private AliexpressAffiliateHotproductQueryRequest getHotproductQueryRequest(int pageNo) {
        AliexpressAffiliateHotproductQueryRequest request = new AliexpressAffiliateHotproductQueryRequest();
        request.setCategoryIds("200001074");
        request.setFields("sku_id,evaluate_rate,product_id,product_title,target_app_sale_price,promo_code_info");
        request.setKeywords("PC peripherals,gaming accessories,mouse,keyboard,webcam,headset,controller,gadgets");
        request.setMinSalePrice(1500L);
        request.setMaxSalePrice(200000L);
        request.setPageNo(Long.valueOf(pageNo));
        request.setPageSize(50L);
        request.setPlatformProductType("ALL");
        request.setTargetCurrency("BRL");
        request.setTargetLanguage("PT-BR");
        request.setTrackingId(trackingId);
        request.setShipToCountry("BR");
        return request;
    }
}

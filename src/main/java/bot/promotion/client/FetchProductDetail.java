package bot.promotion.client;

import bot.promotion.dto.HotProductResponse;
import bot.promotion.model.Token;
import bot.promotion.repository.TokenRepository;
import com.aliexpress.open.request.AliexpressAffiliateProductdetailGetRequest;
import com.aliexpress.open.response.AliexpressAffiliateProductdetailGetResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.global.iop.api.IopClient;
import com.global.iop.util.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class FetchProductDetail {
    @Value("${aliexpress.app.tracking-id}")
    private String trackingId;

    private final IopClient iopClient;
    private final TokenRepository tokenRepository;
    private final ObjectMapper objectMapper;
    private String cachedAccessToken;
    private LocalDateTime tokenFetchTime;
    private static final long CACHE_DURATION_MINUTES = 18;

    @Autowired
    public FetchProductDetail(IopClient iopClient, TokenRepository tokenRepository, ObjectMapper objectMapper) {
        this.iopClient = iopClient;
        this.tokenRepository = tokenRepository;
        this.objectMapper = objectMapper;
    }

    public HotProductResponse productDetail(String productId) {
        String accessToken = getValidAccessToken();
        if (accessToken == null) {
            System.out.println("Access token is null in line 40");
            return null;
        }

        AliexpressAffiliateProductdetailGetRequest request = getProductDetailRequest(productId);

        try {
            AliexpressAffiliateProductdetailGetResponse responseApi = iopClient.execute(request, accessToken);

            if (!responseApi.isSuccess()) {
                try {
                    Thread.sleep(2000);
                    responseApi = iopClient.execute(request, accessToken);
                    if (!responseApi.isSuccess()) {
                        System.out.println("Error answer from API is null in line 50 on second attempt");
                        return null;
                    }
                } catch (InterruptedException e) {
                    System.out.println("Thread was interrupted during fetch product detail in line 58 on second attempt: " + e.getMessage());
                    return null;
                } catch (ApiException e) {
                    System.out.println("Error get hot products in line 61 on getHotProduct on second attempt" + e.getMessage());
                    return null;
                } catch (Exception e) {
                    System.out.println("Error get hot products in line 64 on getHotProduct on second attempt" + e.getMessage());
                    return null;
                }
            }

            String jsonBody = responseApi.getGopResponseBody();
            return objectMapper.readValue(jsonBody, HotProductResponse.class);

        } catch (ApiException e) {
            System.out.println("Error get hot products in line 73 on getHotProduct" + e.getMessage());
            return null;
        } catch (Exception e) {
            System.out.println("Error get hot products in line 76 on getHotProduct" + e.getMessage());
            return null;
        }
    }

    private synchronized String getValidAccessToken() {
        LocalDateTime now = LocalDateTime.now();
        if (cachedAccessToken == null || tokenFetchTime == null || tokenFetchTime.isBefore((now.minusMinutes(CACHE_DURATION_MINUTES)))) {
            try {
                Optional<Token> tokenDB = tokenRepository.findById("aliexpress_token");
                if (tokenDB.isEmpty()) {
                    System.out.println("Token not found in database.");
                    return null;
                }

                if (tokenDB.get().getAccessToken() == null) {
                    System.out.println("Access token is null in line 92");
                    return null;
                }

                this.cachedAccessToken = tokenDB.get().getAccessToken();
                this.tokenFetchTime = now;
            } catch (Exception e) {
                System.out.println("Error fetching token from database: " + e.getMessage());
                return this.cachedAccessToken;
            }
        }

        return this.cachedAccessToken;
    }

    private AliexpressAffiliateProductdetailGetRequest getProductDetailRequest(String productId) {
        AliexpressAffiliateProductdetailGetRequest request = new AliexpressAffiliateProductdetailGetRequest();
        request.setProductIds(productId);
        request.setTargetCurrency("BRL");
        request.setTrackingId(trackingId);
        request.setCountry("BR");
        return request;
    }
}

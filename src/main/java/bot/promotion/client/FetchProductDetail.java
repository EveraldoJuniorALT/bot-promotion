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
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
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

        safeSleep(3000); // Sleep for 3 seconds before making the request
        AliexpressAffiliateProductdetailGetResponse responseApi = executeRequest(request, accessToken);
        if (!responseIsValid(responseApi)) {
            System.out.println("First attempt failed, retrying on fetchProductDetail");
            safeSleep(5000); // Sleep for 5 seconds before retrying
            responseApi = executeRequest(request, accessToken);
        }

        if (responseIsValid(responseApi)) {
            return parseResponse(responseApi);
        }
        System.out.println("Failed to fetch product details after retrying.");
        return null;
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
                    System.out.println("Access token is null in line 71");
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

    private void safeSleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Thread was interrupted during sleep: " + e.getMessage());
        }
    }

    private boolean responseIsValid(AliexpressAffiliateProductdetailGetResponse response) {
        return response != null && response.isSuccess();
    }

    private HotProductResponse parseResponse(AliexpressAffiliateProductdetailGetResponse response) {
        try {
            String jsonBody = response.getGopResponseBody();
            return objectMapper.readValue(jsonBody, HotProductResponse.class);
        } catch (Exception e) {
            System.out.println("Error parsing JSON response: " + e.getMessage());
            return null;
        }
    }

    private AliexpressAffiliateProductdetailGetResponse executeRequest(AliexpressAffiliateProductdetailGetRequest request, String accessToken) {
        try {
            return iopClient.execute(request, accessToken);
        } catch (ApiException e) {
            System.out.println("Error executing API request in line 113: " + e.getMessage());
            return null;
        }
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

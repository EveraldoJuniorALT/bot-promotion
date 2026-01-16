package bot.promotion.client;

import bot.promotion.dto.HotProductResponse;
import bot.promotion.entity.Token;
import bot.promotion.repository.TokenRepository;
import com.aliexpress.open.request.AliexpressAffiliateHotproductQueryRequest;
import com.aliexpress.open.response.AliexpressAffiliateHotproductQueryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.global.iop.api.IopClient;
import com.global.iop.util.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
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

    public HotProductResponse getHotProduct(int pageNo, String keyword) {
        String accessToken = getValidAccessToken();
        if (accessToken == null) {
            System.out.println("Access token is null in line 40");
            return null;
        }
        AliexpressAffiliateHotproductQueryRequest request = getHotProductQueryRequest(pageNo, keyword);

        safeSleep(3000); // Sleep for 3 seconds before making the request
        AliexpressAffiliateHotproductQueryResponse responseApi = executeRequest(request, accessToken);
        if (!responseIsValid(responseApi)) {
            System.out.println("First attempt failed, retrying");
            safeSleep(5000); // Sleep for 5 seconds before retrying
            responseApi = executeRequest(request, accessToken);
        }

        if (responseIsValid(responseApi)) {
            return parseResponse(responseApi);
        }
        System.out.println("Failed to fetch hot products after retrying.");
        return null;
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
                    System.out.println("Access token is null in line 71");
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

    private void safeSleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Thread was interrupted during sleep: " + e.getMessage());
        }
    }

    private boolean responseIsValid(AliexpressAffiliateHotproductQueryResponse responseApi) {
        return responseApi != null && responseApi.isSuccess();
    }

    private HotProductResponse parseResponse(AliexpressAffiliateHotproductQueryResponse responseApi) {
        try {
            String jsonBody = responseApi.getGopResponseBody();
            return objectMapper.readValue(jsonBody, HotProductResponse.class);
        } catch (Exception e) {
            System.out.println("Error parsing JSON response in line 103: " + e.getMessage());
            return null;
        }
    }

    private AliexpressAffiliateHotproductQueryResponse executeRequest(AliexpressAffiliateHotproductQueryRequest request, String accessToken) {
        try {
            return iopClient.execute(request, accessToken);
        } catch (ApiException e) {
            System.out.println("Error executing API request in line 112: " + e.getMessage());
            return null;
        }
    }

    private AliexpressAffiliateHotproductQueryRequest getHotProductQueryRequest(int pageNo, String keyword) {
        AliexpressAffiliateHotproductQueryRequest request = new AliexpressAffiliateHotproductQueryRequest();
        request.setCategoryIds("200001081");
        request.setKeywords(keyword);
        request.setMinSalePrice(1500L);
        request.setMaxSalePrice(200000L);
        request.setPageNo((long) pageNo);
        request.setPageSize(50L);
        request.setPlatformProductType("ALL");
        request.setTargetCurrency("BRL");
        request.setTargetLanguage("PT");
        request.setTrackingId(trackingId);
        request.setShipToCountry("BR");
        return request;
    }
}

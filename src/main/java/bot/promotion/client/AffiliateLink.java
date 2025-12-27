package bot.promotion.client;

import bot.promotion.dto.AffiliateLinkResponse;
import bot.promotion.model.Token;
import bot.promotion.repository.TokenRepository;
import com.aliexpress.open.request.AliexpressAffiliateLinkGenerateRequest;
import com.aliexpress.open.response.AliexpressAffiliateLinkGenerateResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.global.iop.api.IopClient;
import com.global.iop.util.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AffiliateLink {
    @Value("${aliexpress.app.tracking-id}")
    private String tracking;

    private final IopClient iopClient;
    private final TokenRepository tokenRepository;
    private final ObjectMapper objectMapper;
    private String cachedAccessToken;
    private LocalDateTime tokenFetchTime;
    private static final long CHACHE_DURATION_MINUTES = 18;

    @Autowired
    public AffiliateLink(TokenRepository tokenRepository, ObjectMapper objectMapper, IopClient iopClient) {
        this.tokenRepository = tokenRepository;
        this.objectMapper = objectMapper;
        this.iopClient = iopClient;
    }

    public String generateAffiliateLink(String productURL) {
        String accessToken = getValidAccessToken();
        if (accessToken == null) {
            System.out.println("Access token is null in line 40");
            return null;
        }

        AliexpressAffiliateLinkGenerateRequest request = getAffiliateLinkRequest(productURL);
        safeSleep(5000); // Sleep for 5 seconds before making the request

        AliexpressAffiliateLinkGenerateResponse linkResponse = executeRequest(request, accessToken);
        if (!responseIsValid(linkResponse)) {
            System.out.println("First attempt to generate affiliate link failed, retrying");
            safeSleep(7000); // Sleep for 7 seconds before retrying
            linkResponse = executeRequest(request, accessToken);
        }

        if (responseIsValid(linkResponse)) {
            return parseResponse(linkResponse);
        }
        System.out.println("Failed to generate affiliate link after retrying.");
        return null;
    }

    private String getValidAccessToken() {
        LocalDateTime now = LocalDateTime.now();
        if (cachedAccessToken == null || tokenFetchTime == null || tokenFetchTime.isBefore(now.minusMinutes(CHACHE_DURATION_MINUTES))) {
            try {
                Optional<Token> tokenDB = tokenRepository.findById("aliexpress_token");
                if (tokenDB.isEmpty()) {
                    System.out.println("Token not found in DB");
                    return null;
                }

                if (tokenDB.get().getAccessToken() == null) {
                    System.out.println("Access token is null in line 99");
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

    private boolean responseIsValid(AliexpressAffiliateLinkGenerateResponse responseApi) {
        return responseApi != null && responseApi.isSuccess();
    }

    private String parseResponse(AliexpressAffiliateLinkGenerateResponse responseApi) {
        try {
            String jsonBody = responseApi.getGopResponseBody();
            AffiliateLinkResponse customResponse = objectMapper.readValue(jsonBody, AffiliateLinkResponse.class);
            return customResponse.getRespResult()
                    .getResult()
                    .getPromotionLinks()
                    .getFirst()
                    .getPromotionLink();
        } catch (Exception e) {
            System.out.println("Error parsing JSON response in line 117: " + e.getMessage());
            return null;
        }
    }

    private AliexpressAffiliateLinkGenerateResponse executeRequest(AliexpressAffiliateLinkGenerateRequest request, String accessToken) {
        try {
            return iopClient.execute(request, accessToken);
        } catch (ApiException e) {
            System.out.println("Error executing API request in line 137: " + e.getMessage());
            return null;
        }
    }

    private AliexpressAffiliateLinkGenerateRequest getAffiliateLinkRequest(String productURL) {
        AliexpressAffiliateLinkGenerateRequest request = new AliexpressAffiliateLinkGenerateRequest();
        request.setPromotionLinkType(0L);
        request.setSourceValues(productURL);
        request.setTrackingId(tracking);
        return request;
    }
}
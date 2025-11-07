package bot.promotion.service;

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
public class AffiliateService {
    @Value("${aliexpress.app.tracking-id}")
    private String tracking;

    private final IopClient iopClient;
    private final TokenRepository tokenRepository;
    private final ObjectMapper objectMapper;
    private String cachedAccessToken;
    private LocalDateTime tokenFetchTime;
    private static final long CHACHE_DURATION_MINUTES = 18;

    @Autowired
    public AffiliateService(TokenRepository tokenRepository, ObjectMapper objectMapper, IopClient iopClient) {
        this.tokenRepository = tokenRepository;
        this.objectMapper = objectMapper;
        this.iopClient = iopClient;
    }

    public String generateAffiliateLink(String productURL) {
        String accessToken = getValidAccessToken();
        if (accessToken == null) {
            System.out.println("Access token is null in line 42");
            return null;
        }

        AliexpressAffiliateLinkGenerateRequest request = new AliexpressAffiliateLinkGenerateRequest();
        request.setPromotionLinkType(0L);
        request.setSourceValues(productURL);
        request.setTrackingId(tracking);

        try {
            AliexpressAffiliateLinkGenerateResponse linkResponse = iopClient.execute(request, accessToken);
            if (!linkResponse.isSuccess()) {
                System.out.println("Error answer from API is null in line 52");
                return null;
            }

            String jsonBody = linkResponse.getGopResponseBody();
            AffiliateLinkResponse customResponse = objectMapper.readValue(jsonBody, AffiliateLinkResponse.class);

            return customResponse.getRespResult()
                    .getResult()
                    .getPromotionLinks()
                    .getFirst()
                    .getPromotionLink();
        } catch (ApiException e) {
            System.out.println("Error generate affiliate link in line 65 on generateAffiliateLink" + e.getMessage());
            return null;
        } catch (Exception e) {
            System.out.println("Error generate link in line 68 on  generateAffiliateLink" + e.getMessage());
            return null;
        }
    }

    public String getValidAccessToken() {
        LocalDateTime now = LocalDateTime.now();
        if (cachedAccessToken == null || tokenFetchTime == null || tokenFetchTime.isBefore(now.minusMinutes(CHACHE_DURATION_MINUTES))) {
            try {
                Optional<Token> tokenDB = tokenRepository.findById("aliexpress_token");
                if (tokenDB.isEmpty()) {
                    System.out.println("Token not found in DB");
                    return null;
                }

                if (tokenDB.get().getAccessToken() == null) {
                    System.out.println("Access token is null in line 90");
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
}
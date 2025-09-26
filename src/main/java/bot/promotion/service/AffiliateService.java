package bot.promotion.service;

import bot.promotion.dto.AliexpressLinkResponse;
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

import java.util.Optional;

@Service
public class AffiliateService {
    @Value("${aliexpress.app.tracking-id}")
    private String tracking;

    private final IopClient iopClient;
    private final TokenRepository tokenRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public AffiliateService(TokenRepository tokenRepository, ObjectMapper objectMapper, IopClient iopClient) {
        this.tokenRepository = tokenRepository;
        this.objectMapper = objectMapper;
        this.iopClient = iopClient;
    }

    public String generateAffiliateLink(String productURL) {
        Optional<Token> tokenDB = tokenRepository.findById("aliexpress_token");
        if (tokenDB.isEmpty()) {
            System.out.println("Response from DB about token not found");
            return null;
        }
        String accessToken = tokenDB.get().getAccessToken();

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
            AliexpressLinkResponse customResponse = objectMapper.readValue(jsonBody, AliexpressLinkResponse.class);
            System.out.println(customResponse);

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
}
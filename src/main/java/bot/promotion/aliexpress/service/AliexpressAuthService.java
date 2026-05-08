package bot.promotion.aliexpress.service;

import bot.promotion.product.dto.TokenResponse;
import bot.promotion.product.entity.Token;
import bot.promotion.product.repository.TokenRepository;
import bot.promotion.telegram.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.global.iop.api.IopClient;
import com.global.iop.api.IopRequest;
import com.global.iop.api.IopResponse;
import com.global.iop.domain.Protocol;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AliexpressAuthService {

    @Value("${aliexpress.api.name-url}")
    private String apiName;

    @Value("${aliexpress.api.refresh-name-url}")
    private String refreshTokenApiName;

    private final ObjectMapper objectMapper;
    private final TokenRepository tokenRepository;
    private final IopClient iopClient;
    private final NotificationService notify;

    public void exchangeCodeForToken(String code) {
        try {
            IopRequest iopRequest = new IopRequest();
            iopRequest.setApiName(apiName);
            iopRequest.addApiParameter("code", code);

            IopResponse response = iopClient.execute(iopRequest, Protocol.GOP);
            if (!response.isSuccess()) {
                notify.sendWarningMessage("Answer from API is null in line 49 on AliexpressAuthService.exchangeCodeForToken");
                return;
            }

            String jsonBody = response.getGopResponseBody();
            TokenResponse tokenResponse = objectMapper.readValue(jsonBody, TokenResponse.class);

            Token tokenEntity = new Token(
                    "aliexpress_token",
                    tokenResponse.getAccessToken(),
                    tokenResponse.getRefreshToken(),
                    tokenResponse.getExpiresIn()
            );

            tokenRepository.save(tokenEntity);
            System.out.println("Saved in DB successfully");
        } catch (HttpClientErrorException e) {
            notify.sendErrorMessage("Http error when calling Aliexpress API, Line 66 on AliexpressAuthService.exchangeCodeForToken: ", e);
        } catch (Exception e) {
            notify.sendErrorMessage("Error in line 68 on AliexpressAuthService.exchangeCodeForToken ", e);
        }
    }

    public void refreshToken() {
        Optional<Token> optionalToken = tokenRepository.findById("aliexpress_token");
        if (optionalToken.isEmpty()) {
            notify.sendWarningMessage("Token not found in DB");
            return;
        }

        Token currentToken = optionalToken.get();
        String refreshToken = currentToken.getRefreshToken();

        try {
            IopRequest iopRequest = new IopRequest();
            iopRequest.setApiName(refreshTokenApiName);
            iopRequest.addApiParameter("refresh_token", refreshToken);

            IopResponse response = iopClient.execute(iopRequest, Protocol.GOP);

            if (!response.isSuccess()) {
                notify.sendWarningMessage("Answer from API is null, access token not renewed. Line 90 ");
                return;
            }

            String jsonBody = response.getGopResponseBody();
            TokenResponse newTokens = objectMapper.readValue(jsonBody, TokenResponse.class);

            currentToken.setAccessToken(newTokens.getAccessToken());
            currentToken.setRefreshToken(newTokens.getRefreshToken());
            currentToken.setExpiresIn(newTokens.getExpiresIn());

            tokenRepository.save(currentToken);
            System.out.println("Successfully! AccessToken renewed in DB");
        } catch (HttpClientErrorException e) {
            notify.sendErrorMessage("Http error when calling Ali API in line 104 on AliexpressAuthService.refreshToken: ", e);
        } catch (Exception e) {
            notify.sendErrorMessage("Error in line 106 on AliexpressAuthService.refreshToken", e);
        }
    }
}

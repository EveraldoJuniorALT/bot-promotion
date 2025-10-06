package bot.promotion.service;

import bot.promotion.dto.TokenResponse;
import bot.promotion.model.Token;
import bot.promotion.repository.TokenRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.global.iop.api.IopClient;
import com.global.iop.api.IopRequest;
import com.global.iop.api.IopResponse;
import com.global.iop.domain.Protocol;
import com.global.iop.util.IopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class AliexpressAuthService {

    @Value("${aliexpress.app.key}")
    private String appKey;

    @Value("${aliexpress.app.secret}")
    private String appSecret;

    @Value("${aliexpress.api.token-base-url-http}")
    private String baseUrl;

    @Value("${aliexpress.api.name-url}")
    private String apiName;

    @Value("${aliexpress.api.refresh-name-url}")
    private String refreshTokenApiName;

    private final ObjectMapper objectMapper;
    private final TokenRepository tokenRepository;
    private final IopClient iopClient;

    @Autowired
    public AliexpressAuthService(ObjectMapper objectMapper, TokenRepository tokenRepository, IopClient iopClient) {
        this.iopClient = iopClient;
        this.objectMapper = objectMapper;
        this.tokenRepository = tokenRepository;
    }

    public void exchangeCodeForToken(String code) {
        try {
            IopRequest iopRequest = new IopRequest();
            iopRequest.setApiName(apiName);
            iopRequest.addApiParameter("code", code);

            IopResponse response = iopClient.execute(iopRequest, Protocol.GOP);
            if (!response.isSuccess()) {
                System.out.println("Error: Answer from API is null in line 66 on AliexpressAuthService.exchangeCodeForToken");
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
            System.err.println("Http error when calling Aliexpress API, Line 84 on AliexpressAuthService.exchangeCodeForToken: " + e.getStatusCode());
            System.err.println("Error response body: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            System.out.println("Error in line 87 on AliexpressAuthService.exchangeCodeForToken" + e.getMessage());
        }
    }

    public void refreshToken() {
        Optional<Token> optionalToken = tokenRepository.findById("aliexpress_token");
        if (optionalToken.isEmpty()) {
            System.out.println("Token not found in DB");
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
                System.out.println("Answer from API is null, access token not renewed. Line 109 ");
            }

            String jsonBody = response.getGopResponseBody();
            TokenResponse newTokens = objectMapper.readValue(jsonBody, TokenResponse.class);

            currentToken.setAccessToken(newTokens.getAccessToken());
            currentToken.setRefreshToken(newTokens.getRefreshToken());
            currentToken.setExpiresIn(newTokens.getExpiresIn());

            tokenRepository.save(currentToken);
            System.out.println("Successfully! AccessToken renewed in DB");
        } catch (HttpClientErrorException e) {
            System.err.println("Http error when calling Ali API in line 122 on AliexpressAuthService.refreshToken: " + e.getStatusCode());
            System.err.println("Error response body: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            System.out.println("Error in line 125 on AliexpressAuthService.refreshToken" + e.getMessage());
        }
    }
}

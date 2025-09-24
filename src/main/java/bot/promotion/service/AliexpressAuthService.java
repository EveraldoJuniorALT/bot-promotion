package bot.promotion.service;

import bot.promotion.dto.TokenResponse;
import bot.promotion.model.Token;
import bot.promotion.repository.TokenRepository;
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

    private final RestTemplate restTemplate;
    private final TokenRepository tokenRepository;

    @Autowired
    public AliexpressAuthService(RestTemplate restTemplate, TokenRepository tokenRepository) {
        this.restTemplate = restTemplate;
        this.tokenRepository = tokenRepository;
    }

    public void exchangeCodeForToken(String code) {
        try {
            long time = System.currentTimeMillis();
            String timeStamp = Long.toString(time);

            Map<String, String> paramsForSign = new HashMap<>();
            paramsForSign.put("app_key", appKey);
            paramsForSign.put("timestamp", timeStamp);
            paramsForSign.put("sign_method", "sha256");
            paramsForSign.put("code", code);
            paramsForSign.put("simplify", "true");

            String signature = IopUtils.signApiRequest(apiName, paramsForSign, null, appSecret, "sha256");
            paramsForSign.put("sign", signature);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();

            String key = "";
            for (Map.Entry<String, String> entry : paramsForSign.entrySet()) {
                key += "&" + entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), "utf-8");
            }

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<TokenResponse> tokenResponse = restTemplate.postForEntity(baseUrl + apiName + "?" + key, request, TokenResponse.class);
            if (tokenResponse == null || tokenResponse.getBody().getAccessToken() == null) {
                System.out.println("Error: Answer from API is null in line 80 on AliexpressAuthService.exchangeCodeForToken");
                return;
            }

            Token tokenEntity = new Token(
                    "aliexpress_token",
                    tokenResponse.getBody().getAccessToken(),
                    tokenResponse.getBody().getRefreshToken(),
                    tokenResponse.getBody().getExpiresIn()
            );

            tokenRepository.save(tokenEntity);
            System.out.println("Saved in DB successfully");

        } catch (HttpClientErrorException e) {
            System.err.println("Http error when calling Aliexpress API, Line 95 on AliexpressAuthService.exchangeCodeForToken: " + e.getStatusCode());
            System.err.println("Error responde body: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            System.out.println("Error in line 98 on AliexpressAuthService.exchangeCodeForToken" + e.getMessage());
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
            long time = System.currentTimeMillis();
            String timeStamp = Long.toString(time);

            Map<String, String> paramsForSign = new HashMap<>();
            paramsForSign.put("app_key", appKey);
            paramsForSign.put("refresh_token", refreshToken);
            paramsForSign.put("timestamp", timeStamp);
            paramsForSign.put("sign_method", "sha256");
            paramsForSign.put("simplify", "true");

            String signature = IopUtils.signApiRequest(refreshTokenApiName, paramsForSign, null, appSecret, "sha256");
            paramsForSign.put("sign", signature);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();

            String key = "";
            for (Map.Entry<String, String> entry : paramsForSign.entrySet()) {
                key += "&" + entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), "utf-8");
            }

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<TokenResponse> tokenResponse = restTemplate.postForEntity(baseUrl + refreshTokenApiName + "?" + key, request, TokenResponse.class);

            if (tokenResponse == null || tokenResponse.getBody().getAccessToken() == null) {
                System.out.println("Answer from API is null, access token not renewed. Line 141 ");
            }

            TokenResponse newTokens = tokenResponse.getBody();
            currentToken.setAccessToken(newTokens.getAccessToken());
            currentToken.setRefreshToken(newTokens.getRefreshToken());
            currentToken.setExpiresIn(newTokens.getExpiresIn());

            tokenRepository.save(currentToken);
            System.out.println("Successfully! AccessToken renewed in DB");
        } catch (HttpClientErrorException e) {
            System.err.println("Http error when calling Ali API in line 152 on AliexpressAuthService.refreshToken: " + e.getStatusCode());
            System.err.println("Error response body: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            System.out.println("Error in line 155 on AliexpressAuthService.refreshToken" + e.getMessage());
        }
    }
}

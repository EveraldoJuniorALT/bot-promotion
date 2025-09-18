package bot.promotion.service;

import bot.promotion.dto.TokenResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
public class AliexpressAuthService {

    @Value("${aliexpress.app.key}")
    private String appKey;

    @Value("${aliexpress.app.secret}")
    private String appSecret;

    @Value("${aliexpress.api.token-url}")
    private String tokenUrl;

    private final RestTemplate restTemplate;

    @Autowired
    public AliexpressAuthService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void exchangeCodeForToken(String code, String callbackUrl){
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("client_id", appKey);
        body.add("client_secret", appSecret);
        body.add("redirect_uri", callbackUrl);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            TokenResponse tokenResponse = restTemplate.postForObject(tokenUrl, request, TokenResponse.class);

            if(tokenResponse == null || tokenResponse.getAccessToken() == null){
                System.out.println("Error: Answer from API is null in line 46 on AliexpressAuthService.exchangeCodeForToken");
            }
            System.out.println("Successfully! access token received" + tokenResponse.getAccessToken());
            System.out.println("Refresh token received" + tokenResponse.getRefreshToken());

        } catch (Exception e) {
            System.out.println("Error in line 51 on AliexpressAuthService.exchangeCodeForToken" + e.getMessage());
        }
    }
}

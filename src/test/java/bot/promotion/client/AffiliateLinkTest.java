package bot.promotion.client;

import bot.promotion.aliexpress.client.AffiliateLink;
import bot.promotion.product.entity.Token;
import bot.promotion.product.repository.TokenRepository;
import bot.promotion.telegram.service.NotificationService;
import com.aliexpress.open.request.AliexpressAffiliateLinkGenerateRequest;
import com.aliexpress.open.response.AliexpressAffiliateLinkGenerateResponse;
import com.global.iop.api.IopClient;
import com.global.iop.util.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ActiveProfiles("dev")
@SpringBootTest
class AffiliateLinkTest {
    @MockitoBean
    private TokenRepository tokenRepository;
    @Autowired
    @MockitoBean
    private IopClient iopClient;
    @MockitoBean
    private NotificationService notificationService;

    @Autowired
    private AffiliateLink affiliateLink;

    private static final String ACCESS_TOKEN = "";

    @Test
    @DisplayName("Should generate affiliate link successfully")
    void generateAffiliateLink() {
        Token token = new Token();
        token.setId("aliexpress_token");
        token.setAccessToken(ACCESS_TOKEN);
        when(tokenRepository.findById("aliexpress_token")).thenReturn(Optional.of(token));

        String urlApp = "https://m.aliexpress.com/p/coin-index/index.html?productIds=1005009178167326";
        String urlPc = "https://pt.aliexpress.com/item/1005009178167326.html";

        List<String> affiliateLinks = affiliateLink.generateAffiliateLink(urlApp, urlPc);

        System.out.println("Generated Affiliate Links: " + affiliateLinks);
        assertNotNull(affiliateLinks, "Affiliate links should not be null");
        assertTrue(affiliateLinks.size() > 1);
        affiliateLinks.forEach(link -> {
            assertNotNull(link, "Link should not be null");
            assertFalse(link.trim().isEmpty(), "Link should not be empty");
            System.out.println("Valid link " + link);
        });
    }

    @Test
    @DisplayName("Should return a limit exceeded response when API call limit is reached and return successfully response after retries")
    void generateAffiliateLinkInvalidResponse() throws ApiException {
        Token token = new Token();
        token.setId("aliexpress_token");
        token.setAccessToken(ACCESS_TOKEN);
        when(tokenRepository.findById("aliexpress_token")).thenReturn(Optional.of(token));

        AliexpressAffiliateLinkGenerateResponse limitResponse = mock(AliexpressAffiliateLinkGenerateResponse.class);
        when(limitResponse.isSuccess()).thenReturn(false);
        when(limitResponse.getGopErrorCode()).thenReturn("ApiCallLimit");
        when(limitResponse.getGopResponseBody()).thenReturn("{\"error_response\":{\"code\":\"apicalllimit\", \"msg\":\"api access frequency exceeds\"}}");

        AliexpressAffiliateLinkGenerateResponse successResponse = mock(AliexpressAffiliateLinkGenerateResponse.class);
        when(successResponse.isSuccess()).thenReturn(true);
        String jsonSuccessBody = "{\"resp_result\":{\"result\":{\"promotion_links\":[{\"promotion_link\":\"https://final-link-1.com\"}, {\"promotion_link\":\"https://final-link-2.com\"}]}}}";
        when(successResponse.getGopResponseBody()).thenReturn(jsonSuccessBody);

        when(iopClient.execute(any(AliexpressAffiliateLinkGenerateRequest.class), eq(ACCESS_TOKEN)))
                .thenReturn(limitResponse)
                .thenReturn(limitResponse)
                .thenReturn(successResponse);

        String urlApp = "teste/app";
        String urlPc = "teste/pc";

        List<String> affiliateLinks = affiliateLink.generateAffiliateLink(urlApp, urlPc);
        assertNotNull(affiliateLinks, "Affiliate links should not be null");
        assertEquals(2, affiliateLinks.size(), "There should be 2 affiliate links returned");
        assertTrue(affiliateLinks.contains("https://final-link-1.com"));
        assertTrue(affiliateLinks.contains("https://final-link-2.com"));

        verify(iopClient, times(3)).execute(any(), eq(ACCESS_TOKEN));
        verify(notificationService, atLeastOnce()).sendWarningMessage(contains("Call Limit Exceeded"));
    }
}
package bot.promotion.client;

import bot.promotion.entity.Token;
import bot.promotion.repository.TokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ActiveProfiles("dev")
@SpringBootTest
class AffiliateLinkTest {
    @MockitoBean
    private TokenRepository tokenRepository;

    @Autowired
    private AffiliateLink affiliateLink;

    private static final String ACCESS_TOKEN = "50000100f37D6Enoaa0ktEoud0tjBFet8tRzdFlSIVIrzjDx8148169dfQT1OGTsG1f2";

    @Test
    @DisplayName("Should generate affiliate link successfully")
    void generateAffiliateLink() {
        Token token = new Token();
        token.setId("aliexpress_token");
        token.setAccessToken(ACCESS_TOKEN);
        when(tokenRepository.findById("aliexpress_token")).thenReturn(Optional.of(token));

        String urlApp = "https://m.aliexpress.com/p/coin-index/index.html?productIds=1005007693930368";
        String urlPc = "https://pt.aliexpress.com/item/1005001234567890.html";

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
}
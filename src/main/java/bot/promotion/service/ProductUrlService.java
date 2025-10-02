package bot.promotion.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ProductUrlService {
    private static final Pattern PRODUCT_URL_PATTERN = Pattern.compile("/(?:item/)?(\\d+)\\.html");

        private final AffiliateService affiliateService;

    @Autowired
    public ProductUrlService(AffiliateService affiliateService) {
        this.affiliateService = affiliateService;
    }
    public String extractProductId(String url) {
        if (url == null) return null;

        Matcher matcher = PRODUCT_URL_PATTERN.matcher(url);
        if (!matcher.find()) {
            System.out.println("Unable to extract product ID from URL " + url);
            return null;
        }

        return coinUrl(matcher.group(1));
    }

    public String coinUrl(String productId) {
        if (productId == null) throw new IllegalArgumentException("productId is null");

        return affiliateService.generateAffiliateLink("https://m.aliexpress.com/p/coin-index/index.html?productIds=" + productId);
    }
}

package bot.promotion.service;

import bot.promotion.client.AffiliateLink;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ProductUrlService {
    private static final Pattern PRODUCT_URL_PATTERN = Pattern.compile("/(?:item/)?(\\d+)\\.html");
    private static final Pattern PRODUCT_ID_PATTERN_TWO = Pattern.compile("[?&]productIds=(\\d+)");
    private final AffiliateLink affiliateLink;
    private final NotificationService notify;
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS) // Segue redirecionamentos
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Autowired
    public ProductUrlService(AffiliateLink affiliateLink, NotificationService notify) {
        this.affiliateLink = affiliateLink;
        this.notify = notify;
    }

    public String processUrlAndExtractId(String shortUrl) {
        String finalUrl = findFinalUrl(shortUrl);
        return extractProductId(finalUrl, shortUrl);
    }

    private String findFinalUrl(String shortUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(shortUrl))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

            return response.uri().toString();
        } catch (Exception e) {
            notify.sendErrorMessage("Error resolving final URL: ", e);
            return null;
        }
    }

    private String extractProductId(String finalUrl, String shortUrl) {
        if (finalUrl == null) {
            notify.sendWarningMessage("Product ID not found in URL: " + shortUrl);
            return null;
        }

        Matcher matcherOne = PRODUCT_URL_PATTERN.matcher(finalUrl);
        if (matcherOne.find()) {
            return matcherOne.group(1);
        }

        Matcher matcherTwo = PRODUCT_ID_PATTERN_TWO.matcher(finalUrl);
        if (matcherTwo.find()) {
            return matcherTwo.group(1);
        }

        notify.sendWarningMessage("Product ID not found in URL: " + shortUrl);
        return null;
    }

    public List<String> createCoinUrl(String productId) {
        return affiliateLink.generateAffiliateLink("https://m.aliexpress.com/p/coin-index/index.html?productIds=" + productId,
                "https://pt.aliexpress.com/item/" + productId + ".html");
    }
}

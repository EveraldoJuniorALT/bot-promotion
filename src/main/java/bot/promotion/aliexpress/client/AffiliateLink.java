package bot.promotion.aliexpress.client;

import bot.promotion.aliexpress.dto.AffiliateLinkResponse;
import bot.promotion.product.entity.Token;
import bot.promotion.product.repository.TokenRepository;
import bot.promotion.telegram.service.NotificationService;
import com.aliexpress.open.request.AliexpressAffiliateLinkGenerateRequest;
import com.aliexpress.open.response.AliexpressAffiliateLinkGenerateResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.global.iop.api.IopClient;
import com.global.iop.util.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AffiliateLink {
    @Value("${aliexpress.app.tracking-id}")
    private String tracking;

    private final IopClient iopClient;
    private final TokenRepository tokenRepository;
    private final ObjectMapper objectMapper;
    private final NotificationService notify;
    private final Clock clock;
    private String cachedAccessToken;
    private LocalDateTime tokenFetchTime;
    private static final long CACHE_DURATION_MINUTES = 18;
    private static final int MAX_ATTEMPTS = 15;

    public List<String> generateAffiliateLink(String productUrlApp, String productUrlPc) {
        String accessToken = getValidAccessToken();
        if (accessToken == null) {
            notify.sendWarningMessage("Access token is null in line 46");
            return null;
        }
        String allUrls = productUrlApp + "," + productUrlPc;
        AliexpressAffiliateLinkGenerateRequest request = getAffiliateLinkRequest(allUrls);
        int attempts = 0;
        while (attempts < MAX_ATTEMPTS) {
            attempts++;

            AliexpressAffiliateLinkGenerateResponse linkResponse = executeRequest(request, accessToken);
            if (isCallLimiteError(linkResponse)) {
                notify.sendWarningMessage("Call Limit Exceeded, retrying");
                safeSleep(2000); // Sleep for 2 seconds before retrying
                continue;
            }

            if (responseIsValid(linkResponse)) {
                return parseResponse(linkResponse);
            }
            break;
        }

        notify.sendWarningMessage("Failed to generate affiliate link after retrying.");
        return null;
    }

    private String getValidAccessToken() {
        LocalDateTime now = LocalDateTime.now(clock);
        if (cachedAccessToken == null || tokenFetchTime == null || tokenFetchTime.isBefore(now.minusMinutes(CACHE_DURATION_MINUTES))) {
            try {
                Optional<Token> tokenDB = tokenRepository.findById("aliexpress_token");
                if (tokenDB.isEmpty()) return null;

                if (tokenDB.get().getAccessToken() == null) return null;

                this.cachedAccessToken = tokenDB.get().getAccessToken();
                this.tokenFetchTime = now;
            } catch (Exception e) {
                notify.sendErrorMessage("Error fetching access token: ", e);
                return this.cachedAccessToken;
            }
        }
        return this.cachedAccessToken;
    }

    private void safeSleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            notify.sendErrorMessage("Thread was interrupted during sleep: ", e);
        }
    }

    private boolean responseIsValid(AliexpressAffiliateLinkGenerateResponse responseApi) {
        return responseApi != null && responseApi.isSuccess();
    }

    private List<String> parseResponse(AliexpressAffiliateLinkGenerateResponse responseApi) {
        try {
            String jsonBody = responseApi.getGopResponseBody();
            AffiliateLinkResponse customResponse = objectMapper.readValue(jsonBody, AffiliateLinkResponse.class);
            return customResponse.getRespResult().getResult().getPromotionLinks().stream()
                    .sorted(Comparator.comparingInt(this::getLinkPriority))
                    .map(AffiliateLinkResponse.PromotionLinkItem::getPromotionLink)
                    .toList();
        } catch (Exception e) {
            notify.sendErrorMessage("Error parsing JSON response in line 113: ", e);
            return null;
        }
    }

    private boolean isCallLimiteError(AliexpressAffiliateLinkGenerateResponse responseApi) {
        if (responseApi == null) return false;

        if ("ApiCallLimit".equalsIgnoreCase(responseApi.getGopErrorCode())) return true;

        return responseApi.getGopResponseBody() != null &&
                (responseApi.getGopResponseBody().toLowerCase().contains("apicalllimit") ||
                        responseApi.getGopResponseBody().toLowerCase().contains("api access frequency exceeds"));
    }

    private int getLinkPriority(AffiliateLinkResponse.PromotionLinkItem linkItem) {
        String sourceValue = linkItem.getSourceValue();
        if (sourceValue != null && isPreferredLink(sourceValue)) return 0;
        return 1;
    }

    private boolean isPreferredLink(String sourceValue) {
        return sourceValue.contains("https://m.aliexpress.com/p/coin-index/index.html?productIds=");
    }

    private AliexpressAffiliateLinkGenerateResponse executeRequest(AliexpressAffiliateLinkGenerateRequest request, String accessToken) {
        try {
            return iopClient.execute(request, accessToken);
        } catch (ApiException e) {
            notify.sendErrorMessage("Error executing API request in line 142: ", e);
            return null;
        }
    }

    private AliexpressAffiliateLinkGenerateRequest getAffiliateLinkRequest(String allUrls) {
        AliexpressAffiliateLinkGenerateRequest request = new AliexpressAffiliateLinkGenerateRequest();
        request.setPromotionLinkType(0L);
        request.setSourceValues(allUrls);
        request.setTrackingId(tracking);
        return request;
    }
}
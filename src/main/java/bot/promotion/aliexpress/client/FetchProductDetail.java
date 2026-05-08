package bot.promotion.aliexpress.client;

import bot.promotion.aliexpress.dto.HotProductResponse;
import bot.promotion.product.entity.Token;
import bot.promotion.product.repository.TokenRepository;
import bot.promotion.telegram.service.NotificationService;
import com.aliexpress.open.request.AliexpressAffiliateProductdetailGetRequest;
import com.aliexpress.open.response.AliexpressAffiliateProductdetailGetResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.global.iop.api.IopClient;
import com.global.iop.util.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class FetchProductDetail {
    @Value("${aliexpress.app.tracking-id}")
    private String trackingId;

    private final IopClient iopClient;
    private final TokenRepository tokenRepository;
    private final ObjectMapper objectMapper;
    private final NotificationService notify;
    private String cachedAccessToken;
    private LocalDateTime tokenFetchTime;
    private static final long CACHE_DURATION_MINUTES = 18;
    private static final int MAX_ATTEMPTS = 15;

    @Autowired
    public FetchProductDetail(IopClient iopClient, TokenRepository tokenRepository, ObjectMapper objectMapper, NotificationService notify) {
        this.iopClient = iopClient;
        this.tokenRepository = tokenRepository;
        this.objectMapper = objectMapper;
        this.notify = notify;
    }

    public HotProductResponse productDetail(String productId) {
        String accessToken = getValidAccessToken();
        if (accessToken == null) {
            notify.sendWarningMessage("Access token is null in line 44");
            return null;
        }
        AliexpressAffiliateProductdetailGetRequest request = getProductDetailRequest(productId);
        int attempts = 0;
        while (attempts < MAX_ATTEMPTS) {
            attempts++;
            AliexpressAffiliateProductdetailGetResponse responseApi = executeRequest(request, accessToken);
            if (isCallLimitError(responseApi)) {
                notify.sendWarningMessage("Call Limit Exceeded, retrying");
                safeSleep(2000); // Sleep for 2 seconds before retrying
                continue;
            }

            if (responseIsValid(responseApi)) {
                return parseResponse(responseApi);
            }
            break;
        }
        notify.sendWarningMessage("Failed to fetch product details after retrying.");
        return null;
    }

    private synchronized String getValidAccessToken() {
        LocalDateTime now = LocalDateTime.now();
        if (cachedAccessToken == null || tokenFetchTime == null || tokenFetchTime.isBefore((now.minusMinutes(CACHE_DURATION_MINUTES)))) {
            try {
                Optional<Token> tokenDB = tokenRepository.findById("aliexpress_token");
                if (tokenDB.isEmpty()) return null;

                if (tokenDB.get().getAccessToken() == null) return null;

                this.cachedAccessToken = tokenDB.get().getAccessToken();
                this.tokenFetchTime = now;
            } catch (Exception e) {
                notify.sendErrorMessage("Error fetching token from database: ", e);
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

    private boolean responseIsValid(AliexpressAffiliateProductdetailGetResponse response) {
        return response != null && response.isSuccess();
    }

    private HotProductResponse parseResponse(AliexpressAffiliateProductdetailGetResponse response) {
        try {
            String jsonBody = response.getGopResponseBody();
            return objectMapper.readValue(jsonBody, HotProductResponse.class);
        } catch (Exception e) {
            notify.sendErrorMessage("Error parsing JSON response: ", e);
            return null;
        }
    }

    private boolean isCallLimitError(AliexpressAffiliateProductdetailGetResponse responseApi) {
        if (responseApi == null) return false;

        if ("ApiCallLimit".equalsIgnoreCase(responseApi.getGopErrorCode())) return true;

        return responseApi.getGopResponseBody() != null &&
                (responseApi.getGopResponseBody().toLowerCase().contains("apicalllimit") ||
                        responseApi.getGopResponseBody().toLowerCase().contains("api access frequency exceeds"));
    }

    private AliexpressAffiliateProductdetailGetResponse executeRequest(AliexpressAffiliateProductdetailGetRequest request, String accessToken) {
        try {
            return iopClient.execute(request, accessToken);
        } catch (ApiException e) {
            notify.sendErrorMessage("Error executing API request in line 124: ", e);
            return null;
        }
    }

    private AliexpressAffiliateProductdetailGetRequest getProductDetailRequest(String productId) {
        AliexpressAffiliateProductdetailGetRequest request = new AliexpressAffiliateProductdetailGetRequest();
        request.setProductIds(productId);
        request.setTargetCurrency("BRL");
        request.setTrackingId(trackingId);
        request.setCountry("BR");
        return request;
    }
}

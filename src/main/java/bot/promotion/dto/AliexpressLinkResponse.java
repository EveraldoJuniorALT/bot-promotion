package bot.promotion.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AliexpressLinkResponse {

    @JsonProperty("resp_result")
    private MainResponse respResult;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MainResponse {
        @JsonProperty("result")
        private Result result;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        @JsonProperty("promotion_links")
        private List<PromotionLinkItem> promotionLinks;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PromotionLinkItem {
        @JsonProperty("promotion_link")
        private String promotionLink;
    }
}

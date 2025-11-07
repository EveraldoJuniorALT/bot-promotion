package bot.promotion.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HotProduct {
    @JsonProperty("target_original_price_currency")
    private String originalCurrency;

    @JsonProperty("sku_id")
    private String skuId;

    @JsonProperty("evaluate_rate")
    private String evaluateRate;

    @JsonProperty("product_id")
    private String productId;

    @JsonProperty("sku_id")
    private String skuID;

    @JsonProperty("product_main_image_url")
    private String imageUrl;

    @JsonProperty("product_title")
    private String productTitle;

    @JsonProperty("target_app_sale_price")
    private String salePriceApp;

    @JsonProperty("promo_code_info")
    private PromotionCode promotionCode;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PromotionCode {
        @JsonProperty("promo_code")
        private String codePromotion;

        @JsonProperty("code_value")
        private String codeValue;
    }
}

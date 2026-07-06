package bot.promotion.product.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HotProduct {
    @JsonProperty("original_price_currency")
    private String originalCurrency;

    @JsonProperty("sku_id")
    private String skuId;

    @JsonProperty("evaluate_rate")
    private String evaluateRate;

    @JsonProperty("product_id")
    private String productId;

    @JsonProperty("product_main_image_url")
    private String imageUrl;

    @JsonProperty("product_detail_url")
    private String productLinkPc;

    @JsonProperty("product_title")
    private String productTitle;

    private String salePriceApp;

    @JsonProperty("target_app_sale_price")
    private void setAppSalePrice(String appSalePrice) {
        this.salePriceApp = appSalePrice;
    }

    @JsonProperty("target_sale_price")
    private void setSalePrice(String salePrice) {
        if (this.salePriceApp == null) {
            this.salePriceApp = salePrice;
        }
    }
    // Save affiliate link for later use
    private String affiliateLink;

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

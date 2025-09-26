package bot.promotion.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HotProduct {
    @JsonProperty("product_id")
    private String productId;

    @JsonProperty("product_title")
    private String productTitle;

    @JsonProperty("target_original_price")
    private String originalPrice;

    @JsonProperty("target_sale_price")
    private String salePrice;

    @JsonProperty("product_main_image_url")
    private String imageUrl;
}

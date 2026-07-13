package bot.promotion.product.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SkuProduct {
    private String shippingFees;

    @JsonProperty("color")
    private String modelo;

    @JsonProperty("sale_price_with_tax")
    private String salePrice;

    @JsonProperty("sku_id")
    private String skuId;

    @JsonProperty("sku_image_link")
    private String skuImage;

    @JsonProperty("sku_properties")
    private String skuProperties;

    @JsonProperty("ship_from_country")
    private String shipFromCountry;
}

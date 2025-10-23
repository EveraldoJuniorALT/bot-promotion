package bot.promotion.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SkuProduct {
    @JsonProperty("shipping_fees")
    private String shippingFees;

    @JsonProperty("sale_price_with_tax")
    private String salePrice;

    @JsonProperty("sku_properties")
    private String infoModel;

    @JsonProperty("ship_from_country")
    private String shipFromCountry;
}

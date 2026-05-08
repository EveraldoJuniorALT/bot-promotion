package bot.promotion.product.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShippingInfo {
    @JsonProperty("shipping_fee")
    private String shippingFee;

    @JsonProperty("ship_from_country")
    private String shipFromCountry;
}

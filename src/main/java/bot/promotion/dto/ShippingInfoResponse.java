package bot.promotion.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShippingInfoResponse {
    @JsonProperty("resp_result")
    private MainResponse respResult;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MainResponse {
        @JsonProperty("result")
        private ShippingInfo shippingInfo;
    }

}

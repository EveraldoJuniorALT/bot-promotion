package bot.promotion.aliexpress.dto;

import bot.promotion.product.dto.SkuProduct;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SkuProductResponse {
    @JsonProperty("result")
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
        @JsonProperty("ae_item_sku_info")
        private List<SkuProduct> skuProductsList;
    }
}

package bot.promotion.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HotProductResponse {
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
        @JsonProperty("products")
        private List<HotProduct> productsList;
    }
}

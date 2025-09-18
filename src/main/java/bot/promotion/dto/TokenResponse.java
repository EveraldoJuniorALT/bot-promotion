package bot.promotion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TokenResponse {
    @JsonProperty("access_token")
    private String AccessToken;

    @JsonProperty("refresh_token")
    private String RefreshToken;

    @JsonProperty("expires_in")
    private String ExpiresIn;
}

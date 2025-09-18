package bot.promotion.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Token {

    @Id
    private String id = "aliexpress_token";

    private String accessToken;
    private String refreshToken;
    private String expiresIn;
}

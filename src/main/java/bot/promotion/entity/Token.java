package bot.promotion.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Token {
    @Id
    private String id = "aliexpress_token";

    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
}

package bot.promotion.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
public class PostedDeal {
    @Id
    private String productId;
    private double minimumPrice;
    private Double lastPostedPrice;
    private LocalDateTime lastPostedAt;
}

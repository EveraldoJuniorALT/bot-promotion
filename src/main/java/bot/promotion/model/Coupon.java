package bot.promotion.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
public class Coupon {
    @Id
    private String couponCode;
    private Double discount;
    private Double minimumSpend;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}

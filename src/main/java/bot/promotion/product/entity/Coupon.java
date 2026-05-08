package bot.promotion.product.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Coupon {
    @Id
    private String couponCode;

    private Double discount;
    private Double minimumSpend;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}

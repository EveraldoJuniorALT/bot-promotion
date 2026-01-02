package bot.promotion.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "price_history")
public class PriceHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private BigDecimal price;
    private LocalDateTime capturedDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id_fk")
    private ProductVariant variant;
}

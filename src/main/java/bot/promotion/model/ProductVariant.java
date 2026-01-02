package bot.promotion.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "product_variants")
public class ProductVariant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String skuId;

    private String skuProperties;
    // Average price of 30 days
    private BigDecimal averagePrice;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id_fk")
    private Product product;

    @OneToMany(mappedBy = "variant", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PriceHistory> priceHistories = new ArrayList<>();

    public void addPriceHistory(PriceHistory price) {
        priceHistories.add(price);
        price.setVariant(this);
    }
}

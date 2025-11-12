package bot.promotion.config;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class BrandAndModel {
    private String brands;
    private final List<String> modelsAccepted;
    private final List<String> modelsExcluded;
}

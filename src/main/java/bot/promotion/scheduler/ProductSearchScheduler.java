package bot.promotion.scheduler;

import bot.promotion.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProductSearchScheduler {
    private final ProductService productService;

    @Autowired
    public ProductSearchScheduler(ProductService productService) {
        this.productService = productService;
    }

    @Scheduled(fixedRateString = "PT1H", initialDelayString = "PT1M")
    public void scheduleProductSearch() {
        productService.fetchHotProducts();
    }
}

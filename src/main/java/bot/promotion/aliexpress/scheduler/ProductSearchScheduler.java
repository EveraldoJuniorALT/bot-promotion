package bot.promotion.aliexpress.scheduler;

import bot.promotion.product.service.ProductService;
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

    @Scheduled(fixedRateString = "PT20M", initialDelayString = "PT40S")
    public void scheduleProductSearch() {
        productService.fetchHotProducts();
    }

}

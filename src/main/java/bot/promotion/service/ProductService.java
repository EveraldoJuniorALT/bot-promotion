package bot.promotion.service;

import bot.promotion.client.AliexpressApiClient;
import bot.promotion.dto.HotProduct;
import bot.promotion.dto.HotProductResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ProductService {
    AliexpressApiClient apiClient;
    TelegramService telegramService;
    TelegramMessageFormatter formatter;
    ProductUrlService urlService;

    @Autowired
    public ProductService(AliexpressApiClient apiClient, TelegramService telegramService, TelegramMessageFormatter formatter, ProductUrlService urlService) {
        this.apiClient = apiClient;
        this.telegramService = telegramService;
        this.formatter = formatter;
        this.urlService = urlService;
    }

    public void fetchHotProducts() {
        List<HotProduct> allProducts = new ArrayList<>();
        int currentPage = 1;
        HotProductResponse responseApi = apiClient.getHotProduct(currentPage);

        if (responseApi.getRespResult().getResult().getProductsList() == null) {
            System.out.println("No products found on the first page.");
            return;
        }

        allProducts.addAll(responseApi.getRespResult().getResult().getProductsList());
        int totalPages = responseApi.getRespResult().getResult().getTotalPages();

        for (currentPage = 2; currentPage <= totalPages; currentPage++) {
            responseApi = apiClient.getHotProduct(currentPage);
            if (responseApi.getRespResult().getResult().getProductsList() == null) {
                System.out.println("No products found on page in line 39" + currentPage);
                return;
            }
            allProducts.addAll(responseApi.getRespResult().getResult().getProductsList());
            try {
                // Simulate processing time
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Thread was interrupted during paging");
            }
        }

        for (HotProduct product : allProducts) {
            try {
                telegramService.sendPhotoMessage(product.getImageUrl(),
                        formatter.formatMessage(product,
                                urlService.coinUrl(product.getProductId())));
                // Simulate processing time
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Thread was interrupted during product processing");
            }
        }

    }
}

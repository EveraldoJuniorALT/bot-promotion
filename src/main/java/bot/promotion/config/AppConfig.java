package bot.promotion.config;

import com.global.iop.api.IopClient;
import com.global.iop.api.IopClientImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public IopClient iopClient(
            @Value("${aliexpress.api.token-base-url-sdk}") String baseUrl,
            @Value("${aliexpress.app.key}") String appKey,
            @Value("${aliexpress.app.secret}") String appSecret) {

        return new IopClientImpl(baseUrl, appKey, appSecret);
    }
}

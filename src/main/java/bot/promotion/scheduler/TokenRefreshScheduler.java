package bot.promotion.scheduler;

import bot.promotion.service.AliexpressAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TokenRefreshScheduler {

    private final AliexpressAuthService authService;
    @Autowired
    public TokenRefreshScheduler(AliexpressAuthService authService) {
        this.authService = authService;
    }

    @Scheduled(fixedRateString = "PT20H", initialDelayString = "PT1H")
    public void scheduleTokenRefresh() {
        authService.refreshToken();
    }
}

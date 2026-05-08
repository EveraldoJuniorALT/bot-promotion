package bot.promotion.telegram.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NotificationService {
    private final TelegramSenderService telegramSenderService;

    @Autowired
    public NotificationService(@Lazy TelegramSenderService telegramSenderService) {
        this.telegramSenderService = telegramSenderService;
    }

    @Async
    public void sendWarningMessage(String contextMessage) {
        log.warn(contextMessage);
        telegramSenderService.sendTextLogMessage("⚠️ AVISO: " + contextMessage);
    }

    @Async
    public void sendInfoMessage(String contextMessage) {
        log.info(contextMessage);
        telegramSenderService.sendTextLogMessage("✅ INFO: " + contextMessage);
    }

    @Async
    public void sendErrorMessage(String contextMessage, Exception e) {
        log.error("CRITICAL ERROR [{}]: {}", contextMessage, e.getMessage(), e);
        String formatedMessage = String.format("🚨 ERRO CRITICO\n\nContexto: %s\nErro: %s",
                contextMessage, e.getMessage());
        telegramSenderService.sendTextLogMessage(formatedMessage);
    }
}

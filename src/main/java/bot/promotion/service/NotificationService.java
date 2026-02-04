package bot.promotion.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NotificationService {
    private final TelegramReceiveAndPost telegramReceiveAndPost;

    @Autowired
    public NotificationService(@Lazy TelegramReceiveAndPost telegramReceiveAndPost) {
        this.telegramReceiveAndPost = telegramReceiveAndPost;
    }

    @Async
    public void sendWarningMessage(String contextMessage) {
        log.warn(contextMessage);
        telegramReceiveAndPost.sendTextLogMessage("⚠️ AVISO: " + contextMessage);
    }

    public void sendInfoMessage(String contextMessage) {
        log.info(contextMessage);
        telegramReceiveAndPost.sendTextLogMessage("✅ INFO: " + contextMessage);
    }

    @Async
    public void sendErrorMessage(String contextMessage, Exception e) {
        log.error("CRITICAL ERROR [{}]: {}", contextMessage, e.getMessage(), e);
        String formatedMessage = String.format("🚨 ERRO CRITICO\n\nContexto: %s\n*Erro: %s",
                contextMessage, e.getMessage());
        telegramReceiveAndPost.sendTextLogMessage(formatedMessage);
    }
}

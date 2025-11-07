package bot.promotion.config;

import bot.promotion.service.TelegramService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Component
public class BotInitializer {
    private final TelegramService telegramService;

    @Autowired
    public BotInitializer(TelegramService telegramService) {
        this.telegramService = telegramService;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void registerTelegramBot() {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(telegramService);
        } catch (TelegramApiException e) {
            System.err.println(">>> [ERRO] Falha ao registrar o bot: " + e.getMessage());
        }
    }
}

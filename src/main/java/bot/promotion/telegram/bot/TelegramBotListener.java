package bot.promotion.telegram.bot;

import bot.promotion.telegram.dispatcher.CommandDispatcher;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class TelegramBotListener extends TelegramLongPollingBot {
    @Getter
    @Value("${telegram.bot.name}")
    private String botUserName;
    private final long startTime;
    private final CommandDispatcher commandDispatcher;

    @Autowired
    public TelegramBotListener(@Value("${telegram.bot.token}") String botToken, CommandDispatcher commandDispatcher) {
        super(botToken);
        this.commandDispatcher = commandDispatcher;
        this.startTime = System.currentTimeMillis() / 1000L;
    }

    @Override
    public String getBotUsername() {
        return botUserName;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            if (update.getMessage().getDate() < startTime) return;
            commandDispatcher.dispatch(update);
        }
    }
}

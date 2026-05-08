package bot.promotion.telegram.dispatcher;

import bot.promotion.product.service.ProductUrlService;
import bot.promotion.telegram.service.ProductTelegramService;
import bot.promotion.telegram.service.TelegramSenderService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CommandDispatcher {
    private final ProductUrlService urlService;
    private final ProductTelegramService telegramService;
    private final TelegramSenderService senderService;
    private static final Pattern URL_PATTERN = Pattern.compile("\\b((https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|])");

    public CommandDispatcher(ProductUrlService urlService, @Lazy ProductTelegramService telegramService, @Lazy TelegramSenderService senderService) {
        this.urlService = urlService;
        this.telegramService = telegramService;
        this.senderService = senderService;
    }

    public void dispatch(Update update) {
        String productUrl = findUrlInText(update.getMessage().getText());
        if (productUrl == null) return;

        if (!productUrl.contains("aliexpress.com")) return;

        processMessageReceived(update, productUrl);
    }

    private void processMessageReceived(Update update, String productUrl) {
        String productId = urlService.processUrlAndExtractId(productUrl);
        if (productId == null) return;

        String textMessage = update.getMessage().getText().toLowerCase().split("\\s+")[0];
        String chatId = update.getMessage().getChatId().toString();
        Integer messageId = update.getMessage().getMessageId();

        switch (textMessage) {
            case "/save":
                senderService.deleteUserMessage(messageId, chatId);
                processSaveCommand(productId);
                break;

            case "/post":
                senderService.deleteUserMessage(messageId, chatId);
                processProductUrl(productId);
                break;

            default:
                processDefaultCommand(productId, update.getMessage().getFrom(), chatId, messageId);
                senderService.deleteUserMessage(messageId, chatId);
                break;
        }
        /*
         * The calls to the method above are intentionally duplicated
         * For the 'sendTextMessage' method to function correctly,
         * the message must be deleted after its execution.
         */
    }

    private void processSaveCommand(String productId) {
        telegramService.processSaveProductUrl(productId);
    }

    private void processProductUrl(String productId) {
        telegramService.sendProductInfo(productId);
    }

    private void processDefaultCommand(String productId, User userShared, String chatId, Integer messageId) {
        telegramService.processDefaultProductUrl(productId, userShared, chatId, messageId);
    }

    private String findUrlInText(String text) {
        Matcher matcher = URL_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}

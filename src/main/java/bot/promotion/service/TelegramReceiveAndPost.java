package bot.promotion.service;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TelegramReceiveAndPost extends TelegramLongPollingBot {
    @Getter
    @Value("${telegram.bot.token}")
    private String botToken;
    @Value("${telegram.bot.chat-id}")
    private String chatId;

    private final ProductUrlService productUrlService;
    private final ProductTelegramService productTelegramService;
    private static final Pattern URL_PATTERN = Pattern.compile(
            "\\b((https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|])");

    @Autowired
    public TelegramReceiveAndPost(ProductUrlService productUrlService, ProductTelegramService productTelegramService) {
        this.productUrlService = productUrlService;
        this.productTelegramService = productTelegramService;
    }

    @Override
    public String getBotUsername() {
        return "TesteBotPromotion";
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();

            String[] parts = messageText.split("\\s+", 2);
            String command = parts[0];
            if (command.startsWith("/save")) {
                System.out.println("Command /save received: " + parts[1]);
                return;
            }

            String url = findUrlInText(messageText);
            if (url != null && (url.contains("s.click.aliexpress.com") || url.contains("a.aliexpress.com"))) {
                String productId = productUrlService.processUrlAndExtractId(url);
                productTelegramService.sendProductInfo(productId);
            }

            if (url != null && (url.contains("pt.aliexpress.com"))) {
                String productId = productUrlService.extractProductId(url);
                productTelegramService.sendProductInfo(productId);
            }
        }
    }

    private void deleteUserMessage(Integer messageId) {
        // Implement message deletion logic if needed
    }

    private String findUrlInText(String text) {
        Matcher matcher = URL_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public void sendPhotoMessage(String photoUrl, String caption) {
        SendPhoto sendPhoto = new SendPhoto();
        sendPhoto.setParseMode(ParseMode.HTML);
        sendPhoto.setChatId(chatId);
        sendPhoto.setPhoto(new InputFile(photoUrl));
        sendPhoto.setCaption(caption);
        try {
            execute(sendPhoto);
        } catch (TelegramApiException e) {
            System.out.println("Error sending photo message: " + e.getMessage());
        }
    }
}

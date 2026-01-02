package bot.promotion.service;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TelegramReceiveAndPost extends TelegramLongPollingBot {
    @Getter
    @Value("${telegram.bot.name}")
    private String botUserName;

    @Value("${telegram.bot.chat-id}")
    private String chatId;

    private final ProductUrlService productUrlService;
    private final ProductTelegramService productTelegramService;
    private static final Pattern URL_PATTERN = Pattern.compile("\\b((https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|])");

    @Autowired
    public TelegramReceiveAndPost(@Value("${telegram.bot.token}") String botToken,
                                  ProductUrlService productUrlService,
                                  ProductTelegramService productTelegramService) {
        super(botToken);
        this.productUrlService = productUrlService;
        this.productTelegramService = productTelegramService;
    }

    @Override
    public String getBotUsername() {
        return botUserName;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            Integer messageId = update.getMessage().getMessageId();

            try {
                deleteUserMessage(messageId);
                processMessageReceived(messageText);
            } catch (Exception e) {
                System.out.println("Error processing received message: " + e.getMessage());
            }
        }
    }

    private void processMessageReceived(String messageText) {
        String productUrl = findUrlInText(messageText);
        if (productUrl == null) {
            return;
        }

        if (messageText.startsWith("/save")) {
            processSaveCommand(messageText);
            return;
        }

        processProductUrl(productUrl);
    }

    private void processProductUrl(String url) {
        String productId = productUrlService.processUrlAndExtractId(url);
        if (productId == null) return;
        productTelegramService.sendProductInfo(productId);
    }

    private void processSaveCommand(String command) {
        String[] parts = command.split("\\s+", 2);
        if (parts.length < 2) return;
        productTelegramService.processProductUrl(parts[1]);
    }

    private void deleteUserMessage(Integer messageId) {
        DeleteMessage deleteMessage = new DeleteMessage();
        deleteMessage.setChatId(chatId);
        deleteMessage.setMessageId(messageId);
        try {
            execute(deleteMessage);
        } catch (TelegramApiException e) {
            System.out.println("Error deleting user message in line 75: " + e.getMessage());
        }
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

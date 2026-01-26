package bot.promotion.service;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
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
    private final long startTime;

    @Autowired
    public TelegramReceiveAndPost(@Value("${telegram.bot.token}") String botToken,
                                  ProductUrlService productUrlService,
                                  ProductTelegramService productTelegramService) {
        super(botToken);
        this.productUrlService = productUrlService;
        this.productTelegramService = productTelegramService;
        this.startTime = System.currentTimeMillis() / 1000L;
    }

    @Override
    public String getBotUsername() {
        return botUserName;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            if(update.getMessage().getDate() < startTime) return;

            String productUrl = findUrlInText(update.getMessage().getText());
            if (productUrl == null) return;

            if (!productUrl.contains("aliexpress.com")) return;

            try {
                processMessageReceived(update, productUrl);
            } catch (Exception e) {
                System.out.println("Error processing received message: " + e.getMessage());
            }
        }
    }

    private void processMessageReceived(Update update, String productUrl) {
        String productId = productUrlService.processUrlAndExtractId(productUrl);
        if (productId == null) return;

        deleteUserMessage(update.getMessage().getMessageId(), update.getMessage().getChatId());
        if (update.getMessage().getText().startsWith("/save")) {
            processSaveCommand(productId);
            return;
        }

        if (update.getMessage().getText().startsWith("/post")) {
            processProductUrl(productId);
            return;
        }

        sendTextMessage(createAMessageText(productId));
    }

    private void processSaveCommand(String productId) {
        productTelegramService.processSaveProductUrl(productId);
    }

    private void processProductUrl(String productId) {
        productTelegramService.sendProductInfo(productId);
    }

    private String findUrlInText(String text) {
        Matcher matcher = URL_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private void deleteUserMessage(Integer messageId, Long chatIdFromMessage) {
        DeleteMessage deleteMessage = new DeleteMessage();
        deleteMessage.setChatId(chatIdFromMessage.toString());
        deleteMessage.setMessageId(messageId);
        try {
            execute(deleteMessage);
        } catch (TelegramApiException e) {
            System.out.println("Error deleting user message in line 75: " + e.getMessage());
        }
    }

    private String createAMessageText(String productId) {
        StringBuilder stringBuilder = new StringBuilder();
        List<String> links = productUrlService.createCoinUrl(productId);
        stringBuilder.append("Apenas no App, com super descontos❗❗").append("\n\n");
        stringBuilder.append("🔗 ").append(links.getFirst()).append("\n\n");
        stringBuilder.append("Para pc, sem super descontos❗❗").append("\n\n");
        stringBuilder.append("🔗 ").append(links.getLast()).append("\n\n");
        stringBuilder.append("🚀 Grupo de Ofertas: ").append("https://t.me/GarimpDeOfertas").append("\n\n");
        return stringBuilder.toString();
    }

    public void sendPhotoMessage(String photoUrl, String text) {
        SendPhoto sendPhoto = new SendPhoto();
        sendPhoto.setParseMode(ParseMode.HTML);
        sendPhoto.setChatId(chatId);
        sendPhoto.setPhoto(new InputFile(photoUrl));
        sendPhoto.setCaption(text);
        try {
            execute(sendPhoto);
        } catch (TelegramApiException e) {
            System.out.println("Error sending photo message: " + e.getMessage());
        }
    }

    public void sendTextMessage(String text) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(text);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            System.out.println("Error sending text message: " + e.getMessage());
        }
    }

}

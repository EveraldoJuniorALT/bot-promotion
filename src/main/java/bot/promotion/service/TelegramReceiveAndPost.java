package bot.promotion.service;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
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

    @Value("${telegram.bot.chat-id-priority}")
    private String chatIdPriority;


    private final ProductUrlService productUrlService;
    private final ProductTelegramService productTelegramService;
    private final NotificationService notify;
    private static final Pattern URL_PATTERN = Pattern.compile("\\b((https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|])");
    private final long startTime;

    @Autowired
    public TelegramReceiveAndPost(@Value("${telegram.bot.token}") String botToken,
                                  ProductUrlService productUrlService,
                                  @Lazy ProductTelegramService productTelegramService, @Lazy NotificationService notify) {
        super(botToken);
        this.productUrlService = productUrlService;
        this.productTelegramService = productTelegramService;
        this.notify = notify;
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

            String productUrl = findUrlInText(update.getMessage().getText());
            if (productUrl == null) return;

            if (!productUrl.contains("aliexpress.com")) return;

            processMessageReceived(update, productUrl);
        }
    }

    private void processMessageReceived(Update update, String productUrl) {
        String productId = productUrlService.processUrlAndExtractId(productUrl);
        if (productId == null) return;

        String textMessage = update.getMessage().getText().toLowerCase().split("\\s+")[0];
        String chatId = update.getMessage().getChatId().toString();
        Integer messageId = update.getMessage().getMessageId();

        switch (textMessage) {
            case "/save":
                deleteUserMessage(messageId, chatId);
                processSaveCommand(productId);
                break;

            case "/post":
                deleteUserMessage(messageId, chatId);
                processProductUrl(productId);
                break;

            default:
                sendTextMessage(createMessageText(productId, update.getMessage().getFrom()), chatId, messageId);
                deleteUserMessage(messageId, chatId);
                break;
        }
        /*
         * The calls to the method above are intentionally duplicated
         * For the 'sendTextMessage' method to function correctly,
         * the message must be deleted after its execution.
         */
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

    private void deleteUserMessage(Integer messageId, String chatIdFromMessage) {
        DeleteMessage deleteMessage = new DeleteMessage();
        deleteMessage.setChatId(chatIdFromMessage);
        deleteMessage.setMessageId(messageId);
        try {
            execute(deleteMessage);
        } catch (TelegramApiException e) {
            notify.sendErrorMessage("Error deleting user message in line 75: ", e);
        }
    }

    private String createMessageText(String productId, User userShared) {
        StringBuilder stringBuilder = new StringBuilder();
        List<String> links = productUrlService.createCoinUrl(productId);
        stringBuilder.append("@").append(verifyUserName(userShared) ? userShared.getUserName() : userShared.getFirstName()).append(" compartilhou um link:\n\n");
        stringBuilder.append("Link com super descontos, apenas no APP❗❗").append("\n");
        stringBuilder.append("✅ ").append(links.getFirst()).append("\n\n");
        stringBuilder.append("Para pc, sem super descontos❗❗").append("\n");
        stringBuilder.append("🔗 ").append(links.getLast()).append("\n\n");
        stringBuilder.append("🚀 Grupo de Ofertas: ").append("https://t.me/GarimpDeOfertas").append("\n\n");
        return stringBuilder.toString();
    }

    private boolean verifyUserName(User userShared) {
        return userShared.getUserName() != null && !userShared.getUserName().isEmpty();
    }

    public void sendPhotoMessage(String photoUrl, String text, boolean isPriority) {
        SendPhoto sendPhoto = new SendPhoto();
        sendPhoto.setParseMode(ParseMode.HTML);
        sendPhoto.setChatId(isPriority ? this.chatIdPriority : this.chatId);
        sendPhoto.setPhoto(new InputFile(photoUrl));
        sendPhoto.setCaption(text);
        try {
            execute(sendPhoto);
        } catch (TelegramApiException e) {
            notify.sendErrorMessage("Error sending photo message: ", e);
        }
    }

    public void sendTextMessage(String text, String chatIdFromTelegram, Integer replyToMessageId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatIdFromTelegram);
        sendMessage.setText(text);
        sendMessage.setReplyToMessageId(replyToMessageId);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            notify.sendErrorMessage("Error sending text message: ", e);
        }
    }

    public void sendTextLogMessage(String text) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(text);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            notify.sendErrorMessage("Error sending log text message: ", e);
        }
    }
}

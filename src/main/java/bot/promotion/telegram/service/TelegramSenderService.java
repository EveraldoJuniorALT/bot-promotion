package bot.promotion.telegram.service;

import bot.promotion.telegram.bot.TelegramBotListener;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Service
@RequiredArgsConstructor
public class TelegramSenderService {
    @Value("${telegram.bot.chat-id}")
    private String chatId;
    @Value("${telegram.bot.chat-id-priority}")
    private String chatIdPriority;
    private final TelegramBotListener bot;
    private final NotificationService notify;

    @Async
    public void deleteUserMessage(Integer messageId, String chatIdFromMessage) {
        DeleteMessage deleteMessage = new DeleteMessage();
        deleteMessage.setChatId(chatIdFromMessage);
        deleteMessage.setMessageId(messageId);
        try {
            bot.execute(deleteMessage);
        } catch (TelegramApiException e) {
            notify.sendErrorMessage("Error deleting user message in line 33: ", e);
        }
    }

    @Async
    public void sendPhotoMessage(String photoUrl, String text, boolean isPriority) {
        SendPhoto sendPhoto = new SendPhoto();
        sendPhoto.setParseMode(ParseMode.HTML);
        sendPhoto.setChatId(isPriority ? this.chatIdPriority : this.chatId);
        sendPhoto.setPhoto(new InputFile(photoUrl));
        sendPhoto.setCaption(text);
        try {
            bot.execute(sendPhoto);
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
            bot.execute(sendMessage);
        } catch (TelegramApiException e) {
            notify.sendErrorMessage("Error sending text message: ", e);
        }
    }

    @Async
    public void sendTextLogMessage(String text) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(text);
        try {
            bot.execute(sendMessage);
        } catch (TelegramApiException e) {
            notify.sendErrorMessage("Error sending log text message: ", e);
        }
    }
}

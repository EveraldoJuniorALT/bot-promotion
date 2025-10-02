package bot.promotion.service;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Component
public class TelegramService extends TelegramLongPollingBot {
    @Getter
    @Value("${telegram.bot.token}")
    private String botToken;
    @Value("${telegram.bot.chat-id}")
    private String chatId;

    @Override
    public String getBotUsername() {
        return "TesteBotPromotion";
    }

    @Override
    public void onUpdateReceived(Update update) {
        // No implementation needed for sending messages only
    }

    public void sendPhotoMessage(String photoUrl, String caption) {
        SendPhoto sendPhoto = new SendPhoto();
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

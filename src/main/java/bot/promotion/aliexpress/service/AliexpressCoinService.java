package bot.promotion.aliexpress.service;

import bot.promotion.core.config.EmulatorPoolManager;
import bot.promotion.telegram.service.NotificationService;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.android.AndroidDriver;
import lombok.RequiredArgsConstructor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AliexpressCoinService {
    private final NotificationService notify;
    private static final Pattern EXTRACT_DISCOUNT_PATTERN = Pattern.compile("(\\d+)%");
    private final Executor emulatorTaskExecutor;
    private final EmulatorPoolManager poolManager;

    public CompletableFuture<BigDecimal> processLink(String link) {
        return CompletableFuture.supplyAsync(() -> {
            if (link == null || link.isBlank()) {
                notify.sendWarningMessage("Links is null or blank.");
                return null;
            }

            AndroidDriver driver = null;
            try {
                driver = poolManager.acquireDriver();
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));

                return executeProcess(driver, link, wait);
            } catch (InterruptedException e) {
                notify.sendErrorMessage("Interrupted while processing link in line 44 on AliexpressCoinService: " + link, e);
                return new BigDecimal(1);
            } finally {
                if (driver != null) poolManager.releaseDriver(driver);
            }

        }, emulatorTaskExecutor);
    }

    private BigDecimal executeProcess(AndroidDriver driver, String link, WebDriverWait wait) {
        openLink(driver, link, wait);
        clickFirstProduct(driver);

        BigDecimal coinPercentage = extractExtraDiscount(wait);
        if (coinPercentage != null) return coinPercentage;

        coinPercentage = extractExtraDiscount(wait);
        return coinPercentage != null ? coinPercentage : new BigDecimal("1");
    }

    private void openLink(AndroidDriver driver, String link, WebDriverWait wait) {
        driver.get(link);
        wait.until(ExpectedConditions.visibilityOfElementLocated(
                AppiumBy.xpath("//android.widget.TextView[contains(@text, 'R$') or contains(@text, '$')]")
        ));
    }

    private void clickFirstProduct(AndroidDriver driver) {
        try {
            Thread.sleep(6000);
            clickByCoordinates(240, 1480, driver);

        } catch (InterruptedException e) {
            notify.sendErrorMessage("Error clicking first product: ", e);
        }
    }

    private BigDecimal extractExtraDiscount(WebDriverWait wait) {
        try {
            WebElement elementExtra = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    AppiumBy.accessibilityId("taxIcon")
            ));
            String clearText = clearText(elementExtra.getText());
            return clearText != null ? new BigDecimal(clearText) : null;
        } catch (NumberFormatException e) {
            notify.sendErrorMessage("No extra discount found or invalid format for BigDecimal.", e);
            return null;
        }
    }

    private String clearText(String text) {
        Matcher matcher = EXTRACT_DISCOUNT_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private void clickByCoordinates(int x, int y, AndroidDriver driver) {
        var finger = new org.openqa.selenium.interactions.PointerInput(org.openqa.selenium.interactions.PointerInput.Kind.TOUCH, "finger");
        var tap = new org.openqa.selenium.interactions.Sequence(finger, 1);
        tap.addAction(finger.createPointerMove(Duration.ofMillis(0), org.openqa.selenium.interactions.PointerInput.Origin.viewport(), x, y));
        tap.addAction(finger.createPointerDown(org.openqa.selenium.interactions.PointerInput.MouseButton.LEFT.asArg()));
        tap.addAction(finger.createPointerUp(org.openqa.selenium.interactions.PointerInput.MouseButton.LEFT.asArg()));
        driver.perform(java.util.List.of(tap));
    }
}

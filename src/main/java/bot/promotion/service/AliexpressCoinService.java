package bot.promotion.service;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AliexpressCoinService {
    private final AppiumDriverManager driverManager;
    private static final Pattern EXTRACT_DISCOUNT_PATTERN = Pattern.compile("(\\d+)%");

    @Autowired
    public AliexpressCoinService(AppiumDriverManager driverManager) {
        this.driverManager = driverManager;
    }

    public String processLink(String link) {
        String coinPercentage = executeProcess(link);
        if (coinPercentage != null) {
            return coinPercentage;
        }
        try {
            coinPercentage = executeProcess(link);
            return coinPercentage;
        } catch (Exception e) {
            System.out.println("Error during retry: " + e.getMessage());
            return null;
        }
    }

    private String executeProcess(String link) {
        AndroidDriver driver = driverManager.getDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));

        openLink(driver, link, wait);
        clickFirstProduct(driver);

        return extractExtraDiscount(wait);
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
            System.out.println("Error clicking first product: " + e.getMessage());
        }
    }

    private String extractExtraDiscount(WebDriverWait wait) {
        try {
            WebElement elementExtra = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    AppiumBy.accessibilityId("taxIcon")
            ));
            String extraText = elementExtra.getText();
            return clearText(extraText);
        } catch (Exception e) {
            System.out.println("No extra discount found." + e.getMessage());
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

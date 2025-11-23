package bot.promotion.service;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;

@Service
public class AppiumDriverManager {
    private final UiAutomator2Options options;
    private AndroidDriver driver;

    public AppiumDriverManager(UiAutomator2Options options) {
        this.options = options;
    }

    public synchronized AndroidDriver getDriver() {
        if (driver == null) {
            createDriver();
        }
        if (driver != null) {
            try {
                driver.getCurrentPackage();
            } catch (Exception e) {
                System.out.println("Appium driver session is invalid, recreating driver: " + e.getMessage());
                quitDriver();
                createDriver();
            }
        }
        return driver;
    }

    private void createDriver() {
        try {
            driver = new AndroidDriver(new URL("http://127.0.0.1:4723/"), options);
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(15));
        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid Appium server URL: " + e.getMessage());
        }
        catch (Exception e) {
            System.out.println("Error creating Appium driver: " + e.getMessage());
            throw new RuntimeException("Failed to create Appium driver: " + e.getMessage(), e);
        }
    }

    private void quitDriver() {
        if (driver != null) {
            try {
                driver.quit();
            } catch (Exception e) {
                System.out.println("Error quitting Appium driver: " + e.getMessage());
            }
            driver = null;
        }
    }
}

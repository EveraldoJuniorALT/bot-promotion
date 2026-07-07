package bot.promotion.core.config;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AppiumDriverManager {

    @Value("${appium.server.url:http://127.0.0.1:4723/}")
    private String appiumServerUrl;

    /*
     * Cria um instância independente do AndroidDriver para o paralelismo
     * @param emulatorUdId O endereço IP e porta do emulador
     * @param appiumServerPort Porta exclusiva do UiAutomator2 para evitar conflito de threads
     * @return AndroidDriver configurado para instância específica
     */
    public AndroidDriver createDriver(String emulatorUdId, int appiumServerPort) {
        try {
            UiAutomator2Options options = getOptions(emulatorUdId, appiumServerPort);

            AndroidDriver driver = new AndroidDriver(new URL(appiumServerUrl), options);
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(15));
            return driver;
        } catch (MalformedURLException e) {
            System.out.println("Invalid Appium server URL: " + e.getMessage());
            throw new RuntimeException("Invalid Appium server URL: " + e.getMessage());
        }
    }

    private UiAutomator2Options getOptions(String emulatorUdId, int appiumServerPort) {
        UiAutomator2Options options = new UiAutomator2Options();
        options.setPlatformName("Android");
        options.setAutomationName("UiAutomator2");
        options.setDeviceName("MuMuPlayer-" + emulatorUdId);
        options.setUdid(emulatorUdId);
        options.setSystemPort(appiumServerPort);

        //Keep the session alive for 24 hours to avoid frequent restarts
        options.setCapability("appium:newCommandTimeout", 86400);

        options.setCapability("appium:enforceAppInstall", true);
        options.setCapability("appium:suppressKillServer", true);
        options.setCapability("appium:adbExecTimeout", 1200000);
        options.setCapability("appium:uiautomator2ServerLaunchTimeout", 60000);
        options.setCapability("appium:ignoreHiddenApiPolicyError", true);

        options.setCapability("appium:skipServerInstallation", true);

        options.setAppPackage("com.alibaba.aliexpresshd");
        options.setCapability("appium:appWaitActivity", "*");
        options.setCapability("appium:appWaitForLaunch", false);
        options.setNoReset(true);
        return options;
    }
}

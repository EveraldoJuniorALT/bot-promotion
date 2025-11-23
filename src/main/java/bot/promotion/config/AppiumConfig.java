package bot.promotion.config;

import io.appium.java_client.android.options.UiAutomator2Options;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppiumConfig {

    @Bean
    public UiAutomator2Options getUiAutomator2Options() {
        UiAutomator2Options options = new UiAutomator2Options();
        options.setPlatformName("Android");
        options.setAutomationName("UiAutomator2");
        options.setDeviceName("BlueStacks");
        options.setUdid("127.0.0.1:5555");

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

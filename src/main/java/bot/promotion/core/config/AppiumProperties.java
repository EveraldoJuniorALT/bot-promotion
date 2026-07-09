package bot.promotion.core.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@Data
public class AppiumProperties {

    private List<EmulatorConfig> emulators = new ArrayList<>();

    @PostConstruct
    public void init() {
        int i = 0;
        while (true) {
            String udid = System.getProperty("appium.emulator[" + i + "].udid");
            if (udid == null) break;
            EmulatorConfig  config = new EmulatorConfig();
            config.setUdid(udid);
            config.setSystemPort(Integer.parseInt(System.getProperty("appium.emulator[" + i + "].systemPort")));
            config.setServerUrl(System.getProperty("appium.emulator[" + i + "].serverUrl"));

            emulators.add(config);
            i++;
        }
    }

    @Data
    public static class EmulatorConfig {
        private String udid;
        private int systemPort;
        private String serverUrl;
    }
}

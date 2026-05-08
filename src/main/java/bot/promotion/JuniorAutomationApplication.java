package bot.promotion;

import bot.promotion.core.util.EnvironmentManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class JuniorAutomationApplication {

	public static void main(String[] args) {
        EnvironmentManager.prepareEnvironment();
		SpringApplication.run(JuniorAutomationApplication.class, args);
	}

}

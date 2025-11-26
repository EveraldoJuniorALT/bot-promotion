package bot.promotion;

import bot.promotion.util.EnvironmentManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JuniorAutomationApplication {

	public static void main(String[] args) {
        EnvironmentManager.prepareEnvironment();
		SpringApplication.run(JuniorAutomationApplication.class, args);
	}

}

package bot.promotion.core.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

@Component
@EnableAsync
@EnableScheduling
@RequiredArgsConstructor
public class AsyncConfig {
    private final AppiumProperties appiumProperties;

    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("Scheduler-Thread-");
        scheduler.initialize();
        return scheduler;
    }

    @Bean(name = "apiExecutor")
    public Executor apiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(30);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("Auto-Process-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "telegramExecutor")
    public Executor TelegramExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(15);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("Telegram-Process-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "emulatorTaskExecutor")
    public Executor emulatorTaskExecutor() {
        int activeEmulatorsCount = (appiumProperties.getEmulators() != null)
                ? appiumProperties.getEmulators().size()
                : 2; // Fallback to 2 if emulator list is null
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(activeEmulatorsCount);
        executor.setMaxPoolSize(activeEmulatorsCount * 2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("EmulatorWorker-");
        executor.initialize();
        return executor;
    }
}

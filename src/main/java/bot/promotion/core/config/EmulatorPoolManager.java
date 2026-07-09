package bot.promotion.core.config;

import bot.promotion.telegram.service.NotificationService;
import io.appium.java_client.android.AndroidDriver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

@Component
@RequiredArgsConstructor
public class EmulatorPoolManager {
    private final NotificationService notify;
    private final AppiumDriverManager driverManager;
    private final AppiumProperties appiumProperties;

    private BlockingQueue<AndroidDriver> driverPool;

    @PostConstruct
    private void initializePool() {
        List<AppiumProperties.EmulatorConfig> emulatorProperties = this.appiumProperties.getEmulators();

        if (emulatorProperties == null || emulatorProperties.isEmpty()) throw new IllegalStateException("Emulator properties are empty");

        int poolSize = emulatorProperties.size();
        this.driverPool = new ArrayBlockingQueue<>(poolSize);

        for (int i = 0; i < poolSize; i++) {
            AppiumProperties.EmulatorConfig emulatorConfig = emulatorProperties.get(i);

            AndroidDriver driver = driverManager.createDriver(
                    emulatorConfig.getUdid(),
                    emulatorConfig.getSystemPort(),
                    emulatorConfig.getServerUrl()
            );

            boolean added = driverPool.offer(driver);
            if (!added) {
                notify.sendErrorMessage("Critical failure: Attempt to add emulator to a pool! in line 43 on EmulatorPoolManager", null);
                throw new IllegalStateException("Attempt to add emulator to a pool!");
            }
        }
    }

    /**
     * Pega um emulador emprestado. Se os dois estiverem em uso,
     * a thread que chamou aguardará até um ser devolvido.
     */
    public AndroidDriver acquireDriver() throws InterruptedException {
        return driverPool.take();
    }

    /**
     * Devolve o emulador para a fila após finalizar o uso.
     */
    public void releaseDriver(AndroidDriver driver) {
        if (driver != null) {
            boolean devolvidoComSucesso = driverPool.offer(driver);

            if (!devolvidoComSucesso) {
                notify.sendWarningMessage("Attempt to return emulator, but the pool was already full! Terminating orphaned session.");
                driver.quit();
            }
        }
    }

    @PreDestroy
    private void closeAll() {
        try {
            System.out.println("Pool Closing Emulators...");
            driverPool.forEach(driver -> {
                if (driver != null) {
                    driver.quit();
                }
            });
        } catch (Exception e) {
            System.out.println("The driver session had already been closed or the emulator was shut down first.");
        }
        System.out.println("Appium sessions closed successfully.");
    }
}

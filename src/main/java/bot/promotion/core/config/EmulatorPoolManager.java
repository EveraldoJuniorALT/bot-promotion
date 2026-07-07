package bot.promotion.core.config;

import bot.promotion.telegram.service.NotificationService;
import io.appium.java_client.android.AndroidDriver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

@Component
public class EmulatorPoolManager {
    private final NotificationService notify;
    private final AppiumDriverManager driverManager;
    private final BlockingQueue<AndroidDriver> driverPool;

    @Value("${mumu.udid.instance0}")
    private String udidInstance0;

    @Value("${mumu.udid.instance1}")
    private String udidInstance1;

    public EmulatorPoolManager(NotificationService notify, AppiumDriverManager driverManager) {
        this.notify = notify;
        this.driverManager = driverManager;
        this.driverPool = new ArrayBlockingQueue<>(2);
    }

    @PostConstruct
    private void initializePool() {
        AndroidDriver driver0 = driverManager.createDriver(udidInstance0, 8200);
        AndroidDriver driver1 = driverManager.createDriver(udidInstance1, 8201);

        try {
            driverPool.add(driver0);
            driverPool.add(driver1);
            notify.sendInfoMessage("Pool initialized successfully. 2 emulators ready to use.");
        } catch (IllegalStateException e) {
            notify.sendErrorMessage("Critical failure: Attempt to add emulators to a full pool!", e);
            throw e; // Interrompe a subida do Spring, pois o ambiente está comprometido
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

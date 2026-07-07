package bot.promotion.core.config;

import bot.promotion.telegram.service.NotificationService;
import io.appium.java_client.android.AndroidDriver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

@Component
public class EmulatorPoolManager {
    private final NotificationService notify;
    private final AppiumDriverManager driverManager;
    private final BlockingQueue<AndroidDriver> driverPool;

    public EmulatorPoolManager(NotificationService notify, AppiumDriverManager driverManager) {
        this.notify = notify;
        this.driverManager = driverManager;
        this.driverPool = new ArrayBlockingQueue<>(2);
    }

    @PostConstruct
    private void initializePool() {
        AndroidDriver driver0 = driverManager.createDriver("127.0.0.1:16385", 8200);
        AndroidDriver driver1 = driverManager.createDriver("127.0.0.1:16417", 8201);

        try {
            driverPool.add(driver0);
            driverPool.add(driver1);
            notify.sendInfoMessage("Pool inicializado com sucesso. 2 emuladores prontos para uso.");
        } catch (IllegalStateException e) {
            notify.sendErrorMessage("Falha crítica: Tentativa de adicionar emuladores em um pool cheio!", e);
            throw e; // Interrompe a subida do Spring, pois o ambiente está comprometido
        }

        notify.sendInfoMessage("Pool initialized with success. 2 emulators are ready to use.");
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
                notify.sendWarningMessage("Tentativa de devolver emulador, mas o pool já estava cheio! Encerrando sessão órfã.");
                driver.quit();
            }
        }
    }

    @PreDestroy
    private void closeAll() {
        System.out.println("Pool Closing Emulators...");
        driverPool.forEach(driver -> {
            if (driver != null) {
                driver.quit();
            }
        });
    }
}

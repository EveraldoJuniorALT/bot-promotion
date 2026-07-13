package bot.promotion.core.config;

import bot.promotion.telegram.service.NotificationService;
import io.appium.java_client.android.AndroidDriver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.PriorityBlockingQueue;

@Component
@RequiredArgsConstructor
public class EmulatorPoolManager {
    private final NotificationService notify;
    private final AppiumDriverManager driverManager;
    private final AppiumProperties appiumProperties;

    private final Queue<AndroidDriver> availableDrivers = new LinkedList<>();
    private final PriorityBlockingQueue<DriverRequest> waitingRequests = new PriorityBlockingQueue<>();

    @PostConstruct
    private void initializePool() {
        List<AppiumProperties.EmulatorConfig> emulatorProperties = this.appiumProperties.getEmulators();

        if (emulatorProperties == null || emulatorProperties.isEmpty())
            throw new IllegalStateException("Emulator properties are empty");

        for (AppiumProperties.EmulatorConfig emulatorConfig : emulatorProperties) {
            AndroidDriver driver = driverManager.createDriver(
                    emulatorConfig.getUdid(),
                    emulatorConfig.getSystemPort(),
                    emulatorConfig.getServerUrl()
            );

            releaseDriver(driver);
        }
    }

    /**
     * acquires an emulator based on the reported priority
     * @param isPriority true if the request is from a priority source (Telegram), false otherwise
     */
    public AndroidDriver acquireDriver(boolean isPriority) throws InterruptedException {
        CompletableFuture<AndroidDriver> future = new CompletableFuture<>();
        DriverRequest request = new DriverRequest(isPriority, future);
        waitingRequests.offer(request);

        dispatch();

        return future.join();
    }

    /**
     * Devolve o emulador para a fila após finalizar o uso.
     */
    public void releaseDriver(AndroidDriver driver) {
        if (driver != null) {
            boolean devolvidoComSucesso = availableDrivers.offer(driver);
            dispatch();

            if (!devolvidoComSucesso) {
                notify.sendWarningMessage("Attempt to return emulator, but the pool was already full! Terminating orphaned session.");
                driver.quit();
            }
        }
    }

    /**
     * The Distributor: (synchronized).
     * It takes the free emulator and delivers it to the highest priority order.
     */
    private synchronized void dispatch() {
        while (!availableDrivers.isEmpty() && !waitingRequests.isEmpty()) {
            AndroidDriver driver = availableDrivers.poll();
            DriverRequest nextRequest = waitingRequests.poll();

            if (nextRequest != null && driver != null) {
                nextRequest.getFuture().complete(driver);
            }
        }
    }

    @PreDestroy
    private void closeAll() {
        try {
            System.out.println("Pool Closing Emulators...");
            availableDrivers.forEach(driver -> {
                if (driver != null) {
                    driver.quit();
                }
            });
        } catch (Exception e) {
            System.out.println("The driver session had already been closed or the emulator was shut down first.");
        }
        System.out.println("Appium sessions closed successfully.");
    }

    private static class DriverRequest implements Comparable<DriverRequest> {
        private final boolean isHighPriority;
        private final long timestamp; // Mark the exact millisecond that arrived
        @Getter
        private final CompletableFuture<AndroidDriver> future;

        public DriverRequest(boolean isHighPriority, CompletableFuture<AndroidDriver> future) {
            this.isHighPriority = isHighPriority;
            this.timestamp = System.nanoTime();
            this.future = future;
        }

        @Override
        public int compareTo(@NotNull DriverRequest other) {
            // First rule, Telegram always has top priority
            if (this.isHighPriority && !other.isHighPriority) return -1;
            if (!this.isHighPriority && other.isHighPriority) return 1;

            // Second rule, if both are VIP or Common, it serves who arrived first
            return Long.compare(this.timestamp, other.timestamp);
        }
    }
}

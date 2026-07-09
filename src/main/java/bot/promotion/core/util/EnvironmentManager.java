package bot.promotion.core.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class EnvironmentManager {
    private static final String MUMU_MANAGER_PATH = "C:\\Program Files\\Netease\\MuMuPlayer\\nx_device\\12.0\\shell\\MuMuNxDevice.exe";
    // The number of emulator instances is directly linked
    private static final int INSTANCE_COUNT = 2;

    private static final int BASE_APPIUM_PORT = 4723;

    public static void prepareEnvironment() {
        System.out.println("Preparing environment...");
        registerShutdownHook();
        try {
            startAppiumServer();
            startEmulator();
            connectAdbToMuMu();
            System.out.println("Environment is ready.");
        } catch (Exception e) {
            throw new RuntimeException("Error preparing environment: " + e.getMessage());
        }
    }

    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down environment...");
            try {
                killProcess("node.exe");
                killProcess("cmd.exe");

                killProcess("MuMuNxDevice.exe");
                killProcess("MuMuNxMain.exe");

                System.out.println("Environment shut down successfully.");
            } catch (Exception e) {
                throw new RuntimeException("Error during shutdown: " + e.getMessage());
            }
        }));
    }

    private static void startAppiumServer() throws IOException, InterruptedException {
        for (int i = 0; i < INSTANCE_COUNT; i++) {
            int port = BASE_APPIUM_PORT + (i * 2);
            if (isPortInUse(port)) {
                System.out.println("Appium server is already running on port " + port);
                return;
            }
            System.out.println("Starting Appium server...");

            ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", "start", "appium", "-p", String.valueOf(port));
            builder.start();

            int attemps = 0;
            while (!isPortInUse(port) && attemps < 60) {
                Thread.sleep(1000);
                attemps++;
            }

            if (isPortInUse(port)) {
                System.out.println("Appium server started successfully.");
            } else {
                throw new IOException("Appium server start failed.");
            }
        }
    }

    private static void startEmulator() throws IOException {
        ProcessBuilder builder = new ProcessBuilder("taskmgr.exe");
        Process process = builder.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        boolean isRunning = false;
        while ((line = reader.readLine()) != null) {
            if (line.contains("MuMuNxDevice.exe")) {
                isRunning = true;
                break;
            }
        }

        if (!isRunning) {
            System.out.println("Starting MuMu emulator...");
            for (int i = 0; i < INSTANCE_COUNT; i++) {
                launchMuMuInstance(String.valueOf(i));
            }
            System.out.println("MuMu emulator is already running.");
        }
    }

    private static void launchMuMuInstance(String instanceIndex) {
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    MUMU_MANAGER_PATH, "api", "-v", instanceIndex, "launch_player"
            );
            builder.start();
        } catch (IOException e) {
            System.out.println("Error launching MuMu instance: #" + instanceIndex + " - " + e.getMessage());
        }
    }

    private static void connectAdbToMuMu() throws InterruptedException {
        System.out.println("Connecting ADB to MuMu instances (Dynamic Port Discovery)...");

        for (int i = 0; i < INSTANCE_COUNT; i++) {
            /*
              The rule logic for the MuMu Player port is:
              The base port of instance X = 16384 + (X * 32)
              Ex: Instance 0 -> 16384. Instance 1 -> 16416. Instance 2 -> 16448
             */
            int basePort = 16384 + (i * 32);
            int endPort = basePort + 10;
            Thread.sleep(5000); // Wait a second before attempting to connect to the next instance
            String udid = discoverAndConnectAdb("Instance " + i, basePort, endPort);
            waitForDeviceBoot(udid);
            int appiumPort = BASE_APPIUM_PORT + (i * 2);
            int systemPort = 8200 + i;

            System.setProperty("appium.emulator[" + i + "].udid", udid);
            System.setProperty("appium.emulator[" + i + "].systemPort", String.valueOf(systemPort));
            System.setProperty("appium.emulator[" + i + "].serverUrl", "http://127.0.0.1:" + appiumPort);

            System.out.println("Injected Spring Property -> appium.emulators[" + i + "] (UDID: " + udid + ", Server: " + appiumPort + ")");
        }
    }

    private static String discoverAndConnectAdb(String instanceName, int startPort, int endPort) {
        for (int port = startPort; port <= endPort; port++) {
            String udid = "127.0.0.1:" + port;
            try {
                ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", "adb connect " + udid);
                builder.redirectErrorStream(true);
                Process process = builder.start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                boolean isConnected = false;

                while ((line = reader.readLine()) != null) {
                    if (line.toLowerCase().contains("connected to") || line.toLowerCase().contains("already connected")) {
                        isConnected = true;
                    }
                }
                process.waitFor();

                if (isConnected) {
                    System.out.println(">>> Success! " + instanceName + " found on port " + port);
                    return udid; // Returns the correct port and ends the search for this instance
                }
            } catch (Exception e) {
                System.out.println("Warning: Failed to attempt port:  " + port + " (" + e.getMessage() + ")");
            }
        }
        throw new RuntimeException("Critical Failure: " + instanceName + " did not respond on any port between " + startPort + " and " + endPort);
    }

    private static void waitForDeviceBoot(String udid) {
        System.out.println("Waiting for Android OS to fully boot on " + udid + "...");
        int maxAttempts = 60; // Waiting maximum 2 minutes (60 * 2 seconds)

        for (int i = 0; i < maxAttempts; i++) {
            try {
                ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", "adb -s " + udid + " shell getprop sys.boot_completed");
                Process process = builder.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line = reader.readLine();

                if ("1".equals(line)) {
                    System.out.println(">>> Success! " + udid + " has fully booted.");
                    Thread.sleep(3000);
                    return;
                }
            } catch (Exception e) {
                // Ignore it and keep trying
            }
        }
        throw new RuntimeException("Critical failure: The emulator " + udid + "took a long time to turn on or crashed");
    }

    private static boolean isPortInUse(int port) {
        try (Socket ignore = new Socket("127.0.0.1", port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void killProcess(String processName) throws IOException {
        new ProcessBuilder("taskkill", "/F", "/IM", processName).start();
    }
}

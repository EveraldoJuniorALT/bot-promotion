package bot.promotion.core.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class EnvironmentManager {
    private static final String MUMU_MANAGER_PATH = "C:\\Program Files\\Netease\\MuMuPlayer\\nx_device\\12.0\\shell\\MuMuNxDevice.exe";
    private static final String APPIUM_PORT = "4723";

    public static void prepareEnvironment() {
        System.out.println("Preparing environment...");
        registerShutdownHook();
        try {
            startAppiumServer();
            startEmulator();
            connectAdbToMuMu();
            System.out.println("Environment is ready.");
        } catch (Exception e) {
            System.out.println("Error preparing environment: " + e.getMessage());
        }
    }

    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down environment...");
            try {
                killProcess("node.exe");
                killProcess("cmd.exe");

                shutdownMuMuInstance("0");
                shutdownMuMuInstance("1");

                System.out.println("Environment shut down successfully.");
            } catch (Exception e) {
                System.out.println("Error during shutdown: " + e.getMessage());
            }
        }));
    }

    private static void startAppiumServer() throws IOException, InterruptedException {
        if (isPortInUse(Integer.parseInt(APPIUM_PORT))) {
            System.out.println("Appium server is already running on port " + APPIUM_PORT);
            return;
        }
        System.out.println("Starting Appium server...");

        ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", "start", "appium");
        builder.start();

        int attemps = 0;
        while (!isPortInUse(Integer.parseInt(APPIUM_PORT)) && attemps < 20) {
            Thread.sleep(1000);
            attemps++;
        }

        if (isPortInUse(Integer.parseInt(APPIUM_PORT))) {
            System.out.println("Appium server started successfully.");
        } else {
            System.out.println("Failed to start Appium server within the expected time.");
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
            launchMuMuInstance("0");
            launchMuMuInstance("1");
            System.out.println("MuMu emulator is already running.");
            try {
                Thread.sleep(20000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
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

    private static void connectAdbToMuMu() {
        System.out.println("Connecting ADB to MuMu instances (Dynamic Port Discovery)...");

        String udid0 = discoverAndConnectAdb("Instance 0", 16384, 16394);

        String udid1 = discoverAndConnectAdb("Instance 1", 16416, 16426);

        // Injects the discovered ports directly into the Spring Boot environment!
        System.setProperty("mumu.udid.instance0", udid0);
        System.setProperty("mumu.udid.instance1", udid1);
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
                    if (line.toLowerCase().contains("connected to")) {
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

    private static void shutdownMuMuInstance(String instanceIndex) {
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    MUMU_MANAGER_PATH, "api", "-v", instanceIndex, "shutdown_player"
            );
            builder.start();
        } catch (IOException e) {
            System.out.println("Error occurred while shutting down MuMu instance: " + e.getMessage());
        }
    }
}

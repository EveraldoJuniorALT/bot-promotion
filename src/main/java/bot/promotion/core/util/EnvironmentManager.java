package bot.promotion.core.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class EnvironmentManager {
    private static final String BLUESTACKS_PATH = "C:\\Program Files\\BlueStacks_nxt\\HD-Player.exe";
    private static final String APPIUM_PORT = "4723";

    public static void prepareEnvironment() {
        System.out.println("Preparing environment...");
        registerShutdownHook();
        try {
            startAppiumServer();
            startEmulator();
            connectAdb();
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
                killProcess("HD-Player.exe");
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
            if (line.contains("HD-Player.exe")) {
                isRunning = true;
                break;
            }
        }

        if (!isRunning) {
            System.out.println("Starting BlueStacks emulator...");
            new ProcessBuilder(BLUESTACKS_PATH).start();
            try {
                Thread.sleep(15000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (isRunning) {
            System.out.println("BlueStacks emulator is already running.");
        }
    }

    private static void connectAdb() throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", "adb connect 127.0.0.1:5555");
        builder.redirectErrorStream(true);
        Process process = builder.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println("ADB: " + line);
        }
        process.waitFor();
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

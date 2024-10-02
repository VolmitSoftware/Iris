/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.core.tools;

import com.volmit.iris.Iris;
import com.volmit.iris.util.format.C;
import oshi.SystemInfo;
import oshi.hardware.GlobalMemory;
import oshi.software.os.OperatingSystem;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.zip.Deflater;

import static com.google.common.math.LongMath.isPrime;
import static com.volmit.iris.util.misc.getHardware.getCPUModel;

public class IrisBenchmarking {
    public static boolean inProgress = false;
    static String ServerOS;
    static String filePath = "benchmark.dat";
    static double avgWriteSpeedMBps;
    static double avgReadSpeedMBps;
    static double highestWriteSpeedMBps;
    static double highestReadSpeedMBps;
    static double lowestWriteSpeedMBps;
    static double lowestReadSpeedMBps;
    static double calculateIntegerMath;
    static double calculateFloatingPoint;
    static double calculatePrimeNumbers;
    static double calculateStringSorting;
    static double calculateDataEncryption;
    static double calculateDataCompression;
    static String currentRunning = "None";
    static int BenchmarksCompleted = 0;
    static int BenchmarksTotal = 7;
    static int totalTasks = 10;
    static int currentTasks = 0;
    static double WindowsCPUCompression;
    static double WindowsCPUEncryption;
    static double WindowsCPUCSHA1;
    static double elapsedTimeNs;
    static boolean Winsat = false;
    static boolean WindowsDiskSpeed = false;
    static double startTime;
    // Good enough for now. . .

    public static void runBenchmark() throws InterruptedException {
        inProgress = true;
        getServerOS();
        deleteTestFile(filePath);
        AtomicReference<Double> doneCalculateDiskSpeed = new AtomicReference<>((double) 0);
        startBenchmarkTimer();
        Iris.info("Benchmark Started!");
        Iris.warn("Although it may seem momentarily paused, it's actively processing.");
        BenchmarksCompleted = 0;

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            currentRunning = "calculateDiskSpeed";
            progressBar();
            if (ServerOS.contains("Windows") && isRunningAsAdmin()) {
                WindowsDiskSpeed = true;
                WindowsDiskSpeedTest();
            } else {
                warningFallback();
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                doneCalculateDiskSpeed.set(roundToTwoDecimalPlaces(calculateDiskSpeed()));
                BenchmarksCompleted++;
            }


        }).thenRun(() -> {
            currentRunning = "WindowsCpuSpeedTest";
            progressBar();
            if (ServerOS.contains("Windows") && isRunningAsAdmin()) {
                Winsat = true;
                WindowsCpuSpeedTest();
            } else {
                Iris.info("Skipping:" + C.BLUE + " Windows System Assessment Tool Benchmarks");
                if (!ServerOS.contains("Windows")) {
                    Iris.info("Required Software:" + C.BLUE + " Windows");
                    BenchmarksTotal = 6;
                }
                if (!isRunningAsAdmin()) {
                    Iris.info(C.RED + "ERROR: " + C.DARK_RED + "Elevated privileges missing");
                    BenchmarksTotal = 6;
                }
            }

        }).thenRun(() -> {
            currentRunning = "calculateIntegerMath";
            progressBar();
            calculateIntegerMath = roundToTwoDecimalPlaces(calculateIntegerMath());
            BenchmarksCompleted++;
        }).thenRun(() -> {
            currentRunning = "calculateFloatingPoint";
            progressBar();
            calculateFloatingPoint = roundToTwoDecimalPlaces(calculateFloatingPoint());
            BenchmarksCompleted++;
        }).thenRun(() -> {
            currentRunning = "calculateStringSorting";
            progressBar();
            calculateStringSorting = roundToTwoDecimalPlaces(calculateStringSorting());
            BenchmarksCompleted++;
        }).thenRun(() -> {
            currentRunning = "calculatePrimeNumbers";
            progressBar();
            calculatePrimeNumbers = roundToTwoDecimalPlaces(calculatePrimeNumbers());
            BenchmarksCompleted++;
        }).thenRun(() -> {
            currentRunning = "calculateDataEncryption";
            progressBar();
            calculateDataEncryption = roundToTwoDecimalPlaces(calculateDataEncryption());
            BenchmarksCompleted++;
        }).thenRun(() -> {
            currentRunning = "calculateDataCompression";
            progressBar();
            calculateDataCompression = roundToTwoDecimalPlaces(calculateDataCompression());
            BenchmarksCompleted++;
        }).thenRun(() -> {
            elapsedTimeNs = stopBenchmarkTimer();
            results();
            inProgress = false;
        });

        try {
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    public static void progressBar() {
        Iris.info("-----------------------------------------------------");
        Iris.info("Currently Running: " + C.BLUE + currentRunning);
        // Iris.info("Tasks: " + "Current Tasks: " + C.BLUE + currentTasks + C.WHITE + " / " + "Total Tasks: " + C.BLUE + totalTasks);
        Iris.info("Benchmarks Completed: " + C.BLUE + BenchmarksCompleted + C.WHITE + " / " + "Total: " + C.BLUE + BenchmarksTotal);
        Iris.info("-----------------------------------------------------");
    }

    public static void results() {

        SystemInfo systemInfo = new SystemInfo();
        GlobalMemory globalMemory = systemInfo.getHardware().getMemory();
        long totalMemoryMB = globalMemory.getTotal() / (1024 * 1024);
        long availableMemoryMB = globalMemory.getAvailable() / (1024 * 1024);
        long totalPageSize = globalMemory.getPageSize() / (1024 * 1024);
        long usedMemoryMB = totalMemoryMB - availableMemoryMB;
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();

        Iris.info("OS: " + ServerOS);
        if (!isRunningAsAdmin() || !ServerOS.contains("Windows")) {
            Iris.info(C.GOLD + "For the full results use Windows + Admin Rights..");
        }
        Iris.info("CPU Model: " + getCPUModel());
        Iris.info("CPU Score: " + "WIP");
        Iris.info("- Integer Math: " + calculateIntegerMath + " MOps/Sec");
        Iris.info("- Floating Point Math: " + calculateFloatingPoint + " MOps/Sec");
        Iris.info("- Find Prime Numbers: " + calculatePrimeNumbers + " Primes/Sec");
        Iris.info("- Random String Sorting: " + calculateStringSorting + " Thousand Strings/Sec");
        Iris.info("- Data Encryption: " + formatDouble(calculateDataEncryption) + " MBytes/Sec");
        Iris.info("- Data Compression: " + formatDouble(calculateDataCompression) + " MBytes/Sec");

        if (WindowsDiskSpeed) {
            //Iris.info("Disk Model: " + getDiskModel());
            Iris.info(C.BLUE + "- Running with Windows System Assessment Tool");
            Iris.info("- Sequential 64.0 Write: " + C.BLUE + formatDouble(avgWriteSpeedMBps) + " Mbps");
            Iris.info("- Sequential 64.0 Read: " + C.BLUE + formatDouble(avgReadSpeedMBps) + " Mbps");
        } else {
            // Iris.info("Disk Model: " + getDiskModel());
            Iris.info(C.GREEN + "- Running in Native Mode");
            Iris.info("- Average Write Speed: " + C.GREEN + formatDouble(avgWriteSpeedMBps) + " Mbps");
            Iris.info("- Average Read Speed: " + C.GREEN + formatDouble(avgReadSpeedMBps) + " Mbps");
            Iris.info("- Highest Write Speed: " + formatDouble(highestWriteSpeedMBps) + " Mbps");
            Iris.info("- Highest Read Speed: " + formatDouble(highestReadSpeedMBps) + " Mbps");
            Iris.info("- Lowest Write Speed: " + formatDouble(lowestWriteSpeedMBps) + " Mbps");
            Iris.info("- Lowest Read Speed: " + formatDouble(lowestReadSpeedMBps) + " Mbps");
        }
        Iris.info("Ram Usage: ");
        Iris.info("- Total Ram: " + totalMemoryMB + " MB");
        Iris.info("- Used Ram: " + usedMemoryMB + " MB");
        Iris.info("- Total Process Ram: " + C.BLUE + getMaxMemoryUsage() + " MB");
        Iris.info("- Total Paging Size: " + totalPageSize + " MB");
        if (Winsat) {
            Iris.info(C.BLUE + "Windows System Assessment Tool: ");
            Iris.info("- CPU LZW Compression:" + C.BLUE + formatDouble(WindowsCPUCompression) + " MB/s");
            Iris.info("- CPU AES256 Encryption: " + C.BLUE + formatDouble(WindowsCPUEncryption) + " MB/s");
            Iris.info("- CPU SHA1 Hash: " + C.BLUE + formatDouble(WindowsCPUCSHA1) + " MB/s");
            Iris.info("Duration: " + roundToTwoDecimalPlaces(elapsedTimeNs) + " Seconds");
        }

    }

    public static long getMaxMemoryUsage() {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapMemoryUsage = memoryMXBean.getNonHeapMemoryUsage();
        long maxHeapMemory = heapMemoryUsage.getMax();
        long maxNonHeapMemory = nonHeapMemoryUsage.getMax();
        long maxMemoryUsageMB = (maxHeapMemory + maxNonHeapMemory) / (1024 * 1024);
        return maxMemoryUsageMB;
    }

    public static void getServerOS() {
        SystemInfo systemInfo = new SystemInfo();
        OperatingSystem os = systemInfo.getOperatingSystem();
        ServerOS = os.toString();
    }

    public static boolean isRunningAsAdmin() {
        if (ServerOS.contains("Windows")) {
            try {
                Process process = Runtime.getRuntime().exec("winsat disk");
                process.waitFor();
                return process.exitValue() == 0;
            } catch (IOException | InterruptedException e) {
                // Hmm
            }
        }
        return false;
    }

    public static void warningFallback() {
        Iris.info(C.RED + "Using the " + C.DARK_RED + "FALLBACK" + C.RED + " method due to compatibility issues. ");
        Iris.info(C.RED + "Please note that this may result in less accurate results.");
    }

    private static String formatDouble(double value) {
        return String.format("%.2f", value);
    }

    private static void startBenchmarkTimer() {
        startTime = System.nanoTime();
    }

    private static double stopBenchmarkTimer() {
        long endTime = System.nanoTime();
        return (endTime - startTime) / 1_000_000_000.0;
    }

    private static double calculateIntegerMath() {
        final int numIterations = 1_000_000_000;
        final int numRuns = 30;
        double totalMopsPerSec = 0;

        for (int run = 0; run < numRuns; run++) {
            long startTime = System.nanoTime();
            int result = 0;

            for (int i = 0; i < numIterations; i++) {
                result += i * 2;
                result -= i / 2;
                result ^= i;
                result <<= 1;
                result >>= 1;
            }

            long endTime = System.nanoTime();
            double elapsedSeconds = (endTime - startTime) / 1_000_000_000.0;
            double mopsPerSec = (numIterations / elapsedSeconds) / 1_000_000.0;

            totalMopsPerSec += mopsPerSec;
        }

        double averageMopsPerSec = totalMopsPerSec / numRuns;
        return averageMopsPerSec;
    }

    private static double calculateFloatingPoint() {
        long numIterations = 85_000_000;
        int numRuns = 30;
        double totalMopsPerSec = 0;
        for (int run = 0; run < numRuns; run++) {
            double result = 0;
            long startTime = System.nanoTime();

            for (int i = 0; i < numIterations; i++) {
                result += Math.sqrt(i) * Math.sin(i) / (i + 1);
            }

            long endTime = System.nanoTime();
            double elapsedSeconds = (endTime - startTime) / 1_000_000_000.0;
            double mopsPerSec = (numIterations / elapsedSeconds) / 1_000_000.0;

            totalMopsPerSec += mopsPerSec;
        }

        double averageMopsPerSec = totalMopsPerSec / numRuns;
        return averageMopsPerSec;
    }

    private static double calculatePrimeNumbers() {
        int primeCount;
        long numIterations = 1_000_000;
        int numRuns = 30;
        double totalMopsPerSec = 0;

        for (int run = 0; run < numRuns; run++) {
            primeCount = 0;
            long startTime = System.nanoTime();

            for (int num = 2; primeCount < numIterations; num++) {
                if (isPrime(num)) {
                    primeCount++;
                }
            }

            long endTime = System.nanoTime();
            double elapsedSeconds = (endTime - startTime) / 1_000_000_000.0;
            double mopsPerSec = (primeCount / elapsedSeconds) / 1_000_000.0;

            totalMopsPerSec += mopsPerSec;
        }

        double averageMopsPerSec = totalMopsPerSec / numRuns;
        return averageMopsPerSec;
    }

    private static double calculateStringSorting() {
        int stringCount = 1_000_000;
        int stringLength = 100;
        int numRuns = 30;
        double totalMopsPerSec = 0;

        for (int run = 0; run < numRuns; run++) {
            List<String> randomStrings = generateRandomStrings(stringCount, stringLength);
            long startTime = System.nanoTime();
            randomStrings.sort(String::compareTo);
            long endTime = System.nanoTime();

            double elapsedSeconds = (endTime - startTime) / 1_000_000_000.0;
            double mopsPerSec = (stringCount / elapsedSeconds) / 1_000.0;

            totalMopsPerSec += mopsPerSec;
        }

        double averageMopsPerSec = totalMopsPerSec / numRuns;
        return averageMopsPerSec;
    }

    public static double calculateDataEncryption() {
        int dataSizeMB = 100;
        byte[] dataToEncrypt = generateRandomData(dataSizeMB * 1024 * 1024);
        int numRuns = 20;
        double totalMBytesPerSec = 0;

        for (int run = 0; run < numRuns; run++) {
            long startTime = System.nanoTime();
            byte[] encryptedData = performEncryption(dataToEncrypt, 1);

            long endTime = System.nanoTime();
            double elapsedSeconds = (endTime - startTime) / 1_000_000_000.0;
            double mbytesPerSec = (dataToEncrypt.length / (1024 * 1024.0)) / elapsedSeconds;

            totalMBytesPerSec += mbytesPerSec;
        }

        double averageMBytesPerSec = totalMBytesPerSec / numRuns;
        return averageMBytesPerSec;
    }

    private static byte[] performEncryption(byte[] data, int numRuns) {
        byte[] key = "MyEncryptionKey".getBytes();
        byte[] result = Arrays.copyOf(data, data.length);
        for (int run = 0; run < numRuns; run++) {
            for (int i = 0; i < result.length; i++) {
                result[i] ^= key[i % key.length];
            }
        }
        return result;
    }

    public static double calculateDataCompression() {
        int dataSizeMB = 500;
        byte[] dataToCompress = generateRandomData(dataSizeMB * 1024 * 1024);
        long startTime = System.nanoTime();
        byte[] compressedData = performCompression(dataToCompress);
        long endTime = System.nanoTime();

        double elapsedSeconds = (endTime - startTime) / 1e9;
        double mbytesPerSec = (compressedData.length / (1024.0 * 1024.0)) / elapsedSeconds;

        return mbytesPerSec;
    }

    private static byte[] performCompression(byte[] data) {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);

        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            outputStream.write(buffer, 0, count);
        }

        deflater.end();
        return outputStream.toByteArray();
    }

    private static List<String> generateRandomStrings(int count, int length) {
        SecureRandom random = new SecureRandom();
        List<String> randomStrings = new ArrayList<>();

        IntStream.range(0, count).forEach(i -> {
            byte[] bytes = new byte[length];
            random.nextBytes(bytes);
            randomStrings.add(Base64.getEncoder().encodeToString(bytes));
        });
        return randomStrings;
    }

    private static byte[] generateRandomData(int size) {
        SecureRandom random = new SecureRandom();
        byte[] data = new byte[size];
        random.nextBytes(data);
        return data;
    }

    private static double roundToTwoDecimalPlaces(double value) {
        return Double.parseDouble(String.format("%.2f", value));
    }

    private static double calculateCPUScore(long elapsedTimeNs) {
        return 1.0 / (elapsedTimeNs / 1_000_000.0);
    }

    public static double calculateDiskSpeed() {
        int numRuns = 10;
        int fileSizeMB = 1000;

        double[] writeSpeeds = new double[numRuns];
        double[] readSpeeds = new double[numRuns];

        for (int run = 0; run < numRuns; run++) {
            long writeStartTime = System.nanoTime();
            deleteTestFile(filePath);
            createTestFile(filePath, fileSizeMB);
            long writeEndTime = System.nanoTime();

            long readStartTime = System.nanoTime();
            readTestFile(filePath);
            long readEndTime = System.nanoTime();

            double writeSpeed = calculateDiskSpeedMBps(fileSizeMB, writeStartTime, writeEndTime);
            double readSpeed = calculateDiskSpeedMBps(fileSizeMB, readStartTime, readEndTime);

            writeSpeeds[run] = writeSpeed;
            readSpeeds[run] = readSpeed;

            if (run == 0) {
                lowestWriteSpeedMBps = writeSpeed;
                highestWriteSpeedMBps = writeSpeed;
                lowestReadSpeedMBps = readSpeed;
                highestReadSpeedMBps = readSpeed;
            } else {
                if (writeSpeed < lowestWriteSpeedMBps) {
                    lowestWriteSpeedMBps = writeSpeed;
                }
                if (writeSpeed > highestWriteSpeedMBps) {
                    highestWriteSpeedMBps = writeSpeed;
                }
                if (readSpeed < lowestReadSpeedMBps) {
                    lowestReadSpeedMBps = readSpeed;
                }
                if (readSpeed > highestReadSpeedMBps) {
                    highestReadSpeedMBps = readSpeed;
                }
            }
        }
        avgWriteSpeedMBps = calculateAverage(writeSpeeds);
        avgReadSpeedMBps = calculateAverage(readSpeeds);
        return 2;
    }

    public static void createTestFile(String filePath, int fileSizeMB) {
        try {
            File file = new File(filePath);
            byte[] data = new byte[1024 * 1024];
            Arrays.fill(data, (byte) 0);
            FileOutputStream fos = new FileOutputStream(file);
            for (int i = 0; i < fileSizeMB; i++) {
                fos.write(data);
            }
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void readTestFile(String filePath) {
        try {
            File file = new File(filePath);
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[1024];
            while (fis.read(buffer) != -1) {
            }
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void deleteTestFile(String filePath) {
        File file = new File(filePath);
        file.delete();
    }

    public static double calculateDiskSpeedMBps(int fileSizeMB, long startTime, long endTime) {
        double elapsedSeconds = (endTime - startTime) / 1_000_000_000.0;
        double writeSpeed = (fileSizeMB / elapsedSeconds);
        return writeSpeed;
    }

    public static double calculateAverage(double[] values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    public static void WindowsDiskSpeedTest() {
        try {
            String command = "winsat disk";
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            while ((line = reader.readLine()) != null) {
                Iris.debug(line);

                if (line.contains("Disk  Sequential 64.0 Read")) {
                    avgReadSpeedMBps = extractSpeed(line);
                } else if (line.contains("Disk  Sequential 64.0 Write")) {
                    avgWriteSpeedMBps = extractSpeed(line);
                }
            }

            process.waitFor();
            process.destroy();

            Iris.debug("Sequential Read Speed: " + avgReadSpeedMBps + " MB/s");
            Iris.debug("Sequential Write Speed: " + avgWriteSpeedMBps + " MB/s");
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static double extractSpeed(String line) {
        String[] tokens = line.split("\\s+");
        for (int i = 0; i < tokens.length; i++) {
            if (tokens[i].endsWith("MB/s") && i > 0) {
                try {
                    return Double.parseDouble(tokens[i - 1]);
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }
        }
        return 0.0;
    }

    public static void WindowsCpuSpeedTest() {
        try {
            String command = "winsat cpuformal";
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            while ((line = reader.readLine()) != null) {
                Iris.debug(line);

                if (line.contains("CPU AES256 Encryption")) {
                    WindowsCPUEncryption = extractCpuInfo(line);
                }
                if (line.contains("CPU LZW Compression")) {
                    WindowsCPUCompression = extractCpuInfo(line);
                }
                if (line.contains("CPU SHA1 Hash")) {
                    WindowsCPUCSHA1 = extractCpuInfo(line);
                }
            }
            process.waitFor();
            process.destroy();

            Iris.debug("Winsat Encryption: " + WindowsCPUEncryption + " MB/s");
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static double extractCpuInfo(String line) {
        String[] tokens = line.split("\\s+");
        for (int i = 0; i < tokens.length; i++) {
            if (tokens[i].endsWith("MB/s") && i > 0) {
                try {
                    return Double.parseDouble(tokens[i - 1]);
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }
        }
        return 0.0;
    }

}
package com.volmit.iris.core.tools;

import com.volmit.iris.Iris;
import com.volmit.iris.util.format.C;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HWDiskStore;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.zip.Deflater;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.google.common.math.LongMath.isPrime;

public class IrisBenchmarking {
    private static long startTime;
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
    static int BenchmarksCompleted = -1;
    static int BenchmarksTotal = 6;
    static int totalTasks = 10;
    static int currentTasks = 0;
    static double elapsedTimeNs;
    public static boolean inProgress = false;
    // Good enough for now. . .

    public static void runBenchmark() throws InterruptedException {
        inProgress = true;
        AtomicReference<Double> doneCalculateDiskSpeed = new AtomicReference<>((double) 0);
        startBenchmarkTimer();
        Iris.info("Benchmark Started!");
        Iris.warn("Although it may seem momentarily paused, it's actively processing.");
        Thread progressBarThread = new Thread(() -> {
            try {
                progressBar();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        progressBarThread.start();

        // help
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            doneCalculateDiskSpeed.set(roundToTwoDecimalPlaces(calculateDiskSpeed()));
        }).thenRun(() -> {
            BenchmarksCompleted++;
            calculateIntegerMath = roundToTwoDecimalPlaces(calculateIntegerMath());
        }).thenRun(() -> {
            BenchmarksCompleted++;
            calculateFloatingPoint = roundToTwoDecimalPlaces(calculateFloatingPoint());
        }).thenRun(() -> {
            BenchmarksCompleted++;
            calculateStringSorting = roundToTwoDecimalPlaces(calculateStringSorting());
        }).thenRun(() -> {
            BenchmarksCompleted++;
            calculateDataEncryption = roundToTwoDecimalPlaces(calculateDataEncryption());
        }).thenRun(() -> {
            BenchmarksCompleted++;
            calculateDataCompression = roundToTwoDecimalPlaces(calculateDataCompression());
        }).thenRun(() -> {
            BenchmarksCompleted++;
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

    private static int previousCompleted = BenchmarksCompleted;

    // why just why
    public static void progressBar() throws InterruptedException {
        while (true) {
            if (BenchmarksCompleted > previousCompleted) {
                Iris.info("-----------------------------------------------------");
                Iris.info("Currently Running: " + C.BLUE + currentRunning);
                // Iris.info("Tasks: " + "Current Tasks: " + C.BLUE + currentTasks + C.WHITE + " / " + "Total Tasks: " + C.BLUE + totalTasks);
                Iris.info("Benchmarks Completed: " + C.BLUE + BenchmarksCompleted + C.WHITE + " / " +"Total: " + C.BLUE + BenchmarksTotal);
                Iris.info("-----------------------------------------------------");

                previousCompleted = BenchmarksCompleted; // Update the previous value
            }

            if (BenchmarksCompleted == BenchmarksTotal) {
                break;
            }
            Thread.sleep(10);
        }
    }



    public static void results() {

        SystemInfo systemInfo = new SystemInfo();
        GlobalMemory globalMemory = systemInfo.getHardware().getMemory();
        long totalMemoryMB = globalMemory.getTotal() / (1024 * 1024);
        long availableMemoryMB = globalMemory.getAvailable() / (1024 * 1024);
        long totalPageSize = globalMemory.getPageSize() / (1024 * 1024);
        long usedMemoryMB = totalMemoryMB - availableMemoryMB;
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();

        Iris.info("OS: " + serverOS());
        Iris.info("CPU Model: " + getCPUModel());
        Iris.info("CPU Score: " + "WIP");
        Iris.info("- Integer Math: " + calculateIntegerMath + " MOps/Sec");
        Iris.info("- Floating Point Math: " + calculateFloatingPoint + " MOps/Sec");
        Iris.info("- Find Prime Numbers: " + calculatePrimeNumbers + " Primes/Sec");
        Iris.info("- Random String Sorting: " + calculateStringSorting + " Thousand Strings/Sec");
        Iris.info("- Data Encryption: " + formatDouble(calculateDataEncryption) + " MBytes/Sec");
        Iris.info("- Data Compression: " + formatDouble(calculateDataCompression) + " MBytes/Sec");
        Iris.info("Disk Model: " + getDiskModel());
        Iris.info("- Average Write Speed: " + C.BLUE + formatDouble(avgWriteSpeedMBps) + " Mbp/Sec");
        Iris.info("- Average Read Speed: " + C.BLUE + formatDouble(avgReadSpeedMBps) + " Mbp/Sec");
        Iris.info("- Highest Write Speed: " + formatDouble(highestWriteSpeedMBps) + " Mbp/Sec");
        Iris.info("- Highest Read Speed: " + formatDouble(highestReadSpeedMBps) + " Mbp/Sec");
        Iris.info("- Lowest Write Speed: " + formatDouble(lowestWriteSpeedMBps) + " Mbp/Sec");
        Iris.info("- Lowest Read Speed: " + formatDouble(lowestReadSpeedMBps) + " Mbp/Sec");
        Iris.info("Ram Usage: ");
        Iris.info("- Total Ram: " + totalMemoryMB + " MB");
        Iris.info("- Used Ram: " + usedMemoryMB + " MB");
        Iris.info("- Total Process Ram: " + C.BLUE + getMaxMemoryUsage() + " MB");
        Iris.info("- Total Paging Size: " + totalPageSize + " MB");
        Iris.info("Duration: " + roundToTwoDecimalPlaces(elapsedTimeNs) + " Seconds");
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
    public static String serverOS() {
        SystemInfo systemInfo = new SystemInfo();
        OperatingSystem os = systemInfo.getOperatingSystem();
        String osInfo = os.toString();
        return osInfo;
    }
    public static String getCPUModel() {
        try {
            SystemInfo systemInfo = new SystemInfo();
            CentralProcessor processor = systemInfo.getHardware().getProcessor();
            String cpuModel = processor.getProcessorIdentifier().getName();
            return cpuModel.isEmpty() ? "Unknown CPU Model" : cpuModel;
        } catch (Exception e) {
            e.printStackTrace();
            return "Unknown CPU Model";
        }
    }
    public static String getDiskModel() {
        SystemInfo systemInfo = new SystemInfo();
        List<HWDiskStore> diskStores = systemInfo.getHardware().getDiskStores();
        if (!diskStores.isEmpty()) {
            HWDiskStore firstDisk = diskStores.get(0);
            return firstDisk.getModel();
        } else {
            return "Unknown Disk Model";
        }
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
    public static double calculateCpuScore(
            double calculateIntegerMath,
            double calculateFloatingPoint,
            double calculatePrimeNumbers,
            double calculateStringSorting,
            double calculateDataEncryption,
            double calculateDataCompression
    ) {
        double weightIntegerMath = 1.0;
        double weightFloatingPoint = 1.0;
        double weightPrimeNumbers = 1.0;
        double weightStringSorting = 1.0;
        double weightDataEncryption = 1.0;
        double weightDataCompression = 1.0;

        double invertedIntegerMath = 1.0 / calculateIntegerMath;
        double invertedFloatingPoint = 1.0 / calculateFloatingPoint;
        double invertedPrimeNumbers = 1.0 / calculatePrimeNumbers;
        double invertedStringSorting = 1.0 / calculateStringSorting;
        double invertedDataEncryption = 1.0 / calculateDataEncryption;
        double invertedDataCompression = 1.0 / calculateDataCompression;

        double cpuScore =
                (
                        invertedIntegerMath * weightIntegerMath +
                                invertedFloatingPoint * weightFloatingPoint +
                                invertedPrimeNumbers * weightPrimeNumbers +
                                invertedStringSorting * weightStringSorting +
                                invertedDataEncryption * weightDataEncryption +
                                invertedDataCompression * weightDataCompression) /
                        (weightIntegerMath + weightFloatingPoint + weightPrimeNumbers + weightStringSorting + weightDataEncryption + weightDataCompression);

        return cpuScore;
    }
    private static double calculateIntegerMath() {
        currentRunning = "calculateIntegerMath";
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
        int percent = 10;
        double totalMopsPerSec = 0;

        for (int run = 0; run < numRuns; run++) {
            percent = percent + 5;
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
        currentRunning = "calculatePrimeNumbers";
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
        currentRunning = "calculateStringSorting";
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
        currentRunning = "calculateDataEncryption";
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
        currentRunning = "calculateDataCompression";
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
        currentRunning = "calculateDiskSpeed";
        String filePath = "benchmark.dat";
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
}
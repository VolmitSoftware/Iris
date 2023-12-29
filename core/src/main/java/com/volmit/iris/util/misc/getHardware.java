package com.volmit.iris.util.misc;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.HWDiskStore;
import oshi.software.os.OperatingSystem;

import java.util.List;

public class getHardware {
    public static String getServerOS() {
        SystemInfo systemInfo = new SystemInfo();
        OperatingSystem os = systemInfo.getOperatingSystem();
        return os.toString();
    }
    public static long getProcessMemory(){
        long maxMemory = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        return maxMemory;
    }
    public static long getProcessUsedMemory() {
        Runtime runtime = Runtime.getRuntime();

        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        return usedMemory / (1024 * 1024);
    }

    public static long getAvailableProcessMemory(){
        long availableMemory = getHardware.getProcessMemory() - getHardware.getProcessUsedMemory();
        return availableMemory;
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
}

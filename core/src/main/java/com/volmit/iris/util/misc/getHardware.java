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
    public static int getCPUThreads(){
        SystemInfo systemInfo = new SystemInfo();
        CentralProcessor processor = systemInfo.getHardware().getProcessor();
        return processor.getLogicalProcessorCount();
    }
    public static long getProcessMemory(){
        long maxMemory = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        return maxMemory;
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

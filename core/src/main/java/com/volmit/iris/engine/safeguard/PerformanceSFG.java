package com.volmit.iris.engine.safeguard;

import oshi.SystemInfo;
import oshi.hardware.GlobalMemory;

import static com.volmit.iris.util.misc.getHardware.*;

public class PerformanceSFG {
   public static byte CPUPerformanceStage = 3;
    public void getPerformance(){
        // Performance Stage 3 = Max Performance, 2=Medium, 1=Low

        SystemInfo systemInfo = new SystemInfo();
        GlobalMemory globalMemory = systemInfo.getHardware().getMemory();
        long totalMemoryMB = globalMemory.getTotal() / (1024 * 1024);
        long availableMemoryMB = globalMemory.getAvailable() / (1024 * 1024);
        long totalPageSize = globalMemory.getPageSize() / (1024 * 1024);
        long usedMemoryMB = totalMemoryMB - availableMemoryMB;

        // Todo RePixelated: Finish this

    }
}

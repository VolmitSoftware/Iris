package com.volmit.iris.engine.safeguard;

import oshi.SystemInfo;
import oshi.hardware.GlobalMemory;

import static com.volmit.iris.util.misc.getHardware.*;

public class PerformanceSFG {
   public static boolean lowPerformance = false;
    public static void calculatePerformance(){

        if (getCPUModel().contains("Xeon")){
            lowPerformance = true;
        }

        // Todo RePixelated: Finish this
    }
}

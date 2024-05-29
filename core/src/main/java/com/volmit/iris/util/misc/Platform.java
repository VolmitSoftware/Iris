package com.volmit.iris.util.misc;

import com.sun.management.OperatingSystemMXBean;

import java.io.File;
import java.lang.management.ManagementFactory;

@SuppressWarnings("restriction")
public class Platform {
    public static String getVersion() {
        return getSystem().getVersion();
    }

    public static String getName() {
        return getSystem().getName();
    }

    private static OperatingSystemMXBean getSystem() {
        return (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    }

    public static class ENVIRONMENT {
        public static boolean canRunBatch() {
            return getSystem().getName().toLowerCase().contains("windows");
        }

        public static String getJavaHome() {
            return System.getProperty("java.home");
        }

        public static String getJavaVendor() {
            return System.getProperty("java.vendor");
        }

        public static String getJavaVersion() {
            return System.getProperty("java.version");
        }
    }

    public static class STORAGE {
        public static long getAbsoluteTotalSpace() {
            long t = 0;

            for (File i : getRoots()) {
                t += getTotalSpace(i);
            }

            return t;
        }

        public static long getTotalSpace() {
            return getTotalSpace(new File("."));
        }

        public static long getTotalSpace(File root) {
            return root.getTotalSpace();
        }

        public static long getAbsoluteFreeSpace() {
            long t = 0;

            for (File i : getRoots()) {
                t += getFreeSpace(i);
            }

            return t;
        }

        public static long getFreeSpace() {
            return getFreeSpace(new File("."));
        }

        public static long getFreeSpace(File root) {
            return root.getFreeSpace();
        }

        public static long getUsedSpace() {
            return getTotalSpace() - getFreeSpace();
        }

        public static long getUsedSpace(File root) {
            return getTotalSpace(root) - getFreeSpace(root);
        }

        public static long getAbsoluteUsedSpace() {
            return getAbsoluteTotalSpace() - getAbsoluteFreeSpace();
        }

        public static File[] getRoots() {
            return File.listRoots();
        }
    }

    public static class MEMORY {
        public static class PHYSICAL {
            public static long getTotalMemory() {
                return getSystem().getTotalPhysicalMemorySize();
            }

            public static long getFreeMemory() {
                return getSystem().getFreePhysicalMemorySize();
            }

            public static long getUsedMemory() {
                return getTotalMemory() - getFreeMemory();
            }
        }

        public static class VIRTUAL {
            public static long getTotalMemory() {
                return getSystem().getTotalSwapSpaceSize();
            }

            public static long getFreeMemory() {
                return getSystem().getFreeSwapSpaceSize();
            }

            public static long getUsedMemory() {
                return getTotalMemory() - getFreeMemory();
            }

            public static long getCommittedVirtualMemory() {
                return getSystem().getCommittedVirtualMemorySize();
            }
        }
    }

    public static class CPU {
        public static int getAvailableProcessors() {
            return getSystem().getAvailableProcessors();
        }

        public static double getCPULoad() {
            return getSystem().getSystemCpuLoad();
        }

        public static double getLiveProcessCPULoad() {
            return getSystem().getProcessCpuLoad();
        }

        public static String getArchitecture() {
            return getSystem().getArch();
        }
    }
}
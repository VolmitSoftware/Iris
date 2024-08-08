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

package com.volmit.iris.util.misc;


import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import oshi.SystemInfo;
import oshi.hardware.*;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class getHardware {
    public static String getServerOS() {
        SystemInfo systemInfo = new SystemInfo();
        OperatingSystem os = systemInfo.getOperatingSystem();
        return os.toString();
    }

    public static long getProcessMemory() {
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

    public static long getAvailableProcessMemory() {
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

    public static KList<String> getSensors() {
        try {
            KList<String> temps = new KList<>();
            SystemInfo systemInfo = new SystemInfo();
            temps.add("CPU Temperature: " + systemInfo.getHardware().getSensors().getCpuTemperature());
            temps.add("CPU Voltage: " + systemInfo.getHardware().getSensors().getCpuTemperature());
            temps.add("Fan Speeds: " + Arrays.toString(systemInfo.getHardware().getSensors().getFanSpeeds()));
            return temps.copy();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static KList<String> getGraphicsCards() {
        try {
            KList<String> gpus = new KList<>();
            SystemInfo systemInfo = new SystemInfo();
            for (GraphicsCard gpu : systemInfo.getHardware().getGraphicsCards()) {
                gpus.add(C.BLUE + "Gpu Model: " + C.GRAY + gpu.getName());
                gpus.add("- vRam Size: " + C.GRAY + Form.memSize(gpu.getVRam()));
                gpus.add("- Vendor: " + C.GRAY + gpu.getVendor());
            }
            return gpus.copy();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static KList<String> getDisk() {
        try {
            KList<String> systemDisks = new KList<>();
            SystemInfo systemInfo = new SystemInfo();
            List<HWDiskStore> diskStores = systemInfo.getHardware().getDiskStores();
            OperatingSystem operatingSystem = systemInfo.getOperatingSystem();
            List<OSFileStore> fileStores = operatingSystem.getFileSystem().getFileStores();

            for (HWDiskStore disk : diskStores) {
                systemDisks.add(C.BLUE + "Disk: " + disk.getName());
                systemDisks.add("- Model: " + disk.getModel());
                systemDisks.add("Partitions: " + disk.getPartitions());
                for (OSFileStore partition : fileStores) {
                    systemDisks.add(C.BLUE + "- Name: " + partition.getName());
                    systemDisks.add(" - Description: " + partition.getDescription());
                    systemDisks.add(" - Total Space: " + Form.memSize(partition.getTotalSpace()));
                    systemDisks.add(" - Free Space: " + Form.memSize(partition.getFreeSpace()));
                    systemDisks.add(" - Mount: " + partition.getMount());
                    systemDisks.add(" - Label: " + partition.getLabel());
                }
                systemDisks.add(C.DARK_GRAY + "-=" + C.BLUE + " Since Boot " + C.DARK_GRAY + "=- ");
                systemDisks.add("- Total Reads: " + Form.memSize(disk.getReadBytes()));
                systemDisks.add("- Total Writes: " + Form.memSize(disk.getWriteBytes()));
            }
            if (systemDisks.isEmpty()) {
                systemDisks.add("Failed to get disks.");
            }
            return systemDisks.copy();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static KList<String> getPowerSources() {
        try {
            KList<String> systemPowerSources = new KList<>();
            SystemInfo systemInfo = new SystemInfo();
            List<PowerSource> powerSources = systemInfo.getHardware().getPowerSources();
            for (PowerSource powersource : powerSources) {
                systemPowerSources.add(C.BLUE + "- Name: " + powersource.getName());
                systemPowerSources.add("- RemainingCapacityPercent: " + powersource.getRemainingCapacityPercent());
                systemPowerSources.add("- Power Usage Rate: " + powersource.getPowerUsageRate());
                systemPowerSources.add("- Power OnLine: " + powersource.isPowerOnLine());
                systemPowerSources.add("- Capacity Units: " + powersource.getCapacityUnits());
                systemPowerSources.add("- Cycle Count: " + powersource.getCycleCount());
            }
            if (systemPowerSources.isEmpty()) {
                systemPowerSources.add("No PowerSources.");
            }
            return systemPowerSources.copy();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static KList<String> getEDID() {
        try {
            KList<String> systemEDID = new KList<>();
            SystemInfo systemInfo = new SystemInfo();
            HardwareAbstractionLayer hardware = systemInfo.getHardware();
            List<Display> displays = hardware.getDisplays();
            for (Display display : displays) {
                systemEDID.add("Display: " + display.getEdid());
            }
            if (!systemEDID.isEmpty()) {
                systemEDID.add("No displays");
            }
            return systemEDID.copy();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static KList<String> getInterfaces() {
        try {
            KList<String> interfaces = new KList<>();
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(networkInterfaces)) {
                interfaces.add(C.BLUE + "Display Name: %s", ni.getDisplayName());
                Enumeration<InetAddress> inetAddresses = ni.getInetAddresses();
                for (InetAddress ia : Collections.list(inetAddresses)) {
                    interfaces.add("IP: %s", ia.getHostAddress());
                }
                return interfaces.copy();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
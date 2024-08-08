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

import com.volmit.iris.Iris;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import oshi.SystemInfo;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class Hastebin {

    public static void enviornment(CommandSender sender) {
        // Construct the server information
        StringBuilder sb = new StringBuilder();
        SystemInfo systemInfo = new SystemInfo();
        KList<String> disks = new KList<>(getHardware.getDisk());
        KList<String> interfaces = new KList<>(getHardware.getInterfaces());
        KList<String> displays = new KList<>(getHardware.getEDID());
        KList<String> sensors = new KList<>(getHardware.getSensors());
        KList<String> gpus = new KList<>(getHardware.getGraphicsCards());
        KList<String> powersources = new KList<>(getHardware.getPowerSources());

        KList<World> IrisWorlds = new KList<>();
        KList<World> BukkitWorlds = new KList<>();

        for (World w : Bukkit.getServer().getWorlds()) {
            try {
                Engine engine = IrisToolbelt.access(w).getEngine();
                if (engine != null) {
                    IrisWorlds.add(w);
                }
            } catch (Exception e) {
                BukkitWorlds.add(w);
            }
        }

        sb.append(" -- == Iris Info == -- \n");
        sb.append("Iris Version Version: ").append(Iris.instance.getDescription().getVersion()).append("\n");
        sb.append("- Iris Worlds");
        for (World w : IrisWorlds.copy()) {
            sb.append(" - ").append(w.getName());
        }
        sb.append("- Bukkit Worlds");
        for (World w : BukkitWorlds.copy()) {
            sb.append(" - ").append(w.getName());
        }
        sb.append(" -- == Platform Overview == -- " + "\n");
        sb.append("Server Type: ").append(Bukkit.getVersion()).append("\n");
        sb.append("Server Uptime: ").append(Form.stampTime(systemInfo.getOperatingSystem().getSystemUptime())).append("\n");
        sb.append("Version: ").append(Platform.getVersion()).append(" - Platform: ").append(Platform.getName()).append("\n");
        sb.append("Java Vendor: ").append(Platform.ENVIRONMENT.getJavaVendor()).append(" - Java Version: ").append(Platform.ENVIRONMENT.getJavaVersion()).append("\n");
        sb.append(" -- == Processor Overview == -- " + "\n");
        sb.append("CPU Model: ").append(getHardware.getCPUModel());
        sb.append("CPU Architecture: ").append(Platform.CPU.getArchitecture()).append(" Available Processors: ").append(Platform.CPU.getAvailableProcessors()).append("\n");
        sb.append("CPU Load: ").append(Form.pc(Platform.CPU.getCPULoad())).append(" CPU Live Process Load: ").append(Form.pc(Platform.CPU.getLiveProcessCPULoad())).append("\n");
        sb.append("-=" + " Graphics " + "=- " + "\n");
        for (String gpu : gpus) {
            sb.append(" ").append(gpu).append("\n");
        }
        sb.append(" -- == Memory Information == -- " + "\n");
        sb.append("Physical Memory - Total: ").append(Form.memSize(Platform.MEMORY.PHYSICAL.getTotalMemory())).append(" Free: ").append(Form.memSize(Platform.MEMORY.PHYSICAL.getFreeMemory())).append(" Used: ").append(Form.memSize(Platform.MEMORY.PHYSICAL.getUsedMemory())).append("\n");
        sb.append("Virtual Memory - Total: ").append(Form.memSize(Platform.MEMORY.VIRTUAL.getTotalMemory())).append(" Free: ").append(Form.memSize(Platform.MEMORY.VIRTUAL.getFreeMemory())).append(" Used: ").append(Form.memSize(Platform.MEMORY.VIRTUAL.getUsedMemory())).append("\n");
        sb.append(" -- == Storage Information == -- " + "\n");
        for (String disk : disks) {
            sb.append(" ").append(sb.append(disk)).append("\n");
        }
        sb.append(" -- == Interface Information == -- " + "\n");
        for (String inter : interfaces) {
            sb.append(" ").append(inter).append("\n");
        }
        sb.append(" -- == Display Information == -- " + "\n");
        for (String display : displays) {
            sb.append(display).append("\n");
        }
        sb.append(" -- == Sensor Information == -- " + "\n");
        for (String sensor : sensors) {
            sb.append(" ").append(sensor).append("\n");
        }
        sb.append(" -- == Power Information == -- " + "\n");
        for (String power : powersources) {
            sb.append(" ").append(power).append("\n");
        }

        try {
            String hastebinUrl = uploadToHastebin(sb.toString());

            // Create the clickable message
            TextComponent message = new TextComponent("[Link]");
            TextComponent link = new TextComponent(hastebinUrl);
            link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, hastebinUrl));
            message.addExtra(link);

            // Send the clickable message to the player
            sender.spigot().sendMessage(message);
        } catch (Exception e) {
            sender.sendMessage(C.DARK_RED + "Failed to upload server information to Hastebin.");
        }
    }

    private static String uploadToHastebin(String content) throws Exception {
        URL url = new URL("https://paste.bytecode.ninja/documents");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "text/plain");
        conn.setDoOutput(true);

        DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
        wr.writeBytes(content);
        wr.flush();
        wr.close();

        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String response = br.readLine();
        br.close();

        return "https://paste.bytecode.ninja/" + response.split("\"")[3];
    }


}
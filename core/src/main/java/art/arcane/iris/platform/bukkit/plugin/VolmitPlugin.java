/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.platform.bukkit.plugin;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.io.IO;
import art.arcane.iris.world.task.J;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

@SuppressWarnings("EmptyMethod")
public abstract class VolmitPlugin extends JavaPlugin implements Listener {
    public static final boolean bad = false;
    private final KList<Runnable> postShutdown = new KList<>();

    public File getJarFile() {
        return getFile();
    }

    public void postShutdown(Runnable r) {
        postShutdown.add(r);
    }

    protected void runPostShutdown() {
        postShutdown.forEach(Runnable::run);
    }

    public void l(Object l) {
        IrisLogging.info(String.valueOf(l));
    }

    public void w(Object l) {
        IrisLogging.warn(String.valueOf(l));
    }

    public void f(Object l) {
        IrisLogging.error(String.valueOf(l));
    }

    public void v(Object l) {
        IrisLogging.debug(String.valueOf(l));
    }

    public void onEnable() {
        J.a(this::outputInfo);
        registerListener(this);
        start();
    }

    public void unregisterAll() {
        unregisterListeners();
    }

    private void outputInfo() {
        try {
            IO.delete(getDataFolder("info"));
            getDataFolder("info").mkdirs();
            outputPluginInfo();
        } catch (Throwable e) {
            IrisLogging.reportError(e);

        }
    }

    private void outputPluginInfo() throws IOException {
        FileConfiguration fc = new YamlConfiguration();
        fc.set("version", getDescription().getVersion());
        fc.set("name", getDescription().getName());
        fc.save(getDataFile("info", "plugin.yml"));
    }

    @Override
    public void onDisable() {
        stop();
        J.cancelPluginTasks();
        unregisterListener(this);
        unregisterAll();
    }

    public String getTag() {
        if (bad) {
            return "";
        }
        return getTag("");
    }

    public void registerListener(Listener l) {
        IrisLogging.debug("Register Listener " + l.getClass().getSimpleName());
        Bukkit.getPluginManager().registerEvents(l, this);
    }

    public void unregisterListener(Listener l) {
        IrisLogging.debug("Register Listener " + l.getClass().getSimpleName());
        HandlerList.unregisterAll(l);
    }

    public void unregisterListeners() {
        if (bad) {
            return;
        }
        HandlerList.unregisterAll((Listener) this);
    }

    public File getDataFile(String... strings) {
        File f = new File(getDataFolder(), new KList<>(strings).toString(File.separator));
        f.getParentFile().mkdirs();
        return f;
    }

    public File getDataFileList(String pre, String[] strings) {
        KList<String> v = new KList<>(strings);
        v.add(0, pre);
        File f = new File(getDataFolder(), v.toString(File.separator));
        f.getParentFile().mkdirs();
        return f;
    }

    public File getDataFolder(String... strings) {
        if (strings.length == 0) {
            return super.getDataFolder();
        }

        File f = new File(getDataFolder(), new KList<>(strings).toString(File.separator));
        f.mkdirs();

        return f;
    }

    public File getDataFolderNoCreate(String... strings) {
        if (strings.length == 0) {
            return super.getDataFolder();
        }

        File f = new File(getDataFolder(), new KList<>(strings).toString(File.separator));

        return f;
    }

    public File getDataFolderList(String pre, String[] strings) {
        KList<String> v = new KList<>(strings);
        v.add(0, pre);
        if (v.size() == 0) {
            return super.getDataFolder();
        }
        File f = new File(getDataFolder(), v.toString(File.separator));
        f.mkdirs();

        return f;
    }

    public abstract void start();

    public abstract void stop();

    public abstract String getTag(String subTag);
}

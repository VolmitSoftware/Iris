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

package com.volmit.iris.util.plugin;

import com.volmit.iris.Iris;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.reflect.V;
import com.volmit.iris.util.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@SuppressWarnings("EmptyMethod")
public abstract class VolmitPlugin extends JavaPlugin implements Listener {
    public static final boolean bad = false;
    private KMap<KList<String>, VirtualCommand> commands;
    private KList<MortarCommand> commandCache;
    private KList<MortarPermission> permissionCache;

    public File getJarFile() {
        return getFile();
    }

    public void l(Object l) {
        Iris.info("[" + getName() + "]: " + l);
    }

    public void w(Object l) {
        Iris.warn("[" + getName() + "]: " + l);
    }

    public void f(Object l) {
        Iris.error("[" + getName() + "]: " + l);
    }

    public void v(Object l) {
        Iris.verbose("[" + getName() + "]: " + l);
    }

    public void onEnable() {
        registerInstance();
        registerPermissions();
        registerCommands();
        J.a(this::outputInfo);
        registerListener(this);
        start();
    }

    public void unregisterAll() {
        unregisterListeners();
        unregisterCommands();
        unregisterPermissions();
        unregisterInstance();
    }

    private void outputInfo() {
        try {
            IO.delete(getDataFolder("info"));
            getDataFolder("info").mkdirs();
            outputPluginInfo();
            outputCommandInfo();
            outputPermissionInfo();
        } catch (Throwable e) {
            Iris.reportError(e);

        }
    }

    private void outputPermissionInfo() throws IOException {
        FileConfiguration fc = new YamlConfiguration();

        for (MortarPermission i : permissionCache) {
            chain(i, fc);
        }

        fc.save(getDataFile("info", "permissions.yml"));
    }

    private void chain(MortarPermission i, FileConfiguration fc) {
        KList<String> ff = new KList<>();

        for (MortarPermission j : i.getChildren()) {
            ff.add(j.getFullNode());
        }

        fc.set(i.getFullNode().replaceAll("\\Q.\\E", ",") + "." + "description", i.getDescription());
        fc.set(i.getFullNode().replaceAll("\\Q.\\E", ",") + "." + "default", i.isDefault());
        fc.set(i.getFullNode().replaceAll("\\Q.\\E", ",") + "." + "children", ff);

        for (MortarPermission j : i.getChildren()) {
            chain(j, fc);
        }
    }

    private void outputCommandInfo() throws IOException {
        FileConfiguration fc = new YamlConfiguration();

        for (MortarCommand i : commandCache) {
            chain(i, "/", fc);
        }

        fc.save(getDataFile("info", "commands.yml"));
    }

    private void chain(MortarCommand i, String c, FileConfiguration fc) {
        String n = c + (c.length() == 1 ? "" : " ") + i.getNode();
        fc.set(n + "." + "description", i.getDescription());
        fc.set(n + "." + "required-permissions", i.getRequiredPermissions());
        fc.set(n + "." + "aliases", i.getAllNodes());

        for (MortarCommand j : i.getChildren()) {
            chain(j, n, fc);
        }
    }

    private void outputPluginInfo() throws IOException {
        FileConfiguration fc = new YamlConfiguration();
        fc.set("version", getDescription().getVersion());
        fc.set("name", getDescription().getName());
        fc.save(getDataFile("info", "plugin.yml"));
    }

    private void registerPermissions() {
        permissionCache = new KList<>();

        for (Field i : getClass().getDeclaredFields()) {
            if (i.isAnnotationPresent(Permission.class)) {
                try {
                    i.setAccessible(true);
                    MortarPermission pc = (MortarPermission) i.getType().getConstructor().newInstance();
                    i.set(Modifier.isStatic(i.getModifiers()) ? null : this, pc);
                    registerPermission(pc);
                    permissionCache.add(pc);
                    v("Registered Permissions " + pc.getFullNode() + " (" + i.getName() + ")");
                } catch (IllegalArgumentException | IllegalAccessException | InstantiationException |
                         InvocationTargetException | NoSuchMethodException | SecurityException e) {
                    Iris.reportError(e);
                    w("Failed to register permission (field " + i.getName() + ")");
                    e.printStackTrace();
                }
            }
        }

        for (org.bukkit.permissions.Permission i : computePermissions()) {
            try {
                Bukkit.getPluginManager().addPermission(i);
            } catch (Throwable e) {
                Iris.reportError(e);

            }
        }
    }

    private KList<org.bukkit.permissions.Permission> computePermissions() {
        KList<org.bukkit.permissions.Permission> g = new KList<>();
        for (Field i : getClass().getDeclaredFields()) {
            if (i.isAnnotationPresent(Permission.class)) {
                try {
                    MortarPermission x = (MortarPermission) i.get(Modifier.isStatic(i.getModifiers()) ? null : this);
                    g.add(toPermission(x));
                    g.addAll(computePermissions(x));
                } catch (IllegalArgumentException | IllegalAccessException | SecurityException e) {
                    Iris.reportError(e);
                    e.printStackTrace();
                }
            }
        }

        return g.removeDuplicates();
    }

    private KList<org.bukkit.permissions.Permission> computePermissions(MortarPermission p) {
        KList<org.bukkit.permissions.Permission> g = new KList<>();

        if (p == null) {
            return g;
        }

        for (MortarPermission i : p.getChildren()) {
            if (i == null) {
                continue;
            }

            g.add(toPermission(i));
            g.addAll(computePermissions(i));
        }

        return g;
    }

    private org.bukkit.permissions.Permission toPermission(MortarPermission p) {
        if (p == null) {
            return null;
        }

        org.bukkit.permissions.Permission perm = new org.bukkit.permissions.Permission(p.getFullNode() + (p.hasParent() ? "" : ".*"));
        perm.setDescription(p.getDescription() == null ? "" : p.getDescription());
        perm.setDefault(p.isDefault() ? PermissionDefault.TRUE : PermissionDefault.OP);

        for (MortarPermission i : p.getChildren()) {
            perm.getChildren().put(i.getFullNode(), true);
        }

        return perm;
    }

    private void registerPermission(MortarPermission pc) {

    }

    @Override
    public void onDisable() {
        stop();
        Bukkit.getScheduler().cancelTasks(this);
        unregisterListener(this);
        unregisterAll();
    }

    private void tickController(IController i) {
        if (bad) {
            return;
        }

        if (i.getTickInterval() < 0) {
            return;
        }

        M.tick++;
        if (M.interval(i.getTickInterval())) {
            try {
                i.tick();
            } catch (Throwable e) {
                w("Failed to tick controller " + i.getName());
                e.printStackTrace();
                Iris.reportError(e);
            }
        }
    }

    private void registerInstance() {
        if (bad) {
            return;
        }
        for (Field i : getClass().getDeclaredFields()) {
            if (i.isAnnotationPresent(Instance.class)) {
                try {
                    i.setAccessible(true);
                    i.set(Modifier.isStatic(i.getModifiers()) ? null : this, this);
                    v("Registered Instance " + i.getName());
                } catch (IllegalArgumentException | IllegalAccessException | SecurityException e) {
                    w("Failed to register instance (field " + i.getName() + ")");
                    e.printStackTrace();
                    Iris.reportError(e);
                }
            }
        }
    }

    private void unregisterInstance() {
        if (bad) {
            return;
        }
        for (Field i : getClass().getDeclaredFields()) {
            if (i.isAnnotationPresent(Instance.class)) {
                try {
                    i.setAccessible(true);
                    i.set(Modifier.isStatic(i.getModifiers()) ? null : this, null);
                    v("Unregistered Instance " + i.getName());
                } catch (IllegalArgumentException | IllegalAccessException | SecurityException e) {
                    w("Failed to unregister instance (field " + i.getName() + ")");
                    e.printStackTrace();
                    Iris.reportError(e);
                }
            }
        }
    }

    private void registerCommands() {
        if (bad) {
            return;
        }
        commands = new KMap<>();
        commandCache = new KList<>();

        for (Field i : getClass().getDeclaredFields()) {
            if (i.isAnnotationPresent(com.volmit.iris.util.plugin.Command.class)) {
                try {
                    i.setAccessible(true);
                    MortarCommand pc = (MortarCommand) i.getType().getConstructor().newInstance();
                    com.volmit.iris.util.plugin.Command c = i.getAnnotation(com.volmit.iris.util.plugin.Command.class);
                    registerCommand(pc, c.value());
                    commandCache.add(pc);
                    v("Registered Commands /" + pc.getNode() + " (" + i.getName() + ")");
                } catch (IllegalArgumentException | IllegalAccessException | InstantiationException |
                         InvocationTargetException | NoSuchMethodException | SecurityException e) {
                    w("Failed to register command (field " + i.getName() + ")");
                    e.printStackTrace();
                    Iris.reportError(e);
                }
            }
        }
    }


    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        KList<String> chain = new KList<>();

        for (String i : args) {
            if (i.trim().isEmpty()) {
                continue;
            }

            chain.add(i.trim());
        }

        for (KList<String> i : commands.k()) {
            for (String j : i) {
                if (j.equalsIgnoreCase(alias)) {
                    VirtualCommand cmd = commands.get(i);

                    List<String> v = cmd.hitTab(sender, chain.copy(), alias);
                    if (v != null) {
                        return v;
                    }
                }
            }
        }

        return super.onTabComplete(sender, command, alias, args);
    }

    @Override
    public boolean onCommand(CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (bad) {
            return false;
        }

        KList<String> chain = new KList<>();
        chain.add(args);

        for (KList<String> i : commands.k()) {
            for (String j : i) {
                if (j.equalsIgnoreCase(label)) {
                    VirtualCommand cmd = commands.get(i);

                    if (cmd.hit(sender, chain.copy(), label)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public void registerCommand(ICommand cmd) {
        registerCommand(cmd, "");
    }

    public void registerCommand(ICommand cmd, String subTag) {
        if (bad) {
            return;
        }

        commands.put(cmd.getAllNodes(), new VirtualCommand(cmd, subTag.trim().isEmpty() ? getTag() : getTag(subTag.trim())));
        PluginCommand cc = getCommand(cmd.getNode().toLowerCase());

        if (cc != null) {
            cc.setExecutor(this);
            cc.setUsage(getName() + ":" + getClass().toString() + ":" + cmd.getNode());
        } else {
            RouterCommand r = new RouterCommand(cmd, this);
            r.setUsage(getName() + ":" + getClass().toString());
            ((CommandMap) new V(Bukkit.getServer()).get("commandMap")).register("", r);
        }
    }

    public void unregisterCommand(ICommand cmd) {
        if (bad) {
            return;
        }
        try {
            SimpleCommandMap m = new V(Bukkit.getServer()).get("commandMap");

            Map<String, Command> k = new V(m).get("knownCommands");

            for (Iterator<Map.Entry<String, Command>> it = k.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Command> entry = it.next();
                if (entry.getValue() instanceof Command) {
                    org.bukkit.command.Command c = entry.getValue();
                    String u = c.getUsage();

                    if (u != null && u.equals(getName() + ":" + getClass().toString() + ":" + cmd.getNode())) {
                        if (c.unregister(m)) {
                            it.remove();
                            v("Unregistered Command /" + cmd.getNode());
                        } else {
                            Bukkit.getConsoleSender().sendMessage(getTag() + "Failed to unregister command " + c.getName());
                        }
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
            Iris.reportError(e);
        }
    }

    public String getTag() {
        if (bad) {
            return "";
        }
        return getTag("");
    }

    public void registerListener(Listener l) {
        Iris.debug("Register Listener " + l.getClass().getSimpleName());
        Bukkit.getPluginManager().registerEvents(l, this);
    }

    public void unregisterListener(Listener l) {
        Iris.debug("Register Listener " + l.getClass().getSimpleName());
        HandlerList.unregisterAll(l);
    }

    public void unregisterListeners() {
        if (bad) {
            return;
        }
        HandlerList.unregisterAll((Listener) this);
    }

    public void unregisterCommands() {
        if (bad) {
            return;
        }
        for (VirtualCommand i : commands.v()) {
            try {
                unregisterCommand(i.getCommand());
            } catch (Throwable e) {
                Iris.reportError(e);

            }
        }
    }

    private void unregisterPermissions() {
        if (bad) {
            return;
        }
        for (org.bukkit.permissions.Permission i : computePermissions()) {
            Bukkit.getPluginManager().removePermission(i);
            v("Unregistered Permission " + i.getName());
        }
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

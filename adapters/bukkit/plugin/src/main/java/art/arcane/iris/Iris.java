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

package art.arcane.iris;

import art.arcane.iris.platform.bukkit.plugin.VolmitPlugin;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.volmlib.integration.ReloadAware;
import art.arcane.volmlib.util.function.NastyRunnable;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;

import java.util.List;
import java.util.concurrent.CompletionStage;

@SuppressWarnings("CanBeFinal")
public class Iris extends VolmitPlugin implements Listener, ReloadAware {
    public static Iris instance;
    private final IrisPluginRuntime runtime;

    public Iris() {
        runtime = IrisPluginRuntime.create(this);
        runtime.initialize();
    }

    IrisPluginRuntime runtime() {
        return runtime;
    }

    void enablePlugin() {
        super.onEnable();
    }

    void disablePlugin() {
        super.onDisable();
    }

    public static VolmitSender getSender() {
        return IrisPluginRuntime.getSender();
    }

    public static <T> T service(Class<T> type) {
        return IrisPluginRuntime.service(type);
    }

    public static void callEvent(Event event) {
        IrisPluginRuntime.callEvent(event);
    }

    public static void msg(String message) {
        IrisPluginRuntime.msg(message);
    }

    public static void warn(String format, Object... args) {
        IrisPluginRuntime.warn(format, args);
    }

    public static void error(String format, Object... args) {
        IrisPluginRuntime.error(format, args);
    }

    public static void debug(String message) {
        IrisPluginRuntime.debug(message);
    }

    public static void debug(String category, int line, String message) {
        IrisPluginRuntime.debug(category, line, message);
    }

    public static void verbose(String message) {
        IrisPluginRuntime.verbose(message);
    }

    public static void success(String message) {
        IrisPluginRuntime.success(message);
    }

    public static void info(String format, Object... args) {
        IrisPluginRuntime.info(format, args);
    }

    public static void later(NastyRunnable task) {
        IrisPluginRuntime.later(task);
    }

    public static void reportErrorChunk(int x, int z, Throwable failure, String extra) {
        IrisPluginRuntime.reportErrorChunk(x, z, failure, extra);
    }

    public static void reportError(Throwable failure) {
        IrisPluginRuntime.reportError(failure);
    }

    public static void reportError(String context, Throwable failure) {
        IrisPluginRuntime.reportError(context, failure);
    }

    public static void dump() {
        IrisPluginRuntime.dump();
    }

    public static void panic() {
        IrisPluginRuntime.panic();
    }

    @Override
    public void onEnable() {
        runtime.onEnable();
    }

    @Override
    public void onDisable() {
        runtime.onDisable();
    }

    @Override
    public CompletionStage<Void> commitReload(ReloadAware.PreUnloadReason reason) {
        return runtime.commitReload(reason);
    }

    public void selectLanguage(CommandSender sender, String[] arguments) {
        runtime.selectLanguage(sender, arguments);
    }

    public List<String> completeLanguage(CommandSender sender, String[] arguments) {
        return runtime.completeLanguage(sender, arguments);
    }

    @EventHandler
    public void onPlaceholderApiEnable(PluginEnableEvent event) {
        runtime.onPlaceholderApiEnable(event);
    }

    @EventHandler
    public void onPlaceholderApiDisable(PluginDisableEvent event) {
        runtime.onPlaceholderApiDisable(event);
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public String getTag(String subTag) {
        return IrisPluginRuntime.tag(subTag);
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(String worldName, String id) {
        return runtime.getDefaultBiomeProvider(worldName, id);
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return runtime.getDefaultWorldGenerator(worldName, id);
    }

    public ChunkGenerator requireWorldGenerator(String worldName, String id) {
        return runtime.requireWorldGenerator(worldName, id);
    }
}

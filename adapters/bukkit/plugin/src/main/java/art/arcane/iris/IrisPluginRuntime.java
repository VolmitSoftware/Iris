package art.arcane.iris;

import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.volmlib.integration.ReloadAware;
import art.arcane.volmlib.util.function.NastyRunnable;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;

import java.util.List;
import java.util.concurrent.CompletionStage;

interface IrisPluginRuntime {
    static IrisPluginRuntime create(Iris plugin) {
        return new IrisRuntime(plugin);
    }

    static void bootstrap(BootstrapContext context) {
        IrisBootstrapSequence.bootstrap(context);
    }

    static VolmitSender getSender() {
        return IrisRuntime.getSender();
    }

    static <T> T service(Class<T> type) {
        return IrisRuntime.service(type);
    }

    static void callEvent(Event event) {
        IrisRuntime.callEvent(event);
    }

    static void msg(String message) {
        IrisRuntime.msg(message);
    }

    static void warn(String format, Object... args) {
        IrisRuntime.warn(format, args);
    }

    static void error(String format, Object... args) {
        IrisRuntime.error(format, args);
    }

    static void debug(String message) {
        IrisRuntime.debug(message);
    }

    static void debug(String category, int line, String message) {
        IrisRuntime.debug(category, line, message);
    }

    static void verbose(String message) {
        IrisRuntime.verbose(message);
    }

    static void success(String message) {
        IrisRuntime.success(message);
    }

    static void info(String format, Object... args) {
        IrisRuntime.info(format, args);
    }

    static void later(NastyRunnable task) {
        IrisRuntime.later(task);
    }

    static void reportErrorChunk(int x, int z, Throwable failure, String extra) {
        IrisRuntime.reportErrorChunk(x, z, failure, extra);
    }

    static void reportError(Throwable failure) {
        IrisRuntime.reportError(failure);
    }

    static void reportError(String context, Throwable failure) {
        IrisRuntime.reportError(context, failure);
    }

    static void dump() {
        IrisRuntime.dump();
    }

    static void panic() {
        IrisRuntime.panic();
    }

    static String tag(String subTag) {
        return IrisRuntime.tag(subTag);
    }

    void initialize();

    void onEnable();

    void onDisable();

    CompletionStage<Void> commitReload(ReloadAware.PreUnloadReason reason);

    void selectLanguage(CommandSender sender, String[] arguments);

    List<String> completeLanguage(CommandSender sender, String[] arguments);

    void onPlaceholderApiEnable(PluginEnableEvent event);

    void onPlaceholderApiDisable(PluginDisableEvent event);

    BiomeProvider getDefaultBiomeProvider(String worldName, String id);

    ChunkGenerator getDefaultWorldGenerator(String worldName, String id);

    ChunkGenerator requireWorldGenerator(String worldName, String id);
}

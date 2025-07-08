package com.volmit.iris.util.misc;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.core.safeguard.IrisSafeguard;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.platform.PlatformChunkGenerator;
import com.volmit.iris.util.context.IrisContext;
import com.volmit.iris.util.json.JSONException;
import com.volmit.iris.util.reflect.ShadeFix;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.sentry.Attachments;
import com.volmit.iris.util.sentry.IrisLogger;
import com.volmit.iris.util.sentry.ServerID;
import io.sentry.Sentry;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.serializer.ComponentSerializer;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.DrilldownPie;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import oshi.SystemInfo;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class Bindings {

    public static void capture(Throwable throwable) {
        Sentry.captureException(throwable);
    }

    public static void setupSentry() {
        var settings = IrisSettings.get().getSentry();
        if (settings.disableAutoReporting || Sentry.isEnabled() || Boolean.getBoolean("iris.suppressReporting")) return;
        Iris.info("Enabling Sentry for anonymous error reporting. You can disable this in the settings.");
        Iris.info("Your server ID is: " + ServerID.ID);
        Sentry.init(options -> {
            options.setDsn("https://b16ecc222e9c1e0c48faecacb906fd89@o4509451052646400.ingest.de.sentry.io/4509452722765904");
            if (settings.debug) {
                options.setLogger(new IrisLogger());
                options.setDebug(true);
            }

            options.setAttachServerName(false);
            options.setEnableUncaughtExceptionHandler(false);
            options.setRelease(Iris.instance.getDescription().getVersion());
            options.setBeforeSend((event, hint) -> {
                if (suppress(event.getThrowable())) return null;
                event.setTag("iris.safeguard", IrisSafeguard.mode());
                event.setTag("iris.nms", INMS.get().getClass().getCanonicalName());
                var context = IrisContext.get();
                if (context != null) event.getContexts().set("engine", context.asContext());
                event.getContexts().set("safeguard", IrisSafeguard.asContext());
                return event;
            });
        });
        Sentry.configureScope(scope -> {
            if (settings.includeServerId) scope.setUser(ServerID.asUser());
            scope.addAttachment(Attachments.PLUGINS);
            scope.setTag("server", Bukkit.getVersion());
            scope.setTag("server.type", Bukkit.getName());
            scope.setTag("server.api", Bukkit.getBukkitVersion());
        });
        Runtime.getRuntime().addShutdownHook(new Thread(Sentry::close));
    }

    private static boolean suppress(Throwable e) {
        return (e instanceof IllegalStateException ex && "zip file closed".equals(ex.getMessage())) || e instanceof JSONException;
    }


    public static void setupBstats(Iris plugin) {
        J.s(() -> {
            var metrics = new Metrics(plugin, 24220);
            metrics.addCustomChart(new SingleLineChart("custom_dimensions", () -> Bukkit.getWorlds()
                    .stream()
                    .filter(IrisToolbelt::isIrisWorld)
                    .mapToInt(w -> 1)
                    .sum()));

            metrics.addCustomChart(new DrilldownPie("used_packs", () -> Bukkit.getWorlds().stream()
                    .map(IrisToolbelt::access)
                    .filter(Objects::nonNull)
                    .map(PlatformChunkGenerator::getEngine)
                    .collect(Collectors.toMap(engine -> engine.getDimension().getLoadKey(), engine -> {
                        var hash32 = engine.getHash32().getNow(null);
                        if (hash32 == null) return Map.of();
                        int version = engine.getDimension().getVersion();
                        String checksum = Long.toHexString(hash32);

                        return Map.of("v" + version + " (" + checksum + ")", 1);
                    }, (a, b) -> {
                        Map<String, Integer> merged = new HashMap<>(a);
                        b.forEach((k, v) -> merged.merge(k, v, Integer::sum));
                        return merged;
                    }))));


            var info = new SystemInfo().getHardware();
            var cpu = info.getProcessor().getProcessorIdentifier();
            var mem = info.getMemory();
            metrics.addCustomChart(new SimplePie("cpu_model", cpu::getName));

            var nf = NumberFormat.getInstance(Locale.ENGLISH);
            nf.setMinimumFractionDigits(0);
            nf.setMaximumFractionDigits(2);
            nf.setRoundingMode(RoundingMode.HALF_UP);

            metrics.addCustomChart(new DrilldownPie("memory", () -> {
                double total = mem.getTotal() * 1E-9;
                double alloc = Math.min(total, Runtime.getRuntime().maxMemory() * 1E-9);
                return Map.of(nf.format(alloc), Map.of(nf.format(total), 1));
            }));

            plugin.postShutdown(metrics::shutdown);
        });
    }

    public static class Adventure {
        private final BukkitAudiences audiences;

        public Adventure(Iris plugin) {
            ShadeFix.fix(ComponentSerializer.class);
            this.audiences = BukkitAudiences.create(plugin);
        }

        public Audience player(Player player) {
            return audiences.player(player);
        }

        public Audience sender(CommandSender sender) {
            return audiences.sender(sender);
        }
    }
}

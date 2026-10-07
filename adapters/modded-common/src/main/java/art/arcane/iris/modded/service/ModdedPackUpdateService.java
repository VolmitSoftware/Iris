package art.arcane.iris.modded.service;

import art.arcane.iris.diagnostics.splash.IrisSplashPackScanner;
import art.arcane.iris.diagnostics.splash.IrisSplashPackScanner.SplashPackMetadata;
import art.arcane.iris.pack.BuiltInPackUpdates;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolPlayer;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

public final class ModdedPackUpdateService implements ModdedTickableService {
    private final Function<File, CompletableFuture<PackCheck>> checker;
    private final Set<UUID> notified = new HashSet<>();
    private CompletableFuture<PackCheck> pending;
    private List<String> notices = List.of();
    private File packsFolder;
    private boolean enabled;
    private boolean started;

    public ModdedPackUpdateService() {
        this(ModdedPackUpdateService::checkPacks);
    }

    ModdedPackUpdateService(Function<File, CompletableFuture<PackCheck>> checker) {
        this.checker = checker;
    }

    @Override
    public void onEnable() {
        enabled = true;
    }

    @Override
    public void onDisable() {
        enabled = false;
        if (pending != null) {
            pending.cancel(true);
            pending = null;
        }
        started = false;
        notices = List.of();
        notified.clear();
        packsFolder = null;
    }

    @Override
    public void onServerTick(NativeModdedServer server) {
        if (!enabled || server == null || !server.hasPlayerList()) {
            return;
        }
        if (!started) {
            started = true;
            packsFolder = IrisPlatforms.get().packsFolderNoCreate().getAbsoluteFile();
            pending = checker.apply(packsFolder);
        }
        if (pending == null || !pending.isDone()) {
            return;
        }
        CompletableFuture<PackCheck> completed = pending;
        pending = null;
        try {
            publish(completed.join());
            server.forEachPlayer(this::notifyPlayer);
        } catch (CompletionException error) {
            IrisLogging.reportError("Iris pack update check failed", error);
        }
    }

    public void refresh() {
        if (!enabled) {
            return;
        }
        if (pending != null) {
            pending.cancel(true);
            pending = null;
        }
        started = false;
        notices = List.of();
        notified.clear();
    }

    public void notifyPlayer(NativeProtocolPlayer player) {
        if (!enabled || notices.isEmpty() || player == null || !player.connected()
                || (!player.isGameMaster() && !player.isServerOwner()) || !notified.add(player.id())) {
            return;
        }
        for (String notice : notices) {
            player.sendMessage(notice);
        }
    }

    private static CompletableFuture<PackCheck> checkPacks(File folder) {
        return CompletableFuture.supplyAsync(() -> IrisSplashPackScanner.collect(folder, IrisLogging::reportError))
                .thenCompose(packs -> BuiltInPackUpdates.check(packs.stream().map(SplashPackMetadata::name).toList())
                        .thenApply(updates -> new PackCheck(packs, updates)));
    }

    private void publish(PackCheck result) {
        List<String> updates = new ArrayList<>();
        IrisLogging.info("Iris packs: " + packsFolder);
        for (SplashPackMetadata pack : result.packs()) {
            BuiltInPackUpdates.Update update = result.updates().get(pack.name());
            String version = pack.version().matches("[0-9]+") ? "v" + pack.version() : "version unknown";
            IrisLogging.info("  " + pack.name() + " " + version
                    + (update == null ? "" : update.suffix(pack.version())));
            if (update != null && update.newerThan(pack.version())) {
                updates.add("Iris " + pack.name() + " release metadata is outdated: installed v" + pack.version()
                        + ", latest built-in release v" + update.version() + ".");
                updates.add("Back up and review custom edits in " + new File(packsFolder, pack.name())
                        + "; close Studio, then update with /iris download pack=" + pack.name() + " overwrite=true.");
                updates.add("Existing worlds retain their recorded production pack. Back up the complete world, then stage an update with /iris world update <dimension> "
                        + pack.name() + " and restart the server to activate it.");
            }
        }
        notices = List.copyOf(updates);
        for (String notice : notices) {
            IrisLogging.warn(notice);
        }
    }

    record PackCheck(List<SplashPackMetadata> packs, Map<String, BuiltInPackUpdates.Update> updates) {
        PackCheck {
            packs = List.copyOf(packs);
            updates = Map.copyOf(updates);
        }
    }
}

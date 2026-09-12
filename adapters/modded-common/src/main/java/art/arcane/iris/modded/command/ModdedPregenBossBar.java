package art.arcane.iris.modded.command;

import art.arcane.iris.studio.view.PregeneratorJob;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.RuntimeUiMessages;
import art.arcane.iris.platform.protocol.IrisProtocolServer;
import art.arcane.iris.platform.protocol.IrisSession;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.spi.protocol.IrisProtocol;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.UUID;

public final class ModdedPregenBossBar {
    private static final UUID BAR_ID = UUID.fromString("14150000-1e15-4a2b-9c3d-1115e9a1cafe");
    private static final int UPDATE_INTERVAL_TICKS = 10;

    private static volatile ServerBossEvent bar;
    private static volatile UUID viewer;
    private static int sinceUpdate;

    private ModdedPregenBossBar() {
    }

    public static synchronized void begin(ServerPlayer player) {
        clear();
        if (player == null) {
            return;
        }
        if (hasClientPregenHud(player)) {
            return;
        }
        viewer = player.getUUID();
        bar = new ServerBossEvent(
                BAR_ID,
                Component.literal(IrisLanguage.plain(RuntimeUiMessages.PREGEN_STARTING)),
                BossEvent.BossBarColor.GREEN,
                BossEvent.BossBarOverlay.PROGRESS
        );
        bar.setProgress(0.0F);
        bar.addPlayer(player);
        sinceUpdate = UPDATE_INTERVAL_TICKS;
    }

    public static void tick(MinecraftServer server) {
        ServerBossEvent active = bar;
        if (active == null) {
            return;
        }
        PregeneratorJob.PregenProgress progress = PregeneratorJob.progressSnapshot();
        if (progress == null) {
            clear();
            return;
        }
        if (++sinceUpdate < UPDATE_INTERVAL_TICKS) {
            return;
        }
        sinceUpdate = 0;
        reattach(server, active);
        active.setProgress((float) clamp01(progress.percent() / 100.0D));
        active.setColor(progress.paused() ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.GREEN);
        active.setName(nameFor(progress));
    }

    private static void reattach(MinecraftServer server, ServerBossEvent active) {
        UUID id = viewer;
        if (id == null || server == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player != null && !active.getPlayers().contains(player)) {
            active.addPlayer(player);
        }
    }

    private static Component nameFor(PregeneratorJob.PregenProgress progress) {
        TextKey message = progress.paused()
                ? RuntimeUiMessages.PREGEN_BOSSBAR_PAUSED
                : RuntimeUiMessages.PREGEN_BOSSBAR_RUNNING;
        if (progress.paused()) {
            return ModdedCommandFeedback.text(IrisLanguage.plain(
                    message,
                    MessageArgument.trusted("generated", Form.f(progress.generated())),
                    MessageArgument.trusted("total", Form.f(progress.totalChunks())),
                    MessageArgument.trusted("percent", String.format("%.1f", progress.percent()))
            ), ModdedCommandFeedback.DARK_GREEN);
        }
        String eta = progress.eta() > 0L
                ? IrisLanguage.plain(
                        RuntimeUiMessages.PREGEN_ETA_FRAGMENT,
                        MessageArgument.trusted("eta", Form.duration(progress.eta(), 1))
                )
                : "";
        String failed = progress.failed() > 0L
                ? IrisLanguage.plain(
                        RuntimeUiMessages.PREGEN_FAILED_FRAGMENT,
                        MessageArgument.trusted("failed", Form.f(progress.failed()))
                )
                : "";
        return ModdedCommandFeedback.text(IrisLanguage.plain(
                message,
                MessageArgument.trusted("generated", Form.f(progress.generated())),
                MessageArgument.trusted("total", Form.f(progress.totalChunks())),
                MessageArgument.trusted("percent", String.format("%.1f", progress.percent())),
                MessageArgument.trusted("speed", Form.f((int) progress.chunksPerSecond())),
                MessageArgument.trusted("eta", eta),
                MessageArgument.trusted("failed", failed)
        ), ModdedCommandFeedback.DARK_GREEN);
    }

    private static boolean hasClientPregenHud(ServerPlayer player) {
        IrisProtocolServer protocol = IrisServices.getOrNull(IrisProtocolServer.class);
        if (protocol == null) {
            return false;
        }
        IrisSession session = protocol.registry().get(player.getUUID().toString());
        return session != null && session.isReady() && session.hasCapability(IrisProtocol.CAPABILITY_PREGEN);
    }

    private static double clamp01(double value) {
        if (value < 0.0D) {
            return 0.0D;
        }
        if (value > 1.0D) {
            return 1.0D;
        }
        return value;
    }

    public static synchronized void clear() {
        ServerBossEvent existing = bar;
        if (existing != null) {
            existing.removeAllPlayers();
            existing.setVisible(false);
        }
        bar = null;
        viewer = null;
        sinceUpdate = 0;
    }
}

package art.arcane.iris.world;

import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.RuntimeProgressMessages;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class WorldReplacementProgress implements AutoCloseable {
    private static final int HEARTBEAT_INTERVAL_TICKS = 200;

    private final VolmitSender sender;
    private final String worldKey;
    private final Player player;
    private final UUID languageAudience;
    private final long startedAtNanos;
    private String detail;
    private int taskId = -1;
    private boolean firstTick = true;
    private boolean closed;

    private WorldReplacementProgress(VolmitSender sender, String worldKey) {
        this.sender = Objects.requireNonNull(sender, "sender");
        this.worldKey = Objects.requireNonNull(worldKey, "worldKey");
        player = sender.isPlayer() ? sender.player() : null;
        languageAudience = player == null ? LanguageAudience.current() : player.getUniqueId();
        startedAtNanos = System.nanoTime();
        detail = IrisLanguage.text(
                languageAudience,
                RuntimeProgressMessages.WORLD_CREATE_STAGE_INITIALIZING,
                MessageArgs.empty()
        );
    }

    public static WorldReplacementProgress start(VolmitSender sender, String worldKey) {
        WorldReplacementProgress progress = new WorldReplacementProgress(sender, worldKey);
        progress.send(RuntimeProgressMessages.WORLD_REPLACE_START);
        try {
            progress.taskId = J.ar(progress::heartbeat, HEARTBEAT_INTERVAL_TICKS);
        } catch (Throwable failure) {
            IrisLogging.reportError("Could not start world replacement progress updates for " + worldKey + ".", failure);
        }
        return progress;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (taskId >= 0) {
            try {
                J.car(taskId);
            } catch (Throwable failure) {
                IrisLogging.reportError("Could not cancel world replacement progress updates for " + worldKey + ".", failure);
            }
        }
    }

    public synchronized void stage(TextKey stage) {
        if (closed || stage == null) {
            return;
        }
        String detail = IrisLanguage.text(languageAudience, stage, MessageArgs.empty());
        if (detail.equals(this.detail)) {
            return;
        }
        this.detail = detail;
        send(RuntimeProgressMessages.WORLD_REPLACE_PROGRESS);
    }

    private synchronized void heartbeat() {
        if (closed) {
            return;
        }
        if (firstTick) {
            firstTick = false;
            return;
        }
        send(RuntimeProgressMessages.WORLD_REPLACE_PROGRESS);
    }

    private void send(MessageKey key) {
        try {
            MessageArgs.Builder arguments = MessageArgs.builder().untrusted("world", worldKey);
            if (key == RuntimeProgressMessages.WORLD_REPLACE_PROGRESS) {
                arguments.untrusted("stage", detail)
                        .untrusted("elapsed", TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startedAtNanos));
            }
            String message = IrisLanguage.text(languageAudience, key, arguments.build());
            if (player == null) {
                sender.sendMessage(message);
            } else {
                J.runEntity(player, () -> deliverToPlayer(message));
            }
        } catch (Throwable failure) {
            IrisLogging.reportError("Could not report world replacement progress for " + worldKey + ".", failure);
        }
    }

    private synchronized void deliverToPlayer(String message) {
        if (!closed) {
            sender.sendMessage(message);
        }
    }
}

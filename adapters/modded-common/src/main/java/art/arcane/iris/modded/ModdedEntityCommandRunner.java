package art.arcane.iris.modded;

import art.arcane.iris.core.content.ContentEntity;
import art.arcane.iris.spi.IrisLogging;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

final class ModdedEntityCommandRunner {
    private ModdedEntityCommandRunner() {
    }

    /** Schedules the commands, which a module already checked against the time and weather, at the block. */
    static void run(List<ContentEntity.Command> commands, ServerLevel level, int blockX, int blockY, int blockZ) {
        if (commands.isEmpty()) {
            return;
        }
        MinecraftServer server = level.getServer();
        ModdedScheduler scheduler = ModdedEngineBootstrap.schedulerOrNull();
        if (server == null || scheduler == null) {
            IrisLogging.error("Iris could not schedule entity commands because the modded server scheduler is unavailable.");
            return;
        }

        for (ContentEntity.Command command : commands) {
            schedule(command, server, scheduler, blockX, blockY, blockZ);
        }
    }

    private static void schedule(ContentEntity.Command command, MinecraftServer server, ModdedScheduler scheduler, int blockX, int blockY, int blockZ) {
        int delay = clampDelay(command.delay(), 0);
        int repeatDelay = clampDelay(command.repeatDelay(), 1);
        for (String raw : command.commands()) {
            String prepared = prepareCommand(raw, blockX, blockY, blockZ);
            if (prepared == null) {
                continue;
            }
            if (command.repeat()) {
                scheduler.laterGlobal(() -> new RepeatingCommand(server, scheduler, prepared, repeatDelay).run(), delay);
            } else {
                scheduler.laterGlobal(() -> ModdedServerCommands.dispatch(server, prepared), delay);
            }
        }
    }

    static String prepareCommand(String raw, int blockX, int blockY, int blockZ) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return (raw.startsWith("/") ? raw.substring(1) : raw)
                .replace("{x}", String.valueOf(blockX))
                .replace("{y}", String.valueOf(blockY))
                .replace("{z}", String.valueOf(blockZ));
    }

    static int clampDelay(long delay, int minimum) {
        return (int) Math.max(minimum, Math.min(Integer.MAX_VALUE, delay));
    }

    private static final class RepeatingCommand implements Runnable {
        private final MinecraftServer server;
        private final ModdedScheduler scheduler;
        private final String command;
        private final int interval;

        private RepeatingCommand(MinecraftServer server, ModdedScheduler scheduler, String command, int interval) {
            this.server = server;
            this.scheduler = scheduler;
            this.command = command;
            this.interval = interval;
        }

        @Override
        public void run() {
            ModdedServerCommands.dispatch(server, command);
            scheduler.laterGlobal(this, interval);
        }
    }
}

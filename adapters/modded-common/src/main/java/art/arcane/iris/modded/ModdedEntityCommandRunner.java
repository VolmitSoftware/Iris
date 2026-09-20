package art.arcane.iris.modded;

import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandExecutor;

import art.arcane.iris.command.IrisCommand;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.collection.KList;

final class ModdedEntityCommandRunner {
    private ModdedEntityCommandRunner() {
    }

    static void run(KList<IrisCommand> commands, NativeWorld world, int blockX, int blockY, int blockZ) {
        if (commands.isEmpty()) {
            return;
        }
        NativeCommandExecutor executor = NativeCommandExecutor.fromWorld(world, IrisLogging::reportError);
        ModdedScheduler scheduler = ModdedEngineBootstrap.schedulerOrNull();
        if (executor == null || scheduler == null) {
            IrisLogging.error("Iris could not schedule entity commands because the modded server scheduler is unavailable.");
            return;
        }

        for (IrisCommand command : commands) {
            if (command == null || !command.isValid(world)) {
                continue;
            }
            schedule(command, executor, scheduler, blockX, blockY, blockZ);
        }
    }

    private static void schedule(IrisCommand command, NativeCommandExecutor executor, ModdedScheduler scheduler, int blockX, int blockY, int blockZ) {
        int delay = clampDelay(command.getDelay(), 0);
        int repeatDelay = clampDelay(command.getRepeatDelay(), 1);
        for (String raw : command.getCommands()) {
            String prepared = prepareCommand(raw, blockX, blockY, blockZ);
            if (prepared == null) {
                continue;
            }
            if (command.isRepeat()) {
                scheduler.laterGlobal(() -> new RepeatingCommand(executor, scheduler, prepared, repeatDelay).run(), delay);
            } else {
                scheduler.laterGlobal(() -> executor.dispatch(prepared), delay);
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
        private final NativeCommandExecutor executor;
        private final ModdedScheduler scheduler;
        private final String command;
        private final int interval;

        private RepeatingCommand(NativeCommandExecutor executor, ModdedScheduler scheduler, String command, int interval) {
            this.executor = executor;
            this.scheduler = scheduler;
            this.command = command;
            this.interval = interval;
        }

        @Override
        public void run() {
            executor.dispatch(command);
            scheduler.laterGlobal(this, interval);
        }
    }
}

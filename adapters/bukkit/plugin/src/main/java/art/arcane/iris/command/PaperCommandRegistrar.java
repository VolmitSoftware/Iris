package art.arcane.iris.command;

import art.arcane.iris.Iris;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;

/**
 * Paper-only command registration. Lives outside {@code art.arcane.iris.generation.runtime} on purpose:
 * that package is eagerly Class.forName-scanned by JarScanner during enable, and resolving this
 * class on plain Spigot throws NoClassDefFoundError for the Paper lifecycle types. CommandSVC
 * loads it reflectively only when the Bukkit plugin.yml command is unavailable (Paper path).
 */
public final class PaperCommandRegistrar {
    private static final String ROOT_COMMAND = "iris";

    private PaperCommandRegistrar() {
    }

    public static void register(Iris plugin, CommandSVC commandService) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(
                        ROOT_COMMAND,
                        "Iris world generation command.",
                        List.of("ir", "irs"),
                        command(commandService)
                )
        );
    }

    static BasicCommand command(CommandSVC commandService) {
        return new PaperCommand(commandService);
    }

    private record PaperCommand(CommandSVC commandService) implements BasicCommand {
        @Override
        public void execute(CommandSourceStack source, String[] args) {
            commandService.executeRoot(source.getSender(), ROOT_COMMAND, args);
        }

        @Override
        public Collection<String> suggest(CommandSourceStack source, String[] args) {
            return commandService.tabCompleteRoot(source.getSender(), ROOT_COMMAND, args);
        }

        @Override
        public boolean canUse(CommandSender sender) {
            return true;
        }
    }
}

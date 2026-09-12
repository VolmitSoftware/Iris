package art.arcane.iris.command;

import art.arcane.iris.Iris;
import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.localization.BukkitCommandMessagesExtended;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.command.DirectorExecutor;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import org.bukkit.command.CommandSender;

@Director(name = "debug", description = "Iris diagnostic tools", descriptionKey = "iris.director.commanddebug.description")
public final class CommandDebug implements DirectorExecutor {
    @Director(name = "dump", sync = true, description = "Create and optionally upload a diagnostic report", descriptionKey = "iris.director.commandiris.director.debugdump")
    public void dump(
            @Param(name = "upload", defaultValue = "true", description = "Upload the report to mclo.gs", descriptionKey = "iris.director.commandiris.param.debugdump_upload") boolean upload,
            @Param(name = "sender", contextual = true) CommandSender sender
    ) {
        Iris.instance.debugDump().request(sender, upload);
    }

    @Director(sync = true, description = "Print version information", descriptionKey = "iris.director.commandiris.director.print_version_information")
    public void version(@Param(name = "sender", contextual = true) CommandSender sender) {
        ComponentMessenger.sendMarkup(sender, DirectorMiniMenu.version(
                "Iris", Iris.instance.getDescription().getVersion(), DirectorMiniMenu.Theme.irisGreen()));
    }

    @Director(description = "Toggle debug", descriptionKey = "iris.director.commandiris.director.toggle_debug")
    public void toggle() {
        boolean enabled = !IrisSettings.get().getGeneral().isDebug();
        IrisSettings.get().getGeneral().setDebug(enabled);
        IrisSettings.get().forceSave();
        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_IRIS_SET_DEBUG,
                MessageArgument.untrusted("to", enabled)));
    }
}

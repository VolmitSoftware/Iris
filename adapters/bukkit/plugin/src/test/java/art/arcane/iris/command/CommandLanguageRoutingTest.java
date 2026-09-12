package art.arcane.iris.command;

import art.arcane.iris.Iris;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CommandLanguageRoutingTest {
    private Iris previousPlugin;
    private Iris plugin;

    @Before
    public void installPlugin() {
        previousPlugin = Iris.instance;
        plugin = mock(Iris.class);
        Iris.instance = plugin;
    }

    @After
    public void restorePlugin() {
        Iris.instance = previousPlugin;
    }

    @Test
    public void forwardsEveryLanguageArgumentBeforeCheckingTheAdminRootPermission() {
        Player player = mock(Player.class);
        String[] arguments = {"language", "self", "de_DE"};

        new CommandSVC().executeRoot(player, "iris", arguments);

        verify(plugin).selectLanguage(player, new String[]{"self", "de_DE"});
        verify(player, never()).hasPermission("iris.all");
    }

    @Test
    public void forwardsLanguageTabArgumentsToTheSharedCompletionService() {
        CommandSender sender = mock(CommandSender.class);
        String[] arguments = {"language", "self", "de"};
        String[] languageArguments = {"self", "de"};
        when(plugin.completeLanguage(sender, languageArguments)).thenReturn(List.of("de_DE"));

        assertEquals(List.of("de_DE"), new CommandSVC().tabCompleteRoot(sender, "iris", arguments));
        verify(plugin).completeLanguage(sender, languageArguments);
    }
}

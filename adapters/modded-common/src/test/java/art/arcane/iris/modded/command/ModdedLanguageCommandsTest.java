package art.arcane.iris.modded.command;

import art.arcane.iris.localization.IrisLanguage;
import art.arcane.volmlib.util.director.help.DirectorHelpMessages;
import art.arcane.volmlib.util.localization.PluginLanguageService;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class ModdedLanguageCommandsTest {
    @Test
    public void personalFailureReportsTheCommittedEnglishFallback() throws Exception {
        assertPersonalCompletion("de_DE", "en_US", "Iris: de_DE unavailable; using English (en_US).");
    }

    @Test
    public void personalResetReportsTheInheritedLanguage() throws Exception {
        assertPersonalCompletion("reset", "fr_FR", "Language updated: fr_FR");
    }

    @Test
    public void personalSuccessReportsTheSelectedLanguage() throws Exception {
        assertPersonalCompletion("de_DE", "de_DE", "Language updated: de_DE");
    }

    @Test
    public void pickerRendersTheNativeHelpFrameAndLanguageActions() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        CommandSourceStack source = mock(CommandSourceStack.class, RETURNS_DEEP_STUBS);
        when(source.getPlayer()).thenReturn(null);
        List<Component> rendered = new ArrayList<>();
        doAnswer(invocation -> {
            Supplier<Component> message = invocation.getArgument(0);
            rendered.add(message.get());
            return null;
        }).when(source).sendSuccess(any(), eq(false));

        try (MockedStatic<IrisLanguage> language = mockStatic(IrisLanguage.class)) {
            language.when(IrisLanguage::activeLocale).thenReturn("en_US");
            language.when(IrisLanguage::availableLocales).thenReturn(Set.of("de_DE"));
            language.when(() -> IrisLanguage.plain(DirectorHelpMessages.BACK)).thenReturn("Back");
            CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
            dispatcher.register(Commands.literal("iris").then(ModdedLanguageCommands.tree()));

            assertEquals(1, dispatcher.execute("iris language", source));
        }

        assertEquals(6, rendered.size());
        assertEquals(ModdedCommandFeedback.banner("/iris language"), rendered.getFirst());
        assertEquals(new ClickEvent.RunCommand("/iris"), rendered.get(1).getStyle().getClickEvent());
        assertEquals("Iris language: en_US", rendered.get(2).getString());
        Component locale = rendered.get(3);
        assertTrue(locale.getString().contains("[me]"));
        assertFalse(locale.getString().contains("[server]"));
        assertEquals(new ClickEvent.RunCommand("/iris language self de_DE"),
                locale.getSiblings().getFirst().getStyle().getClickEvent());
        assertEquals(new ClickEvent.RunCommand("/iris language self reset"), rendered.get(4).getStyle().getClickEvent());
        assertEquals(ModdedCommandFeedback.footer(), rendered.getLast());
    }

    private void assertPersonalCompletion(String requested, String selected, String expected) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        CommandSourceStack source = mock(CommandSourceStack.class, RETURNS_DEEP_STUBS);
        ServerPlayer player = mock(ServerPlayer.class);
        UUID playerId = UUID.randomUUID();
        when(source.getPlayer()).thenReturn(player);
        when(player.getUUID()).thenReturn(playerId);
        MinecraftServer server = source.getServer();
        PluginLanguageService service = mock(PluginLanguageService.class);
        CompletableFuture<Void> selection = new CompletableFuture<>();
        when(service.selectPlayer(playerId, requested)).thenReturn(selection);
        when(service.clearPlayer(playerId)).thenReturn(selection);
        when(service.effectiveLocale(playerId)).thenReturn(selected);
        List<Component> rendered = new ArrayList<>();
        doAnswer(invocation -> {
            Supplier<Component> message = invocation.getArgument(0);
            rendered.add(message.get());
            return null;
        }).when(source).sendSuccess(any(), eq(false));
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(server).execute(any(Runnable.class));

        try (MockedStatic<IrisLanguage> language = mockStatic(IrisLanguage.class)) {
            language.when(IrisLanguage::selections).thenReturn(service);
            CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
            dispatcher.register(Commands.literal("iris").then(ModdedLanguageCommands.tree()));
            assertEquals(1, dispatcher.execute("iris language self " + requested, source));
            assertTrue(rendered.isEmpty());
            selection.complete(null);
        }

        assertEquals(List.of(expected), rendered.stream().map(Component::getString).toList());
    }
}

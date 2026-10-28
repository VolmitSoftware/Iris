package art.arcane.iris.modded.command;

import art.arcane.iris.localization.IrisLanguage;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandSource;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandText;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolPlayer;
import art.arcane.volmlib.util.localization.LanguageAudience;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ModdedStudioHotloadLanguageTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final UUID playerId = UUID.randomUUID();
    private File dataFolder;

    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Before
    public void setUp() throws Exception {
        dataFolder = temporaryFolder.newFolder();
        assertTrue(IrisLanguage.reload(dataFolder, "en_US"));
        Files.writeString(dataFolder.toPath().resolve("languages/de_DE.toml"),
                "\"iris.modded.moddedstudiocommands.you_do_not_have_open_studio_use_iris_studio_open\" = \"Kein Studio geöffnet.\"\n");
        IrisLanguage.start();
        IrisLanguage.selections().selectPlayer(playerId, "de_DE").get(5L, TimeUnit.SECONDS);
        ModdedStudioCommands.clear();
    }

    @After
    public void tearDown() {
        ModdedStudioCommands.clear();
        IrisLanguage.shutdown();
        assertTrue(IrisLanguage.reload(dataFolder, "en_US"));
    }

    @Test
    public void delayedHotloadFeedbackKeepsThePlayersLanguage() throws Exception {
        assertEquals(List.of("Kein Studio geöffnet."), runCommand("studio hotload", true));
    }

    @Test
    public void delayedConsoleRefreshUsesTheServerLanguage() throws Exception {
        assertEquals(List.of("You do not have an open studio. Use /iris studio open <pack> first."),
                runCommand("studio refresh", false));
    }

    private List<String> runCommand(String command, boolean playerCommand) throws Exception {
        NativeModdedServer server = mock(NativeModdedServer.class);
        List<Runnable> tasks = new ArrayList<>();
        doAnswer(invocation -> {
            tasks.add(invocation.getArgument(0));
            return null;
        }).when(server).execute(any(Runnable.class));
        NativeCommandSource source = mock(NativeCommandSource.class);
        when(source.server()).thenReturn(server);
        when(source.isGameMaster()).thenReturn(true);
        if (playerCommand) {
            NativeProtocolPlayer player = mock(NativeProtocolPlayer.class);
            when(player.id()).thenReturn(playerId);
            when(source.player()).thenReturn(player);
            when(source.playerId()).thenReturn(playerId);
        }
        List<String> messages = new ArrayList<>();
        doAnswer(invocation -> {
            NativeCommandText text = invocation.getArgument(0);
            messages.add(text.getString());
            return null;
        }).when(source).sendFailure(any());
        CommandDispatcher<NativeCommandSource> dispatcher = new CommandDispatcher<>();
        dispatcher.register(ModdedStudioCommands.tree("studio"));

        assertEquals(1, dispatcher.execute(command, source));
        assertTrue(messages.isEmpty());
        assertNull(LanguageAudience.current());
        UUID ambientAudience = playerCommand ? UUID.randomUUID() : playerId;
        try (LanguageAudience.Scope audience = LanguageAudience.open(ambientAudience)) {
            while (!tasks.isEmpty()) {
                tasks.removeFirst().run();
                assertEquals(ambientAudience, LanguageAudience.current());
            }
        }
        return messages;
    }
}

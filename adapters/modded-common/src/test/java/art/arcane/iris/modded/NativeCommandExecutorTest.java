package art.arcane.iris.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandExecutor;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NativeCommandExecutorTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void dispatchesConsoleSourceWithLeadingSlashRemoved() {
        MinecraftServer server = mock(MinecraftServer.class);
        Commands commands = mock(Commands.class);
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(server.getCommands()).thenReturn(commands);
        when(server.createCommandSourceStack()).thenReturn(source);
        List<Throwable> errors = new ArrayList<>();

        assertTrue(NativeCommandExecutor.dispatch(NativeModdedServer.fromHandle(server), "/say terrain", errors::add));

        verify(commands).performPrefixedCommand(source, "say terrain");
        assertTrue(errors.isEmpty());
    }

    @Test
    public void failedDispatchPreservesFailureForTheCaller() {
        MinecraftServer server = mock(MinecraftServer.class);
        Commands commands = mock(Commands.class);
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(server.getCommands()).thenReturn(commands);
        when(server.createCommandSourceStack()).thenReturn(source);
        IllegalStateException failure = new IllegalStateException("dispatch failed");
        doThrow(failure).when(commands).performPrefixedCommand(source, "say terrain");
        List<Throwable> errors = new ArrayList<>();

        assertFalse(NativeCommandExecutor.dispatch(NativeModdedServer.fromHandle(server), "say terrain", errors::add));

        assertEquals(List.of(failure), errors);
    }
}

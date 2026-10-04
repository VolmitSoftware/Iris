package art.arcane.iris.modded.command;

import art.arcane.iris.generation.context.IrisContext;
import art.arcane.iris.generation.locator.BiomeLocator;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.GenerationSessionLease;
import art.arcane.iris.generation.subterrain.SubterrainPosition;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandSource;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolPlayer;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.util.math.Position2;
import org.junit.Test;
import org.mockito.MockedStatic;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class ModdedCaveLocateTest {
    @Test
    public void caveTeleportUsesValidatedUndergroundPosition() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.player.teleport(fixture.world, 8.5, -42, 8.5)).thenReturn(true);
        try (MockedStatic<IrisModdedCommands> commands = mockStatic(IrisModdedCommands.class);
             MockedStatic<IrisContext> context = mockStatic(IrisContext.class)) {
            fixture.context(context);
            fixture.teleport();
            verify(fixture.player).teleport(fixture.world, 8.5, -42, 8.5);
            commands.verify(() -> IrisModdedCommands.ok(eq(fixture.source), anyString()));
            commands.verify(() -> IrisModdedCommands.fail(eq(fixture.source), anyString()), never());
        }
    }

    @Test
    public void failedTeleportNeverReportsSuccess() throws Exception {
        Fixture fixture = new Fixture();
        try (MockedStatic<IrisModdedCommands> commands = mockStatic(IrisModdedCommands.class);
             MockedStatic<IrisContext> context = mockStatic(IrisContext.class)) {
            fixture.context(context);
            fixture.teleport();
            commands.verify(() -> IrisModdedCommands.fail(eq(fixture.source), anyString()));
            commands.verify(() -> IrisModdedCommands.ok(eq(fixture.source), anyString()), never());
        }
    }

    @Test
    public void worldChangeCancelsBeforeCaveLookupOrTeleport() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.player.inWorld(fixture.world)).thenReturn(false);
        try (MockedStatic<IrisModdedCommands> commands = mockStatic(IrisModdedCommands.class)) {
            fixture.teleport();
            verifyNoInteractions(fixture.locator);
            verify(fixture.player, never()).teleport(any(), anyDouble(), anyDouble(), anyDouble());
            commands.verify(() -> IrisModdedCommands.fail(eq(fixture.source), anyString()));
        }
    }

    private static final class Fixture {
        private final NativeCommandSource source = mock(NativeCommandSource.class);
        private final NativeWorld world = mock(NativeWorld.class);
        private final NativeProtocolPlayer player = mock(NativeProtocolPlayer.class);
        private final Engine engine = mock(Engine.class);
        private final BiomeLocator locator = mock(BiomeLocator.class);

        private Fixture() throws Exception {
            when(player.connected()).thenReturn(true);
            when(player.inWorld(world)).thenReturn(true);
            when(engine.acquireGenerationLease("modded_locator_teleport")).thenReturn(mock(GenerationSessionLease.class));
            SubterrainPosition target = new SubterrainPosition(8, -40, 8);
            when(locator.position(engine, new Position2(0, 0))).thenReturn(target);
            when(locator.landing(engine, world, target, Optional.empty())).thenReturn(new SubterrainPosition(8, -42, 8));
        }

        private void context(MockedStatic<IrisContext> context) {
            context.when(() -> IrisContext.open(eq(engine), anyLong(), any())).thenReturn(mock(IrisContext.Scope.class));
        }

        private void teleport() {
            ModdedLocateCommands.teleportToLocateResult(source, world, engine, player, "biome deep", locator,
                    new Position2(0, 0), Optional.of(new ModdedLocateCommands.CaveDestination(
                            new SubterrainPosition(8, -40, 8), Optional.empty())));
        }
    }
}

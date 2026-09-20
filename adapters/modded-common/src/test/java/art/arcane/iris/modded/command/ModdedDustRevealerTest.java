package art.arcane.iris.modded.command;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.RuntimeUiMessages;
import art.arcane.iris.modded.ModdedIrisLog;
import art.arcane.iris.world.history.SavedBiomeUnavailableException;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandText;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeEditPlayer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeEditWorld;
import art.arcane.volmlib.nativelib.terrain.NativeBlockPoint;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModdedDustRevealerTest {
    @BeforeClass
    public static void initializeRuntimeTypes() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void loadingBiomesShowOneNoticeAndNextInteractionShowsResolvedDetails() {
        Engine engine = mock(Engine.class);
        NativeEditPlayer player = mock(NativeEditPlayer.class);
        NativeEditWorld level = mock(NativeEditWorld.class);
        NativeWorld world = mock(NativeWorld.class);
        NativeBlockState block = mock(NativeBlockState.class);
        NativeBlockPoint position = new NativeBlockPoint(4, 80, 7);
        IrisBiome biome = new IrisBiome();
        biome.setLoadKey("saved-forest");
        when(level.world()).thenReturn(world);
        when(level.block(4, 80, 7)).thenReturn(block);
        when(block.key()).thenReturn("minecraft:stone");
        when(level.biome(position)).thenReturn(new NativeEditWorld.BiomeIdentity("minecraft:forest", 4));
        when(engine.getSurfaceBiome(4, 7))
                .thenThrow(new SavedBiomeUnavailableException("Loading", true))
                .thenReturn(biome);

        try (MockedStatic<IrisModdedCommands> commands = mockStatic(IrisModdedCommands.class);
             MockedStatic<ModdedIrisLog> logging = mockStatic(ModdedIrisLog.class)) {
            commands.when(() -> IrisModdedCommands.engineFor(world)).thenReturn(engine);

            ModdedDustRevealer.reveal(player, level, position);

            ArgumentCaptor<NativeCommandText> notice = ArgumentCaptor.forClass(NativeCommandText.class);
            verify(player).sendSystemMessage(notice.capture());
            assertEquals(IrisLanguage.plain(RuntimeUiMessages.DUST_BIOME_LOADING), notice.getValue().getString());
            verify(engine, never()).getBiome(4, 80, 7);
            verify(level, never()).sound(any(), any(), any());
            logging.verifyNoInteractions();

            clearInvocations(player);
            ModdedDustRevealer.reveal(player, level, position);

            verify(player).sendSystemMessage(argThat(
                    message -> message.getString().startsWith("Surface biome: saved-forest")));
            verify(player, never()).sendSystemMessage(argThat(
                    message -> message.getString().equals(IrisLanguage.plain(RuntimeUiMessages.DUST_BIOME_LOADING))));
            logging.verifyNoInteractions();
        }
    }

    @Test
    public void unavailableSavedBiomesRemainLoggedAndAbortReveal() {
        Engine engine = mock(Engine.class);
        NativeEditPlayer player = mock(NativeEditPlayer.class);
        NativeEditWorld level = mock(NativeEditWorld.class);
        NativeWorld world = mock(NativeWorld.class);
        SavedBiomeUnavailableException failure = new SavedBiomeUnavailableException("Missing saved pack", false);
        when(level.world()).thenReturn(world);
        when(engine.getSurfaceBiome(4, 7)).thenThrow(failure);

        try (MockedStatic<IrisModdedCommands> commands = mockStatic(IrisModdedCommands.class);
             MockedStatic<ModdedIrisLog> logging = mockStatic(ModdedIrisLog.class)) {
            commands.when(() -> IrisModdedCommands.engineFor(world)).thenReturn(engine);

            ModdedDustRevealer.reveal(player, level, new NativeBlockPoint(4, 80, 7));

            ArgumentCaptor<NativeCommandText> notice = ArgumentCaptor.forClass(NativeCommandText.class);
            verify(player).sendSystemMessage(notice.capture());
            assertEquals(IrisLanguage.plain(RuntimeUiMessages.DUST_REVEAL_FAILED), notice.getValue().getString());
            logging.verify(() -> ModdedIrisLog.error("Iris dust saved biome lookup failed at {}", "4, 80, 7", failure));
            logging.verifyNoMoreInteractions();
            verify(engine, never()).getBiome(4, 80, 7);
            verify(level, never()).sound(any(), any(), any());
        }
    }

    @Test
    public void placementClassificationCoversObjectDecorationAndTerrain() {
        assertTrue(ModdedDustRevealer.placementLine(3, 10, 13, "tree")
                .contains("object/stilt 'tree'"));
        assertTrue(ModdedDustRevealer.placementLine(3, 10, 13, null)
                .contains("decoration/object/stilt"));
        assertTrue(ModdedDustRevealer.placementLine(0, 10, 10, "ore")
                .contains("buried object 'ore'"));
        assertTrue(ModdedDustRevealer.placementLine(-4, 10, 6, null)
                .contains("depth 4"));
    }

    @Test
    public void revealTraversalUsesDiagonalAdjacencyButNotDisconnectedBlocks() {
        Set<NativeBlockPoint> object = Set.of(
                new NativeBlockPoint(0, 0, 0),
                new NativeBlockPoint(1, 1, 1),
                new NativeBlockPoint(3, 3, 3));
        List<NativeBlockPoint> hits = ModdedDustRevealer.collect(
                new NativeBlockPoint(0, 0, 0),
                "object",
                -64,
                -64,
                320,
                new AtomicBoolean(),
                (int x, int relativeY, int z) ->
                        object.contains(new NativeBlockPoint(x, relativeY - 64, z))
                                ? "object"
                                : null);

        assertEquals(List.of(new NativeBlockPoint(0, 0, 0), new NativeBlockPoint(1, 1, 1)), hits);
    }

    @Test
    public void cancelledRevealDoesNoTraversal() {
        List<NativeBlockPoint> hits = ModdedDustRevealer.collect(
                new NativeBlockPoint(0, 0, 0),
                "object",
                -64,
                -64,
                320,
                new AtomicBoolean(true),
                (int x, int relativeY, int z) -> "object");

        assertTrue(hits.isEmpty());
    }
}

package art.arcane.iris.studio.edit;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.RuntimeUiMessages;
import art.arcane.iris.platform.bukkit.nms.INMS;
import art.arcane.iris.platform.bukkit.nms.INMSBinding;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.iris.world.history.SavedBiomeUnavailableException;
import art.arcane.iris.world.task.J;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class DustRevealerTest {
    @Test
    public void loadingSurfaceBiomeSendsOneNoticeAndStopsTheReveal() {
        try (Fixture fixture = new Fixture()) {
            when(fixture.engine.getSurfaceBiome(192, 816))
                    .thenThrow(new SavedBiomeUnavailableException("Saved biome information is loading.", true));

            DustRevealer.spawn(fixture.block, fixture.sender);

            verify(fixture.sender).sendMessage(IrisLanguage.text(RuntimeUiMessages.DUST_BIOME_LOADING));
            verifyNoMoreInteractions(fixture.sender);
            verify(fixture.engine).getObjectPlacementKey(192, 96, 816);
            fixture.verifyNoLaterQueriesOrReveal();
            fixture.logging.verifyNoInteractions();
        }
    }

    @Test
    public void loadingDuringHeightLookupSendsOneNoticeBeforeObjectLookup() {
        try (Fixture fixture = new Fixture()) {
            when(fixture.engine.getHeight(192, 816, true))
                    .thenThrow(new SavedBiomeUnavailableException("Saved biome information is loading.", true));

            DustRevealer.spawn(fixture.block, fixture.sender);

            verify(fixture.sender).sendMessage(IrisLanguage.text(RuntimeUiMessages.DUST_BIOME_LOADING));
            verifyNoMoreInteractions(fixture.sender);
            verify(fixture.engine, never()).getObjectPlacementKey(anyInt(), anyInt(), anyInt());
            fixture.verifyNoLaterQueriesOrReveal();
            fixture.logging.verifyNoInteractions();
        }
    }

    @Test
    public void anotherClickReportsTheBiomeAfterLoadingFinishes() {
        try (Fixture fixture = new Fixture()) {
            IrisBiome biome = mock(IrisBiome.class);
            when(biome.getLoadKey()).thenReturn("ready-biome");
            when(fixture.engine.getSurfaceBiome(192, 816))
                    .thenThrow(new SavedBiomeUnavailableException("Saved biome information is loading.", true))
                    .thenReturn(biome);

            DustRevealer.spawn(fixture.block, fixture.sender);
            verify(fixture.sender).sendMessage(IrisLanguage.text(RuntimeUiMessages.DUST_BIOME_LOADING));
            clearInvocations(fixture.sender);

            DustRevealer.spawn(fixture.block, fixture.sender);

            ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
            verify(fixture.sender, atLeastOnce()).sendMessage(messages.capture());
            assertTrue(messages.getAllValues().stream().anyMatch(message -> message.contains("ready-biome")));
            verify(fixture.sender, never()).sendMessage(IrisLanguage.text(RuntimeUiMessages.DUST_BIOME_LOADING));
            verify(fixture.sender, never()).sendMessage(IrisLanguage.text(RuntimeUiMessages.DUST_REVEAL_FAILED));
            fixture.logging.verifyNoInteractions();
        }
    }

    @Test
    public void unavailableSavedBiomeReportsTheOriginalFailureAndStopsTheReveal() {
        try (Fixture fixture = new Fixture()) {
            SavedBiomeUnavailableException failure = new SavedBiomeUnavailableException(
                    "Saved biome data could not be read.", new IllegalStateException("Corrupt biome receipt"));
            when(fixture.engine.getSurfaceBiome(192, 816)).thenThrow(failure);

            DustRevealer.spawn(fixture.block, fixture.sender);

            fixture.logging.verify(() -> IrisLogging.reportError(failure));
            fixture.logging.verifyNoMoreInteractions();
            verify(fixture.sender).sendMessage(IrisLanguage.text(RuntimeUiMessages.DUST_REVEAL_FAILED));
            verifyNoMoreInteractions(fixture.sender);
            verify(fixture.engine).getObjectPlacementKey(192, 96, 816);
            fixture.verifyNoLaterQueriesOrReveal();
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final MockedStatic<IrisToolbelt> toolbelt = mockStatic(IrisToolbelt.class);
        private final MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class);
        private final MockedStatic<INMS> nativeAccess = mockStatic(INMS.class);
        private final MockedStatic<J> scheduling = mockStatic(J.class);
        private final Engine engine = mock(Engine.class);
        private final World world = mock(World.class);
        private final Block block = mock(Block.class);
        private final VolmitSender sender = mock(VolmitSender.class);

        private Fixture() {
            PlatformChunkGenerator generator = mock(PlatformChunkGenerator.class);
            when(generator.getEngine()).thenReturn(engine);
            toolbelt.when(() -> IrisToolbelt.access(world)).thenReturn(generator);
            INMSBinding binding = mock(INMSBinding.class);
            when(binding.getTrueBiomeBaseKey(any())).thenReturn("minecraft:plains");
            nativeAccess.when(INMS::get).thenReturn(binding);
            when(block.getWorld()).thenReturn(world);
            when(block.getX()).thenReturn(192);
            when(block.getY()).thenReturn(96);
            when(block.getZ()).thenReturn(816);
            when(block.getType()).thenReturn(Material.STONE);
            when(engine.getHeight(192, 816, true)).thenReturn(96);
        }

        private void verifyNoLaterQueriesOrReveal() {
            verify(engine, never()).getBiome(anyInt(), anyInt(), anyInt());
            verify(engine, never()).getCaveOrMantleBiome(anyInt(), anyInt(), anyInt());
            verify(engine, never()).getRegion(anyInt(), anyInt());
            verify(engine, never()).getObjectsAt(anyInt(), anyInt());
            scheduling.verifyNoInteractions();
        }

        @Override
        public void close() {
            scheduling.close();
            nativeAccess.close();
            logging.close();
            toolbelt.close();
        }
    }
}

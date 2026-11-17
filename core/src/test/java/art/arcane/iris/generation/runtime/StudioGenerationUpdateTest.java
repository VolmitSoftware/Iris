package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.world.history.GenerationEpoch;
import art.arcane.iris.world.history.GenerationEpochContractFactory;
import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.iris.world.history.GenerationHistoryPaths;
import art.arcane.iris.world.history.GenerationPackFingerprint;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class StudioGenerationUpdateTest {
    private static final String MISSING_DIMENSION = "missing";

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void unchangedSourceNeedsNoManifestUpdateAndLeavesNoSnapshot() throws Exception {
        Path source = temporary.newFolder("unchanged-pack").toPath();
        Files.createDirectories(source.resolve("dimensions"));
        Files.writeString(source.resolve("dimensions/test.json"), "{}");

        assertUnchangedSourceStagesNothing(source, source);
    }

    @Test
    public void unchangedAuthoringRootAliasNeedsNoManifestUpdate() throws Exception {
        Path source = temporary.newFolder("unchanged-alias-target").toPath();
        Files.createDirectories(source.resolve("dimensions"));
        Files.writeString(source.resolve("dimensions/test.json"), "{}");
        Path alias = temporary.getRoot().toPath().resolve("unchanged-alias");
        Files.createSymbolicLink(alias, source);

        assertUnchangedSourceStagesNothing(alias, source);
        assertThrows(IOException.class, () -> GenerationPackFingerprint.compute(alias, GenerationPackFingerprint.CURRENT_VERSION));
    }

    private void assertUnchangedSourceStagesNothing(Path authoringRoot, Path source) throws Exception {
        String fingerprint = GenerationPackFingerprint.compute(source, GenerationPackFingerprint.CURRENT_VERSION);
        GenerationHistoryPaths paths = GenerationHistoryPaths.forDimension(temporary.newFolder().toPath());
        Files.createDirectories(paths.generationRoot());
        Files.writeString(paths.manifest(), "unchanged manifest");
        IrisEngine engine = mock(IrisEngine.class);
        GenerationHistory history = mock(GenerationHistory.class);
        GenerationEpoch active = mock(GenerationEpoch.class);
        when(engine.getStudioGenerationSource()).thenReturn(authoringRoot);
        when(history.activeEpoch()).thenReturn(active);
        when(history.paths()).thenReturn(paths);
        when(active.packFingerprintVersion()).thenReturn(GenerationPackFingerprint.CURRENT_VERSION);
        when(active.packFingerprint()).thenReturn(fingerprint);

        assertNull(StudioGenerationUpdate.prepare(engine, history));

        assertEquals("unchanged manifest", Files.readString(paths.manifest()));
        try (Stream<Path> entries = Files.list(paths.generationRoot())) {
            assertEquals(1L, entries.count());
        }
        verify(history).activeEpoch();
        verify(history).paths();
        verify(history).activePackLinkBasis();
        verifyNoMoreInteractions(history);
    }

    @Test
    public void changedAuthoringRootAliasReachesValidationAndCleansRejectedSnapshot() throws Exception {
        Path source = temporary.newFolder("changed-alias-target").toPath();
        Files.createDirectories(source.resolve("dimensions"));
        Files.writeString(source.resolve("dimensions/test.json"), "{}");
        Path alias = temporary.getRoot().toPath().resolve("changed-alias");
        Files.createSymbolicLink(alias, source);
        GenerationHistoryPaths paths = GenerationHistoryPaths.forDimension(temporary.newFolder("alias-world").toPath());
        Files.createDirectories(paths.generationRoot());
        Files.writeString(paths.manifest(), "unchanged manifest");
        IrisEngine engine = mock(IrisEngine.class);
        when(engine.getStudioGenerationSource()).thenReturn(alias);
        GenerationHistory history = mock(GenerationHistory.class);
        GenerationEpoch active = mock(GenerationEpoch.class);
        when(history.activeEpoch()).thenReturn(active);
        when(history.paths()).thenReturn(paths);
        when(active.packFingerprintVersion()).thenReturn(GenerationPackFingerprint.CURRENT_VERSION);
        when(active.packFingerprint()).thenReturn("0".repeat(64));
        when(active.dimensionContract()).thenReturn(contract(MISSING_DIMENSION));

        IOException failure = prepareWithoutTheDimension(engine, history);

        assertTrue(failure.getMessage(), failure.getMessage().contains(
                "Studio pack update is missing dimension '" + MISSING_DIMENSION + "'."));
        assertEquals("unchanged manifest", Files.readString(paths.manifest()));
        try (Stream<Path> entries = Files.list(paths.generationRoot())) {
            assertEquals(1L, entries.count());
        }
        verify(history, times(2)).activeEpoch();
        verify(history).paths();
        verify(history).activePackLinkBasis();
        verifyNoMoreInteractions(history);
    }

    @Test
    public void authoringRootAliasStillRejectsInternalFileAndDirectoryLinks() throws Exception {
        for (boolean directory : List.of(false, true)) {
            Path source = temporary.newFolder("internal-link-pack-" + directory).toPath();
            Path outside = directory ? temporary.newFolder("external-directory").toPath()
                    : temporary.newFile("external-file.json").toPath();
            Files.createSymbolicLink(source.resolve("linked-resource"), outside);
            Path alias = temporary.getRoot().toPath().resolve("unsafe-alias-" + directory);
            Files.createSymbolicLink(alias, source);
            IrisEngine engine = mock(IrisEngine.class);
            when(engine.getStudioGenerationSource()).thenReturn(alias);
            GenerationHistory history = mock(GenerationHistory.class);

            IOException failure = assertThrows(IOException.class, () -> StudioGenerationUpdate.prepare(engine, history));

            assertTrue(failure.getMessage(), failure.getMessage().contains("Pack contains a symbolic link"));
            verifyNoInteractions(history);
        }
    }

    @Test
    public void invalidPackLeavesManifestUntouchedAndRemovesItsTemporarySnapshot() throws Exception {
        Path source = temporary.newFolder("invalid-pack").toPath();
        Files.writeString(source.resolve("pack.json"), "{}");
        Path world = temporary.newFolder("invalid-world").toPath();
        GenerationHistoryPaths paths = GenerationHistoryPaths.forDimension(world);
        Files.createDirectories(paths.generationRoot());
        Files.writeString(paths.manifest(), "unchanged manifest");
        IrisEngine engine = mock(IrisEngine.class);
        GenerationHistory history = mock(GenerationHistory.class);
        GenerationEpoch active = mock(GenerationEpoch.class);
        when(engine.getStudioGenerationSource()).thenReturn(source);
        when(history.activeEpoch()).thenReturn(active);
        when(history.paths()).thenReturn(paths);
        when(active.packFingerprintVersion()).thenReturn(GenerationPackFingerprint.CURRENT_VERSION);
        when(active.packFingerprint()).thenReturn("0".repeat(64));
        when(active.dimensionContract()).thenReturn(contract("test"));

        IOException failure = prepareWithoutTheDimension(engine, history);

        assertTrue(failure.getMessage(), failure.getMessage().contains("Studio pack update is missing dimension 'test'."));
        assertEquals("unchanged manifest", Files.readString(paths.manifest()));
        try (Stream<Path> entries = Files.list(paths.generationRoot())) {
            assertEquals(1L, entries.count());
        }
        verify(history, times(2)).activeEpoch();
        verify(history).paths();
        verify(history).activePackLinkBasis();
        verifyNoMoreInteractions(history);
    }

    @SuppressWarnings("unchecked")
    private static IOException prepareWithoutTheDimension(IrisEngine engine, GenerationHistory history) {
        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisDimension> dimensions = mock(ResourceLoader.class);
        when(data.getDimensionLoader()).thenReturn(dimensions);
        try (MockedStatic<IrisData> loaders = mockStatic(IrisData.class)) {
            loaders.when(() -> IrisData.openRuntime(any(File.class))).thenReturn(data);
            return assertThrows(IOException.class, () -> StudioGenerationUpdate.prepare(engine, history));
        }
    }

    private static GenerationEpoch.DimensionContract contract(String dimensionKey) {
        return new GenerationEpoch.DimensionContract(
                dimensionKey, "iris:overworld_type", "NORMAL", "OVERWORLD", 127,
                -64, 384, 384, 1D, false, "none", 0, "0".repeat(64),
                GenerationEpochContractFactory.CURRENT_DIMENSION_TYPE_FINGERPRINT_SCHEMA, "c".repeat(64));
    }
}

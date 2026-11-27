package art.arcane.iris.generation.runtime;

import art.arcane.iris.world.history.GenerationEpoch;
import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.iris.world.history.GenerationHistoryPaths;
import art.arcane.iris.world.history.GenerationPackFingerprint;
import art.arcane.iris.world.history.PackTreeState;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class StudioGenerationUpdateTest {
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

    @Test
    public void sourceMatchingTheActivatedRecordNeedsNoSnapshot() throws Exception {
        Path source = temporary.newFolder("recorded-pack").toPath();
        Files.createDirectories(source.resolve("dimensions"));
        Files.writeString(source.resolve("dimensions/test.json"), "{}");
        IrisEngine engine = mock(IrisEngine.class);
        GenerationHistory history = mock(GenerationHistory.class);
        GenerationEpoch active = mock(GenerationEpoch.class);
        when(engine.getStudioGenerationSource()).thenReturn(source);
        when(history.activeEpoch()).thenReturn(active);
        when(active.epochId()).thenReturn("a".repeat(64));
        ActivatedStudioSource recorded = new ActivatedStudioSource("a".repeat(64), PackTreeState.capture(source));

        assertNull(StudioGenerationUpdate.prepare(engine, history, recorded, StudioPackValidation.BEFORE_ACTIVATION));

        verify(history).activeEpoch();
        verifyNoMoreInteractions(history);
    }

    @Test
    public void recordOfAnotherEpochIsIgnored() throws Exception {
        Path source = temporary.newFolder("stale-record-pack").toPath();
        Files.createDirectories(source.resolve("dimensions"));
        Files.writeString(source.resolve("dimensions/test.json"), "{}");
        GenerationEpoch active = mock(GenerationEpoch.class);
        when(active.epochId()).thenReturn("a".repeat(64));
        ActivatedStudioSource stale = new ActivatedStudioSource("b".repeat(64), PackTreeState.capture(source));

        assertTrue(stale.sourceStateOf(active).isEmpty());
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

        assertNull(StudioGenerationUpdate.prepare(engine, history, null, StudioPackValidation.BEFORE_ACTIVATION));

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

        IOException failure = assertThrows(IOException.class, () -> StudioGenerationUpdate.prepare(engine, history, null, StudioPackValidation.BEFORE_ACTIVATION));

        assertTrue(failure.getMessage(), failure.getMessage().contains(
                "Studio pack update is invalid: Dimension 'test' declares no regions."));
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

            IOException failure = assertThrows(IOException.class, () -> StudioGenerationUpdate.prepare(engine, history, null, StudioPackValidation.BEFORE_ACTIVATION));

            assertTrue(failure.getMessage(), failure.getMessage().contains("Pack contains a symbolic link"));
            verifyNoInteractions(history);
        }
    }

    @Test
    public void validationAfterActivationDoesNotRejectAnInvalidPackInPrepare() throws Exception {
        Path source = temporary.newFolder("unvalidated-pack").toPath();
        Files.createDirectories(source.resolve("dimensions"));
        Files.writeString(source.resolve("dimensions/test.json"), "{}");
        GenerationHistoryPaths paths = GenerationHistoryPaths.forDimension(temporary.newFolder("unvalidated-world").toPath());
        Files.createDirectories(paths.generationRoot());
        IrisEngine engine = mock(IrisEngine.class);
        GenerationHistory history = mock(GenerationHistory.class);
        GenerationEpoch active = mock(GenerationEpoch.class);
        when(engine.getStudioGenerationSource()).thenReturn(source);
        when(history.activeEpoch()).thenReturn(active);
        when(history.paths()).thenReturn(paths);
        when(active.packFingerprintVersion()).thenReturn(GenerationPackFingerprint.CURRENT_VERSION);
        when(active.packFingerprint()).thenReturn("0".repeat(64));

        Throwable failure = assertThrows(Throwable.class, () -> StudioGenerationUpdate.prepare(
                engine, history, null, StudioPackValidation.AFTER_ACTIVATION));

        assertFalse(String.valueOf(failure.getMessage()),
                String.valueOf(failure.getMessage()).contains("Studio pack update is invalid"));
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

        IOException failure = assertThrows(IOException.class, () -> StudioGenerationUpdate.prepare(engine, history, null, StudioPackValidation.BEFORE_ACTIVATION));

        assertTrue(failure.getMessage(), failure.getMessage().contains("Missing dimensions/ folder"));
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
    public void invalidNativeAnchorCannotStageOrPromoteStudioUpdate() throws Exception {
        Path source = temporary.newFolder("invalid-anchor-pack").toPath();
        Files.createDirectories(source.resolve("dimensions"));
        Files.writeString(source.resolve("dimensions/test.json"), """
                {"structures":[{"placementId":"village","anchor":"SURFACE",
                  "nativeStructures":[{"structure":"minecraft:village_plains"}]}]}
                """);
        GenerationHistoryPaths paths = GenerationHistoryPaths.forDimension(
                temporary.newFolder("invalid-anchor-world").toPath());
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

        IOException failure = assertThrows(IOException.class, () -> StudioGenerationUpdate.prepare(engine, history, null, StudioPackValidation.BEFORE_ACTIVATION));

        assertTrue(failure.getMessage(), failure.getMessage().contains(
                "Dimension 'test' structures[0].anchor must be omitted, null, or LEGACY for nativeStructures."));
        assertEquals("unchanged manifest", Files.readString(paths.manifest()));
        try (Stream<Path> entries = Files.list(paths.generationRoot())) {
            assertEquals(1L, entries.count());
        }
        verify(history, times(2)).activeEpoch();
        verify(history).paths();
        verify(history).activePackLinkBasis();
        verifyNoMoreInteractions(history);
    }
}

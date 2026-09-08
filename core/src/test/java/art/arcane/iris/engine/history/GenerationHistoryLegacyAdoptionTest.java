package art.arcane.iris.engine.history;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class GenerationHistoryLegacyAdoptionTest extends GenerationHistorySupport {
    @Test
    public void legacyAdoptionIsDurableExplicitAndIdempotent() throws Exception {
        Path world = temporaryFolder.newFolder("legacy-world").toPath();
        Path legacy = Files.createDirectories(world.resolve("iris/pack/dimensions"));
        Files.writeString(legacy.resolve("main.json"), "legacy");
        Path legacyRoot = world.resolve("iris/pack");
        Path legacyMantle = Files.createDirectories(world.resolve("mantle-hydrology"));
        Files.writeString(legacyMantle.resolve("r.0.0.lz4b"), "mantle");
        String fingerprint = fingerprint(legacyRoot);

        GenerationHistory adopted = GenerationHistory.adoptLegacyPack(
                world,
                fingerprint,
                42L,
                contract(),
                GenerationRegistryContract.empty()
        );

        assertFalse(Files.exists(legacyRoot));
        assertFalse(Files.exists(legacyMantle));
        assertTrue(Files.isDirectory(adopted.activePackRoot()));
        Path activationMantle = adopted.paths().activationMantleRoot(1L);
        assertEquals("mantle", Files.readString(activationMantle.resolve("r.0.0.lz4b")));
        assertEquals(1L, adopted.activeActivation().activationId());
        GenerationHistory repeated = GenerationHistory.adoptLegacyPack(
                world,
                fingerprint,
                42L,
                contract(),
                GenerationRegistryContract.empty()
        );
        assertEquals(adopted.activeEpoch(), repeated.activeEpoch());
        assertEquals("mantle", Files.readString(repeated.paths().activationMantleRoot(1L)
                .resolve("r.0.0.lz4b")));
    }

    @Test
    public void legacyMantleAdoptionFailsClosedOnDualRoots() throws Exception {
        Path world = temporaryFolder.newFolder("legacy-dual-mantle-world").toPath();
        Path legacyPack = Files.createDirectories(world.resolve("iris/pack/dimensions")).getParent();
        Files.writeString(legacyPack.resolve("dimensions/main.json"), "legacy");
        Path legacyMantle = Files.createDirectories(world.resolve("mantle-hydrology"));
        Files.writeString(legacyMantle.resolve("legacy.bin"), "legacy");
        GenerationHistoryPaths paths = GenerationHistoryPaths.forDimension(world);
        Path activationMantle = Files.createDirectories(paths.activationMantleRoot(1L));
        Files.writeString(activationMantle.resolve("activation.bin"), "activation");

        IOException failure = assertThrows(
                IOException.class,
                () -> GenerationHistory.adoptLegacyPack(
                        world,
                        fingerprint(legacyPack),
                        42L,
                        contract(),
                        GenerationRegistryContract.empty()
                )
        );

        assertTrue(failure.getMessage().contains("mantle roots conflict"));
        assertEquals("legacy", Files.readString(legacyMantle.resolve("legacy.bin")));
        assertEquals("activation", Files.readString(activationMantle.resolve("activation.bin")));
    }

    @Test
    public void legacyMantleAdoptionRejectsRootAliases() throws Exception {
        Path world = temporaryFolder.newFolder("legacy-aliased-mantle-world").toPath();
        Path legacyPack = Files.createDirectories(world.resolve("iris/pack/dimensions")).getParent();
        Files.writeString(legacyPack.resolve("dimensions/main.json"), "legacy");
        GenerationHistoryPaths paths = GenerationHistoryPaths.forDimension(world);
        Path activationMantle = Files.createDirectories(paths.activationMantleRoot(1L));
        Path alias = paths.legacyMantleRoot();
        try {
            Files.createSymbolicLink(alias, activationMantle);
        } catch (UnsupportedOperationException exception) {
            return;
        }

        assertThrows(
                IOException.class,
                () -> GenerationHistory.adoptLegacyPack(
                        world,
                        fingerprint(legacyPack),
                        42L,
                        contract(),
                        GenerationRegistryContract.empty()
                )
        );
        assertTrue(Files.isSymbolicLink(alias));
        assertTrue(Files.isDirectory(activationMantle));
    }

    @Test
    public void legacyAdoptionNeverDeletesMismatchedOrUnsafeInput() throws Exception {
        Path mismatchWorld = temporaryFolder.newFolder("legacy-mismatch-world").toPath();
        Path mismatchRoot = Files.createDirectories(mismatchWorld.resolve("iris/pack/dimensions")).getParent();
        Files.writeString(mismatchRoot.resolve("dimensions/main.json"), "legacy");

        assertThrows(
                IOException.class,
                () -> GenerationHistory.adoptLegacyPack(
                        mismatchWorld,
                        "f".repeat(64),
                        42L,
                        contract(),
                        GenerationRegistryContract.empty()
                )
        );
        assertTrue(Files.isDirectory(mismatchRoot));
        assertFalse(Files.exists(GenerationHistoryPaths.forDimension(mismatchWorld).manifest()));

        Path linkedWorld = temporaryFolder.newFolder("legacy-linked-world").toPath();
        Files.createDirectories(linkedWorld.resolve("iris"));
        Path outside = temporaryFolder.newFolder("legacy-outside").toPath();
        Path link = linkedWorld.resolve("iris/pack");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException exception) {
            return;
        }
        assertThrows(
                IOException.class,
                () -> GenerationHistory.adoptLegacyPack(
                        linkedWorld,
                        "f".repeat(64),
                        42L,
                        contract(),
                        GenerationRegistryContract.empty()
                )
        );
        assertTrue(Files.isDirectory(outside));
        assertTrue(Files.isSymbolicLink(link));
    }
}

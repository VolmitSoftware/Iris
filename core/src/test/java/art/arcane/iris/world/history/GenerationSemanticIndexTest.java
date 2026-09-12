package art.arcane.iris.world.history;

import art.arcane.iris.generation.hydrology.HydrologyFeatureType;
import art.arcane.iris.testsupport.DurabilityMode;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.CRC32;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class GenerationSemanticIndexTest {
    @ClassRule
    public static final DurabilityMode DURABILITY = DurabilityMode.relaxed();

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void roundTripPreservesAllGeneratedTruthAndQueriesWithoutDiskAccess() throws Exception {
        Path dimensionRoot = temporaryFolder.newFolder("dimension").toPath();
        GenerationSemanticIndex index = GenerationSemanticIndex.load(dimensionRoot);
        ChunkGenerationSemantics semantics = completeSemantics(-33, -1, 7L);

        assertTrue(index.recordAndPersist(semantics));
        assertFalse(index.recordAndPersist(semantics));
        Path expectedDirectory = dimensionRoot.resolve("iris/generation/semantics");
        assertEquals(expectedDirectory, index.storageDirectory());
        Path shard = onlyShard(expectedDirectory);
        assertTrue(shard.getFileName().toString().matches("r\\.-2\\.-1\\.[0-9a-f]{64}\\.isem"));

        GenerationSemanticIndex loaded = GenerationSemanticIndex.load(dimensionRoot);
        assertEquals(Optional.of(semantics), loaded.get(-33, -1));
        assertEquals(1, loaded.recordCount());
        Files.delete(shard);

        assertEquals(-33, requiredMatch(loaded, GenerationSemanticIndex.SemanticKind.SURFACE_BIOME, "iris:forest").chunk().chunkX());
        assertEquals(-33, requiredMatch(loaded, GenerationSemanticIndex.SemanticKind.CAVE_BIOME, "iris:limestone_caves").chunk().chunkX());
        assertEquals(-33, requiredMatch(loaded, GenerationSemanticIndex.SemanticKind.REGION, "temperate").chunk().chunkX());
        assertEquals(-33, requiredMatch(loaded, GenerationSemanticIndex.SemanticKind.RIVER_PROFILE, "rivers/default").chunk().chunkX());
        assertEquals(-33, requiredMatch(loaded, GenerationSemanticIndex.SemanticKind.OBJECT, "iris:oak").chunk().chunkX());
        GenerationSemanticIndex.Match structure = requiredMatch(
                loaded,
                GenerationSemanticIndex.SemanticKind.STRUCTURE,
                "minecraft:village_plains"
        );
        assertEquals(new ChunkGenerationSemantics.BlockPosition(-520, 72, -7), structure.exactPosition().orElseThrow());
    }

    @Test
    public void packFilenameKeysSurviveJournalCompactionAndExactQueries() throws Exception {
        Path root = temporaryFolder.newFolder("pack-filename-keys").toPath();
        GenerationSemanticIndex index = GenerationSemanticIndex.initialize(root);
        ChunkGenerationSemantics original = ChunkGenerationSemantics.builder(-33, -1, 5L)
                .addObject("trees/mixed/AmySmol10")
                .addObject("trees/mixed/amysmol10")
                .addSurfaceBiome("mountain/Cute_Cliffs+")
                .addRegion("Regions/Highlands")
                .addStructure("Structures/Tower+", -520, 72, -7)
                .seal().build();
        index.claimAndPersist(original);
        GenerationSemanticIndex replayed = GenerationSemanticIndex.load(root);
        assertEquals(original, replayed.get(-33, -1).orElseThrow());
        replayed.compactJournals();
        GenerationSemanticIndex compacted = GenerationSemanticIndex.load(root);
        assertEquals(original, compacted.get(-33, -1).orElseThrow());
        assertEquals("trees/mixed/AmySmol10",
                requiredMatch(compacted, GenerationSemanticIndex.SemanticKind.OBJECT,
                        "trees/mixed/AmySmol10").key());
        assertEquals("trees/mixed/amysmol10",
                requiredMatch(compacted, GenerationSemanticIndex.SemanticKind.OBJECT,
                        "trees/mixed/amysmol10").key());
        assertEquals("mountain/Cute_Cliffs+",
                requiredMatch(compacted, GenerationSemanticIndex.SemanticKind.SURFACE_BIOME,
                        "mountain/Cute_Cliffs+").key());
        assertEquals("Structures/Tower+",
                requiredMatch(compacted, GenerationSemanticIndex.SemanticKind.STRUCTURE,
                        "Structures/Tower+").key());
        assertTrue(compacted.findNearest(GenerationSemanticIndex.Query.acrossActivations(
                GenerationSemanticIndex.SemanticKind.SURFACE_BIOME, "mountain/cute_cliffs+",
                new ChunkGenerationSemantics.BlockPosition(-520, 72, -7), 2)).isEmpty());
    }

    @Test
    public void pointsOfInterestSurviveJournalReplayAndCompaction() throws Exception {
        Path root = temporaryFolder.newFolder("poi-journal").toPath();
        GenerationSemanticIndex index = GenerationSemanticIndex.initialize(root);
        ChunkGenerationSemantics original = ChunkGenerationSemantics.builder(-2, 3, 5L)
                .addPointOfInterest(new ChunkGenerationSemantics.PointOfInterest("buried_treasure",
                        new ChunkGenerationSemantics.BlockPosition(-31, 102, 55)))
                .addPointOfInterest(new ChunkGenerationSemantics.PointOfInterest("custom:altar",
                        new ChunkGenerationSemantics.BlockPosition(-22, 78, 61)))
                .seal().build();
        index.claimAndPersist(original);
        GenerationSemanticIndex replayed = GenerationSemanticIndex.load(root);
        assertEquals(original, replayed.get(-2, 3).orElseThrow());
        replayed.compactJournals();
        assertEquals(original, GenerationSemanticIndex.load(root).get(-2, 3).orElseThrow());
    }

    @Test
    public void pointsOfInterestRejectNoncanonicalOrderAndExcessiveCounts() throws Exception {
        for (int corruption = 0; corruption < 2; corruption++) {
            Path root = temporaryFolder.newFolder("poi-invalid-" + corruption).toPath();
            GenerationSemanticIndex index = GenerationSemanticIndex.initialize(root);
            index.recordAndPersist(ChunkGenerationSemantics.builder(0, 0, 1L)
                    .addPointOfInterest(new ChunkGenerationSemantics.PointOfInterest("buried_treasure",
                            new ChunkGenerationSemantics.BlockPosition(1, 12, 2)))
                    .addPointOfInterest(new ChunkGenerationSemantics.PointOfInterest("buried_treasure",
                            new ChunkGenerationSemantics.BlockPosition(2, 12, 2)))
                    .seal().build());
            Path shard = onlyShard(index.storageDirectory());
            byte[] encoded = Files.readAllBytes(shard);
            int countOffset = encoded.length - Integer.BYTES - Short.BYTES - 2 * (Short.BYTES + 3 * Integer.BYTES);
            if (corruption == 0) {
                System.arraycopy(encoded, countOffset + Short.BYTES, encoded,
                        countOffset + Short.BYTES + 14, 14);
            } else {
                ByteBuffer.wrap(encoded).putShort(countOffset, (short) (ChunkGenerationSemantics.MAX_STRUCTURES + 1));
            }
            rewriteChecksum(encoded);
            replaceReferencedShard(root, shard, encoded);
            assertThrows(IOException.class, () -> GenerationSemanticIndex.load(root).forEachRecord(record -> { }));
        }
    }

    @Test
    public void stagedFactsMergeMonotonicallyAndSealDurably() throws Exception {
        Path dimensionRoot = temporaryFolder.newFolder("staged").toPath();
        GenerationSemanticIndex index = GenerationSemanticIndex.load(dimensionRoot);
        ChunkGenerationSemantics noiseStage = ChunkGenerationSemantics.builder(4, 5, 3L)
                .addSurfaceBiome("iris:forest")
                .build();
        ChunkGenerationSemantics biomeStage = ChunkGenerationSemantics.builder(4, 5, 3L)
                .addRegion("temperate")
                .addRiverProfile("rivers/new")
                .build();
        ChunkGenerationSemantics fullStage = ChunkGenerationSemantics.builder(4, 5, 3L)
                .addObject("iris:oak")
                .addStructure("iris:tower", 72, 90, 88)
                .seal()
                .build();

        assertTrue(index.recordAndPersist(noiseStage));
        assertFalse(index.recordAndPersist(noiseStage));
        assertTrue(index.recordAndPersist(biomeStage));
        assertTrue(index.recordAndPersist(fullStage));
        assertFalse(index.recordAndPersist(noiseStage));

        ChunkGenerationSemantics expected = ChunkGenerationSemantics.builder(4, 5, 3L)
                .addSurfaceBiome("iris:forest")
                .addRegion("temperate")
                .addRiverProfile("rivers/new")
                .addObject("iris:oak")
                .addStructure("iris:tower", 72, 90, 88)
                .seal()
                .build();
        assertEquals(Optional.of(expected), index.get(4, 5));
        assertEquals(1, index.recordCount());
        assertEquals(Optional.of(expected), GenerationSemanticIndex.load(dimensionRoot).get(4, 5));
    }

    @Test
    public void conflictingActivationsAndFactsAfterSealAreRejectedWithoutDiskChanges() throws Exception {
        Path dimensionRoot = temporaryFolder.newFolder("sealed").toPath();
        GenerationSemanticIndex index = GenerationSemanticIndex.load(dimensionRoot);
        ChunkGenerationSemantics sealed = ChunkGenerationSemantics.builder(4, 5, 3L)
                .addSurfaceBiome("iris:forest")
                .seal()
                .build();
        assertTrue(index.recordAndPersist(sealed));
        Path shard = onlyShard(index.storageDirectory());
        byte[] persisted = Files.readAllBytes(shard);

        ChunkGenerationSemantics additionalFact = ChunkGenerationSemantics.builder(4, 5, 3L)
                .addSurfaceBiome("iris:desert")
                .build();
        ChunkGenerationSemantics differentActivation = ChunkGenerationSemantics.builder(4, 5, 4L)
                .addSurfaceBiome("iris:forest")
                .build();

        assertThrows(IllegalStateException.class, () -> index.recordAndPersist(additionalFact));
        assertThrows(IllegalStateException.class, () -> index.recordAndPersist(differentActivation));
        assertArrayEquals(persisted, Files.readAllBytes(shard));
        assertEquals(Optional.of(sealed), index.get(4, 5));
        assertEquals(Optional.of(sealed), GenerationSemanticIndex.load(dimensionRoot).get(4, 5));
    }

    @Test
    public void nearestChunkUsesDistanceStableTiesRadiusAndActivationFilters() throws Exception {
        Path dimensionRoot = temporaryFolder.newFolder("nearest-chunks").toPath();
        GenerationSemanticIndex index = GenerationSemanticIndex.load(dimensionRoot);
        index.recordAndPersist(ChunkGenerationSemantics.builder(-1, 0, 1L).addRiverProfile("rivers/new").build());
        index.recordAndPersist(ChunkGenerationSemantics.builder(1, 0, 2L).addRiverProfile("rivers/new").build());
        ChunkGenerationSemantics.BlockPosition origin = new ChunkGenerationSemantics.BlockPosition(0, 80, 0);

        GenerationSemanticIndex.Match tied = index.findNearest(GenerationSemanticIndex.Query.acrossActivations(
                GenerationSemanticIndex.SemanticKind.RIVER_PROFILE,
                "rivers/new",
                origin,
                2
        )).orElseThrow();
        assertEquals(new GenerationSemanticIndex.ChunkReference(-1, 0, 1L), tied.chunk());

        GenerationSemanticIndex.Match filtered = index.findNearest(GenerationSemanticIndex.Query.forActivation(
                GenerationSemanticIndex.SemanticKind.RIVER_PROFILE,
                "rivers/new",
                origin,
                2,
                2L
        )).orElseThrow();
        assertEquals(new GenerationSemanticIndex.ChunkReference(1, 0, 2L), filtered.chunk());

        assertTrue(index.findNearest(GenerationSemanticIndex.Query.acrossActivations(
                GenerationSemanticIndex.SemanticKind.RIVER_PROFILE,
                "rivers/new",
                origin,
                0
        )).isEmpty());
        assertTrue(index.findNearest(GenerationSemanticIndex.Query.forActivation(
                GenerationSemanticIndex.SemanticKind.RIVER_PROFILE,
                "rivers/new",
                origin,
                2,
                99L
        )).isEmpty());
    }

    @Test
    public void nearestStructureUsesExactHorizontalDistanceAndStablePositionTies() throws Exception {
        Path dimensionRoot = temporaryFolder.newFolder("nearest-structures").toPath();
        GenerationSemanticIndex index = GenerationSemanticIndex.load(dimensionRoot);
        index.recordAndPersist(ChunkGenerationSemantics.builder(0, 0, 1L)
                .addStructure("iris:tower", -10, 90, 0)
                .addStructure("iris:tower", 10, 70, 0)
                .build());
        index.recordAndPersist(ChunkGenerationSemantics.builder(2, 0, 2L)
                .addStructure("iris:tower", 32, 64, 0)
                .build());
        ChunkGenerationSemantics.BlockPosition origin = new ChunkGenerationSemantics.BlockPosition(0, 0, 0);

        GenerationSemanticIndex.Match tied = index.findNearest(GenerationSemanticIndex.Query.acrossActivations(
                GenerationSemanticIndex.SemanticKind.STRUCTURE,
                "iris:tower",
                origin,
                4
        )).orElseThrow();
        assertEquals(new ChunkGenerationSemantics.BlockPosition(-10, 90, 0), tied.exactPosition().orElseThrow());

        GenerationSemanticIndex.Match filtered = index.findNearest(GenerationSemanticIndex.Query.forActivation(
                GenerationSemanticIndex.SemanticKind.STRUCTURE,
                "iris:tower",
                origin,
                4,
                2L
        )).orElseThrow();
        assertEquals(new ChunkGenerationSemantics.BlockPosition(32, 64, 0), filtered.exactPosition().orElseThrow());
    }

    @Test
    public void shardBytesAreDeterministicAcrossRecordInsertionOrder() throws Exception {
        Path firstRoot = temporaryFolder.newFolder("deterministic-first").toPath();
        Path secondRoot = temporaryFolder.newFolder("deterministic-second").toPath();
        List<ChunkGenerationSemantics> semantics = List.of(
                completeSemantics(0, 0, 1L),
                completeSemantics(4, 7, 2L),
                completeSemantics(31, 31, 3L)
        );
        GenerationSemanticIndex first = GenerationSemanticIndex.load(firstRoot);
        GenerationSemanticIndex second = GenerationSemanticIndex.load(secondRoot);
        for (ChunkGenerationSemantics record : semantics) {
            first.recordAndPersist(record);
        }
        for (int index = semantics.size() - 1; index >= 0; index--) {
            second.recordAndPersist(semantics.get(index));
        }

        assertArrayEquals(
                Files.readAllBytes(onlyShard(first.storageDirectory())),
                Files.readAllBytes(onlyShard(second.storageDirectory()))
        );
        assertArrayEquals(
                Files.readAllBytes(first.storageDirectory().resolve("index.isix")),
                Files.readAllBytes(second.storageDirectory().resolve("index.isix"))
        );
    }

    @Test
    public void failedPublicationLeavesMemoryAndPersistedStateUnchanged() throws Exception {
        Path dimensionRoot = temporaryFolder.newFolder("publication-failure").toPath();
        GenerationSemanticIndex initial = GenerationSemanticIndex.load(dimensionRoot);
        ChunkGenerationSemantics baseline = ChunkGenerationSemantics.builder(2, 3, 8L)
                .addSurfaceBiome("iris:forest")
                .build();
        initial.recordAndPersist(baseline);
        byte[] persisted = Files.readAllBytes(onlyShard(initial.storageDirectory()));

        GenerationSemanticIndex failing = GenerationSemanticIndex.load(
                dimensionRoot,
                (directory, regionX, regionZ, encoded) -> {
                    throw new IOException("simulated publication failure");
                }
        );
        ChunkGenerationSemantics update = ChunkGenerationSemantics.builder(2, 3, 8L)
                .addRegion("temperate")
                .build();

        IOException error = assertThrows(IOException.class, () -> failing.recordAndPersist(update));

        assertEquals("simulated publication failure", error.getMessage());
        assertEquals(Optional.of(baseline), failing.get(2, 3));
        assertArrayEquals(persisted, Files.readAllBytes(onlyShard(initial.storageDirectory())));
        assertEquals(Optional.of(baseline), GenerationSemanticIndex.load(dimensionRoot).get(2, 3));
    }

    @Test
    public void failedCatalogPublicationLeavesAnUnreferencedShardAndReopensPreviousTruth() throws Exception {
        Path dimensionRoot = temporaryFolder.newFolder("catalog-publication-failure").toPath();
        GenerationSemanticIndex initial = GenerationSemanticIndex.load(dimensionRoot);
        ChunkGenerationSemantics baseline = ChunkGenerationSemantics.builder(2, 3, 8L)
                .addSurfaceBiome("iris:forest")
                .build();
        initial.recordAndPersist(baseline);

        GenerationSemanticIndex failing = GenerationSemanticIndex.loadWithCatalogPublisher(
                dimensionRoot,
                (directory, regionKeys) -> {
                    throw new IOException("simulated catalog failure");
                }
        );
        ChunkGenerationSemantics update = ChunkGenerationSemantics.builder(32, 3, 9L)
                .addRegion("temperate")
                .build();

        IOException error = assertThrows(IOException.class, () -> failing.recordAndPersist(update));

        assertEquals("simulated catalog failure", error.getMessage());
        assertEquals(Optional.of(baseline), failing.get(2, 3));
        assertTrue(failing.get(32, 3).isEmpty());
        assertEquals(Optional.of(baseline), GenerationSemanticIndex.load(dimensionRoot).get(2, 3));
        assertTrue(GenerationSemanticIndex.load(dimensionRoot).get(32, 3).isEmpty());
        assertEquals(2, shardCount(initial.storageDirectory()));
    }

    @Test
    public void snapshotIsImmutableAndUsesStableChunkOrder() throws Exception {
        Path dimensionRoot = temporaryFolder.newFolder("snapshot").toPath();
        GenerationSemanticIndex index = GenerationSemanticIndex.load(dimensionRoot);
        ChunkGenerationSemantics last = completeSemantics(7, -3, 1L);
        ChunkGenerationSemantics first = completeSemantics(-2, 9, 2L);
        index.recordAndPersist(last);
        index.recordAndPersist(first);

        List<ChunkGenerationSemantics> snapshot = index.recordsSnapshot();

        assertEquals(List.of(first, last), snapshot);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
    }

    @Test
    public void checksumCorruptionAndTruncationFailClosed() throws Exception {
        Path checksumRoot = persistedRoot("corrupt-checksum");
        Path checksumShard = onlyShard(checksumRoot.resolve("iris/generation/semantics"));
        byte[] corrupted = Files.readAllBytes(checksumShard);
        corrupted[20] ^= 0x10;
        replaceReferencedShard(checksumRoot, checksumShard, corrupted);

        IOException checksumError = assertThrows(IOException.class, () -> GenerationSemanticIndex.load(checksumRoot));
        assertTrue(checksumError.getMessage().contains("checksum mismatch"));

        Path truncatedRoot = persistedRoot("corrupt-truncated");
        Path truncatedShard = onlyShard(truncatedRoot.resolve("iris/generation/semantics"));
        byte[] complete = Files.readAllBytes(truncatedShard);
        replaceReferencedShard(truncatedRoot, truncatedShard, Arrays.copyOf(complete, complete.length - 5));

        IOException truncatedError = assertThrows(IOException.class, () -> GenerationSemanticIndex.load(truncatedRoot));
        assertTrue(truncatedError.getMessage().contains("Invalid generation semantic shard"));
    }

    @Test
    public void catalogDetectsMissingShardsAndFailsClosedWhenItIsMissingOrCorrupt() throws Exception {
        Path missingShardRoot = persistedRoot("missing-shard");
        Files.delete(onlyShard(missingShardRoot.resolve("iris/generation/semantics")));
        IOException missingShard = assertThrows(
                IOException.class,
                () -> GenerationSemanticIndex.load(missingShardRoot)
        );
        assertTrue(missingShard.getMessage().contains("catalog references a missing shard"));

        Path missingPointerRoot = persistedRoot("missing-pointer");
        Files.delete(missingPointerRoot.resolve("iris/generation/semantics/r.0.0.isix"));
        IOException missingPointer = assertThrows(
                IOException.class,
                () -> GenerationSemanticIndex.load(missingPointerRoot)
        );
        assertTrue(missingPointer.getMessage().contains("catalog references a missing shard pointer"));

        Path missingCatalogRoot = persistedRoot("missing-catalog");
        Files.delete(missingCatalogRoot.resolve("iris/generation/semantics/index.isix"));
        IOException missingCatalog = assertThrows(
                IOException.class,
                () -> GenerationSemanticIndex.load(missingCatalogRoot)
        );
        assertTrue(missingCatalog.getMessage().contains("shard catalog is missing"));

        Path corruptCatalogRoot = persistedRoot("corrupt-catalog");
        Path catalog = corruptCatalogRoot.resolve("iris/generation/semantics/index.isix");
        byte[] corrupt = Files.readAllBytes(catalog);
        corrupt[8] ^= 0x01;
        Files.write(catalog, corrupt);
        IOException corruptCatalog = assertThrows(
                IOException.class,
                () -> GenerationSemanticIndex.load(corruptCatalogRoot)
        );
        assertTrue(corruptCatalog.getMessage().contains("semantic catalog"));
        assertTrue(corruptCatalog.getMessage().contains("checksum mismatch"));

        Path corruptPointerRoot = persistedRoot("corrupt-pointer");
        Path pointer = corruptPointerRoot.resolve("iris/generation/semantics/r.0.0.isix");
        byte[] corruptPointer = Files.readAllBytes(pointer);
        corruptPointer[16] ^= 0x01;
        Files.write(pointer, corruptPointer);
        IOException pointerError = assertThrows(
                IOException.class,
                () -> GenerationSemanticIndex.load(corruptPointerRoot)
        );
        assertTrue(pointerError.getMessage().contains("semantic shard pointer"));
        assertTrue(pointerError.getMessage().contains("checksum mismatch"));
    }

    @Test
    public void unknownSchemaFailsClosedEvenWithAValidChecksum() throws Exception {
        Path dimensionRoot = persistedRoot("unknown-schema");
        Path shard = onlyShard(dimensionRoot.resolve("iris/generation/semantics"));
        byte[] encoded = Files.readAllBytes(shard);
        encoded[4] = 0;
        encoded[5] = 7;
        rewriteChecksum(encoded);
        replaceReferencedShard(dimensionRoot, shard, encoded);

        IOException error = assertThrows(IOException.class, () -> GenerationSemanticIndex.load(dimensionRoot));

        assertTrue(error.getMessage().contains("unsupported format version 7"));
    }

    @Test
    public void sealedClaimJournalReplaysIntoKeyedAndRiverPostings() throws Exception {
        Path dimensionRoot = temporaryFolder.newFolder("journal-postings").toPath();
        GenerationSemanticIndex index = GenerationSemanticIndex.initialize(dimensionRoot);
        ChunkGenerationSemantics claim = ChunkGenerationSemantics.builder(64, -32, 7L)
                .addRegion("iris:temperate")
                .addRiverFeature(
                        "iris:rivers/default",
                        HydrologyFeatureType.RIFFLE,
                        17L,
                        1031,
                        71,
                        -503
                )
                .seal()
                .build();

        assertTrue(index.claimAndPersist(claim));
        GenerationSemanticIndex loaded = GenerationSemanticIndex.loadRequired(dimensionRoot);

        GenerationSemanticIndex.Match region = loaded.findNearest(
                GenerationSemanticIndex.Query.acrossActivations(
                        GenerationSemanticIndex.SemanticKind.REGION,
                        "iris:temperate",
                        new ChunkGenerationSemantics.BlockPosition(1024, 0, -512),
                        2
                )
        ).orElseThrow();
        assertEquals(new GenerationSemanticIndex.ChunkReference(64, -32, 7L), region.chunk());
        GenerationSemanticIndex.RiverMatch river = loaded.findNearestRiver(
                GenerationSemanticIndex.RiverQuery.acrossActivations(
                        java.util.Set.of(HydrologyFeatureType.RIFFLE),
                        "iris:rivers/default",
                        new ChunkGenerationSemantics.BlockPosition(1024, 0, -512),
                        2
                ),
                ChunkGenerationSemantics::sealed
        ).orElseThrow();
        assertEquals(new ChunkGenerationSemantics.BlockPosition(1031, 71, -503), river.occurrence().position());
        assertTrue(sealedClaims(loaded, 7L).contains(ChunkGenerationOwnership.packChunk(64, -32)));
    }

    @Test
    public void journalReplayTruncatesOnlyATornTerminalEntry() throws Exception {
        Path dimensionRoot = temporaryFolder.newFolder("journal-torn-tail").toPath();
        GenerationSemanticIndex index = GenerationSemanticIndex.initialize(dimensionRoot);
        index.claimAndPersist(sealedRegion(0, 0, 1L, "iris:first"));
        index.claimAndPersist(sealedRegion(1, 0, 1L, "iris:second"));
        Path journal = onlyJournal(index.storageDirectory());
        byte[] bytes = Files.readAllBytes(journal);
        Files.write(journal, Arrays.copyOf(bytes, bytes.length - 3));

        GenerationSemanticIndex loaded = GenerationSemanticIndex.loadRequired(dimensionRoot);

        assertEquals(Optional.of(sealedRegion(0, 0, 1L, "iris:first")), loaded.get(0, 0));
        assertTrue(loaded.get(1, 0).isEmpty());
        assertTrue(Files.size(journal) < bytes.length);
    }

    @Test
    public void journalReplayRejectsInteriorCorruption() throws Exception {
        Path dimensionRoot = temporaryFolder.newFolder("journal-corrupt-interior").toPath();
        GenerationSemanticIndex index = GenerationSemanticIndex.initialize(dimensionRoot);
        index.claimAndPersist(sealedRegion(0, 0, 1L, "iris:first"));
        index.claimAndPersist(sealedRegion(1, 0, 1L, "iris:second"));
        Path journal = onlyJournal(index.storageDirectory());
        byte[] bytes = Files.readAllBytes(journal);
        bytes[16] ^= 0x20;
        Files.write(journal, bytes);

        IOException error = assertThrows(IOException.class, () -> GenerationSemanticIndex.loadRequired(dimensionRoot));

        assertTrue(error.getMessage().contains("interior entry"));
    }

    @Test
    public void duplicateJournalEntriesReplayIdempotently() throws Exception {
        Path dimensionRoot = temporaryFolder.newFolder("journal-idempotent").toPath();
        GenerationSemanticIndex index = GenerationSemanticIndex.initialize(dimensionRoot);
        ChunkGenerationSemantics claim = sealedRegion(0, 0, 1L, "iris:temperate");
        index.claimAndPersist(claim);
        Path journal = onlyJournal(index.storageDirectory());
        byte[] entry = Files.readAllBytes(journal);
        Files.write(journal, entry, StandardOpenOption.APPEND);

        GenerationSemanticIndex loaded = GenerationSemanticIndex.loadRequired(dimensionRoot);

        assertEquals(1, loaded.recordCount());
        assertEquals(Optional.of(claim), loaded.get(0, 0));
        assertEquals(1, sealedClaims(loaded, 1L).size());
    }

    @Test
    public void boundedCacheEvictsAndReloadsUncompactedClaimsWithoutLosingTruth() throws Exception {
        Path dimensionRoot = temporaryFolder.newFolder("journal-cache-eviction").toPath();
        GenerationSemanticIndex index = GenerationSemanticIndex.initialize(dimensionRoot);
        for (int regionX = 0; regionX < 270; regionX++) {
            index.claimAndPersist(sealedRegion(regionX << 5, 0, 1L, "iris:temperate"));
            if (regionX < 10) {
                index.claimAndPersist(sealedRegion((regionX << 5) + 1, 0, 1L, "iris:temperate"));
            }
            assertTrue(index.cachedRegionCount() <= 64);
            assertTrue(index.cachedSummaryCount() <= 256);
        }

        assertEquals(Optional.of(sealedRegion(0, 0, 1L, "iris:temperate")), index.get(0, 0));
        assertTrue(index.cachedSummaryCount() <= 256);
        assertTrue(index.cachedRegionCount() <= 64);
        GenerationSemanticIndex loaded = GenerationSemanticIndex.loadRequired(dimensionRoot);
        assertEquals(280, loaded.recordCount());
        assertTrue(loaded.cachedSummaryCount() <= 256);
        assertEquals(280, sealedClaims(loaded, 1L).size());
        assertEquals(Optional.of(sealedRegion(0, 0, 1L, "iris:temperate")), loaded.get(0, 0));
    }

    @Test
    public void keyedNearestLoadsOnlyRegionsInThatPosting() throws Exception {
        Path dimensionRoot = temporaryFolder.newFolder("keyed-nearest-lazy").toPath();
        GenerationSemanticIndex index = GenerationSemanticIndex.initialize(dimensionRoot);
        for (int regionX = 0; regionX < 70; regionX++) {
            String region = regionX == 0 ? "iris:target" : "iris:unrelated";
            index.claimAndPersist(sealedRegion(regionX << 5, 0, 1L, region));
        }
        index.compactJournals();
        GenerationSemanticIndex loaded = GenerationSemanticIndex.loadRequired(dimensionRoot);
        long before = loaded.regionDecodeCount();
        assertEquals(0L, before);
        assertEquals(0, loaded.cachedRegionCount());

        GenerationSemanticIndex.Match match = loaded.findNearest(
                GenerationSemanticIndex.Query.acrossActivations(
                        GenerationSemanticIndex.SemanticKind.REGION,
                        "iris:target",
                        new ChunkGenerationSemantics.BlockPosition(8, 0, 8),
                        5000
                )
        ).orElseThrow();

        assertEquals(0, match.chunk().chunkX());
        assertEquals(before + 1L, loaded.regionDecodeCount());
        assertTrue(loaded.findNearest(GenerationSemanticIndex.Query.acrossActivations(
                GenerationSemanticIndex.SemanticKind.REGION,
                "iris:missing",
                new ChunkGenerationSemantics.BlockPosition(8, 0, 8),
                5000
        )).isEmpty());
        assertEquals(before + 1L, loaded.regionDecodeCount());
    }

    @Test
    public void lazilyDecodedUnknownRecordFlagsFailClosedEvenWithAValidChecksum() throws Exception {
        Path dimensionRoot = temporaryFolder.newFolder("unknown-record-flags").toPath();
        GenerationSemanticIndex index = GenerationSemanticIndex.load(dimensionRoot);
        index.recordAndPersist(ChunkGenerationSemantics.builder(0, 0, 1L).seal().build());
        Path shard = onlyShard(index.storageDirectory());
        byte[] encoded = Files.readAllBytes(shard);
        encoded[firstRecordFlagsOffset(encoded)] = 2;
        rewriteChecksum(encoded);
        replaceReferencedShard(dimensionRoot, shard, encoded);

        GenerationSemanticIndex loaded = GenerationSemanticIndex.load(dimensionRoot);
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> loaded.get(0, 0));

        assertTrue(error.getCause().getMessage().contains("unsupported semantic record flags 2"));
    }

    @Test
    public void symbolicLinkStorageAncestorsAreRejected() throws Exception {
        Path dimensionRoot = temporaryFolder.newFolder("symlink-dimension").toPath();
        Path external = temporaryFolder.newFolder("symlink-external").toPath();
        Files.createDirectories(external.resolve("generation/semantics"));
        Files.createSymbolicLink(dimensionRoot.resolve("iris"), external);

        assertThrows(IOException.class, () -> GenerationSemanticIndex.load(dimensionRoot));
    }

    @Test
    public void queriesValidateKindsKeysRadiiAndActivationFilters() throws Exception {
        Path dimensionRoot = temporaryFolder.newFolder("invalid-query").toPath();
        GenerationSemanticIndex index = GenerationSemanticIndex.load(dimensionRoot);
        ChunkGenerationSemantics.BlockPosition origin = new ChunkGenerationSemantics.BlockPosition(0, 0, 0);

        assertThrows(
                IllegalArgumentException.class,
                () -> GenerationSemanticIndex.Query.acrossActivations(
                        GenerationSemanticIndex.SemanticKind.REGION,
                        " Iris:Forest",
                        origin,
                        1
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> GenerationSemanticIndex.Query.acrossActivations(
                        GenerationSemanticIndex.SemanticKind.REGION,
                        "iris:forest",
                        origin,
                        -1
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> GenerationSemanticIndex.Query.forActivation(
                        GenerationSemanticIndex.SemanticKind.REGION,
                        "iris:forest",
                        origin,
                        1,
                        0L
                )
        );
        assertTrue(index.findNearest(GenerationSemanticIndex.Query.acrossActivations(
                GenerationSemanticIndex.SemanticKind.REGION,
                "iris:missing",
                origin,
                1
        )).isEmpty());
    }

    private GenerationSemanticIndex.Match requiredMatch(
            GenerationSemanticIndex index,
            GenerationSemanticIndex.SemanticKind kind,
            String key
    ) {
        return index.findNearest(GenerationSemanticIndex.Query.acrossActivations(
                kind,
                key,
                new ChunkGenerationSemantics.BlockPosition(-520, 80, -8),
                2
        )).orElseThrow();
    }

    private Path persistedRoot(String name) throws Exception {
        Path dimensionRoot = temporaryFolder.newFolder(name).toPath();
        GenerationSemanticIndex index = GenerationSemanticIndex.load(dimensionRoot);
        index.recordAndPersist(completeSemantics(0, 0, 1L));
        return dimensionRoot;
    }

    private static ChunkGenerationSemantics completeSemantics(int chunkX, int chunkZ, long activationId) {
        return ChunkGenerationSemantics.builder(chunkX, chunkZ, activationId)
                .addSurfaceBiome("iris:forest")
                .addSurfaceBiome("iris:wetlands")
                .addCaveBiome("iris:limestone_caves")
                .addRegion("temperate")
                .addRiverProfile("rivers/default")
                .addRiverFeature(
                        "rivers/default",
                        HydrologyFeatureType.RIFFLE,
                        5L,
                        (chunkX << 4) + 7,
                        70,
                        (chunkZ << 4) + 6
                )
                .addObject("iris:oak")
                .addStructure(
                        "minecraft:village_plains",
                        (chunkX << 4) + 8,
                        72,
                        (chunkZ << 4) + 9
                )
                .addPointOfInterest(new ChunkGenerationSemantics.PointOfInterest("buried_treasure",
                        new ChunkGenerationSemantics.BlockPosition((chunkX << 4) + 2, 91, (chunkZ << 4) + 5)))
                .seal()
                .build();
    }

    private static ChunkGenerationSemantics sealedRegion(
            int chunkX,
            int chunkZ,
            long activationId,
            String regionKey
    ) {
        return ChunkGenerationSemantics.builder(chunkX, chunkZ, activationId)
                .addRegion(regionKey)
                .seal()
                .build();
    }

    private static Path onlyShard(Path directory) throws Exception {
        try (Stream<Path> files = Files.list(directory)) {
            List<Path> shards = files.filter(path -> path.getFileName().toString().endsWith(".isem")).toList();
            assertEquals(1, shards.size());
            return shards.getFirst();
        }
    }

    private static Path onlyJournal(Path directory) throws Exception {
        try (Stream<Path> files = Files.list(directory)) {
            List<Path> journals = files.filter(path -> path.getFileName().toString().endsWith(".iswal"))
                    .toList();
            assertEquals(1, journals.size());
            return journals.getFirst();
        }
    }

    private static long shardCount(Path directory) throws Exception {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".isem")).count();
        }
    }

    private static void replaceReferencedShard(Path dimensionRoot, Path previousShard, byte[] encoded) throws Exception {
        int regionX = ByteBuffer.wrap(encoded, 8, Integer.BYTES).getInt();
        int regionZ = ByteBuffer.wrap(encoded, 12, Integer.BYTES).getInt();
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(encoded);
        String hash = HexFormat.of().formatHex(digest);
        Path directory = dimensionRoot.resolve("iris/generation/semantics");
        Path replacement = directory.resolve("r." + regionX + "." + regionZ + "." + hash + ".isem");
        Files.write(replacement, encoded);
        if (!replacement.equals(previousShard)) {
            Files.delete(previousShard);
        }

        Path pointer = directory.resolve("r." + regionX + "." + regionZ + ".isix");
        byte[] pointerBytes = Files.readAllBytes(pointer);
        System.arraycopy(digest, 0, pointerBytes, 16, digest.length);
        rewriteChecksum(pointerBytes);
        Files.write(pointer, pointerBytes);
    }

    private static void rewriteChecksum(byte[] encoded) {
        int bodyLength = encoded.length - Integer.BYTES;
        CRC32 checksum = new CRC32();
        checksum.update(encoded, 0, bodyLength);
        ByteBuffer.wrap(encoded, bodyLength, Integer.BYTES).putInt((int) checksum.getValue());
    }

    private static LongOpenHashSet sealedClaims(
            GenerationSemanticIndex index,
            long activationId
    ) throws IOException {
        LongOpenHashSet claims = new LongOpenHashSet();
        index.forEachSealedClaim(
                activationId,
                (chunkX, chunkZ) -> claims.add(ChunkGenerationOwnership.packChunk(chunkX, chunkZ))
        );
        return claims;
    }

    private static int firstRecordFlagsOffset(byte[] encoded) {
        ByteBuffer input = ByteBuffer.wrap(encoded);
        int paletteSize = input.getInt(20);
        int summaryLength = input.getInt(24);
        input.position(28 + summaryLength);
        for (int index = 0; index < paletteSize; index++) {
            int keyLength = Short.toUnsignedInt(input.getShort());
            input.position(input.position() + keyLength);
        }
        return input.position() + Short.BYTES + Long.BYTES;
    }
}

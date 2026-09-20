package art.arcane.iris.nativegen.v26_3_R1;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NativeStructureLocatePersistenceTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void storageProbeScansEachCandidateOnlyOnce() {
        AtomicInteger reads = new AtomicInteger();
        CompoundTag chunk = new CompoundTag();
        chunk.putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
        chunk.putString("Status", "full");
        CompoundTag start = new CompoundTag();
        start.putString("id", "minecraft:village_plains");
        start.putInt("references", 0);
        CompoundTag starts = new CompoundTag();
        starts.put("minecraft:village_plains", start);
        CompoundTag structures = new CompoundTag();
        structures.put("starts", starts);
        chunk.put("structures", structures);
        NativeStructureLocatePersistence.ProbeBudget budget =
                new NativeStructureLocatePersistence.ProbeBudget(
                        512,
                        (chunkPos, visitor) -> {
                            reads.incrementAndGet();
                            chunk.acceptAsRoot(visitor);
                            return CompletableFuture.completedFuture(null);
                        });
        ChunkPos chunkPos = new ChunkPos(7, -11);

        assertTrue(budget.acceptsStored(
                null, chunkPos, "minecraft:village_plains", true));
        assertTrue(budget.acceptsStored(
                null, chunkPos, "minecraft:village_plains", true));
        assertEquals(1, reads.get());
        assertEquals(1, budget.used());
    }

    @Test
    public void legacyStoredChunkIsDatafixedBeforeItsStartIsClassified() {
        AtomicBoolean datafixed = new AtomicBoolean();
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("DataVersion", 100);
        legacy.putString("Status", "full");
        NativeStructureLocatePersistence.ProbeBudget budget =
                new NativeStructureLocatePersistence.ProbeBudget(
                        512,
                        (chunkPos, visitor) -> {
                            legacy.acceptAsRoot(visitor);
                            return CompletableFuture.completedFuture(null);
                        },
                        (level, storedChunk) -> {
                            datafixed.set(true);
                            CompoundTag fixed = storedChunk.copy();
                            fixed.putInt("DataVersion",
                                    SharedConstants.getCurrentVersion().dataVersion().version());
                            CompoundTag start = new CompoundTag();
                            start.putString("id", "minecraft:village_plains");
                            start.putInt("references", 1);
                            CompoundTag starts = new CompoundTag();
                            starts.put("minecraft:village_plains", start);
                            CompoundTag structures = new CompoundTag();
                            structures.put("starts", starts);
                            fixed.put("structures", structures);
                            return fixed;
                        });

        assertFalse(budget.acceptsStored(
                null, new ChunkPos(3, 4), "minecraft:village_plains", true));
        assertTrue(datafixed.get());
    }

    @Test
    public void partialDatafixFailureFallsBackToSelectedChunkVerification() {
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("DataVersion", 100);
        NativeStructureLocatePersistence.ProbeBudget budget =
                new NativeStructureLocatePersistence.ProbeBudget(
                        512,
                        (chunkPos, visitor) -> {
                            legacy.acceptAsRoot(visitor);
                            return CompletableFuture.completedFuture(null);
                        },
                        (level, storedChunk) -> {
                            throw new IllegalStateException("broken partial datafix");
                        });

        assertTrue(budget.acceptsStored(
                null, new ChunkPos(9, -2), "minecraft:village_plains", true));
    }

    @Test
    public void missingStartsContainerFallsBackToSelectedChunkVerification() {
        CompoundTag chunk = currentChunk();
        NativeStructureLocatePersistence.ProbeBudget budget = budget(chunk);

        assertTrue(budget.acceptsStored(
                null, new ChunkPos(5, 8), "minecraft:village_plains", false));
        assertTrue(budget.acceptsStored(
                null, new ChunkPos(5, 8), "minecraft:village_plains", true));
    }

    @Test
    public void presentEmptyStartsContainerRejectsTheCandidateWithoutLoading() {
        CompoundTag chunk = currentChunk();
        CompoundTag structures = new CompoundTag();
        structures.put("starts", new CompoundTag());
        chunk.put("structures", structures);
        NativeStructureLocatePersistence.ProbeBudget budget = budget(chunk);

        assertFalse(budget.acceptsStored(
                null, new ChunkPos(-3, 2), "minecraft:village_plains", false));
    }

    @Test
    public void collisionTombstoneIsRejectedForNormalAndUnexploredLocate() {
        CompoundTag chunk = currentChunk();
        CompoundTag tombstone = new CompoundTag();
        tombstone.putString("id", "INVALID");
        CompoundTag starts = new CompoundTag();
        starts.put("minecraft:village_plains", tombstone);
        CompoundTag structures = new CompoundTag();
        structures.put("starts", starts);
        chunk.put("structures", structures);
        ChunkPos chunkPos = new ChunkPos(12, -9);

        assertFalse(budget(chunk).acceptsStored(
                null, chunkPos, "minecraft:village_plains", false));
        assertFalse(budget(chunk).acceptsStored(
                null, chunkPos, "minecraft:village_plains", true));
    }

    @Test
    public void malformedNonCompoundStartIsRejectedWithoutLoading() {
        CompoundTag chunk = currentChunk();
        CompoundTag starts = new CompoundTag();
        starts.putInt("minecraft:village_plains", 1);
        CompoundTag structures = new CompoundTag();
        structures.put("starts", starts);
        chunk.put("structures", structures);

        assertFalse(budget(chunk).acceptsStored(
                null, new ChunkPos(1, 1), "minecraft:village_plains", false));
    }

    @Test
    public void referencedStoredStartIsOnlyRejectedForUnexploredLocate() {
        CompoundTag chunk = currentChunk();
        CompoundTag start = new CompoundTag();
        start.putString("id", "minecraft:village_plains");
        start.putInt("references", 1);
        CompoundTag starts = new CompoundTag();
        starts.put("minecraft:village_plains", start);
        CompoundTag structures = new CompoundTag();
        structures.put("starts", starts);
        chunk.put("structures", structures);
        ChunkPos chunkPos = new ChunkPos(-4, -7);

        assertTrue(budget(chunk).acceptsStored(
                null, chunkPos, "minecraft:village_plains", false));
        assertFalse(budget(chunk).acceptsStored(
                null, chunkPos, "minecraft:village_plains", true));
    }

    private static CompoundTag currentChunk() {
        CompoundTag chunk = new CompoundTag();
        chunk.putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
        return chunk;
    }

    private static NativeStructureLocatePersistence.ProbeBudget budget(CompoundTag chunk) {
        return new NativeStructureLocatePersistence.ProbeBudget(
                512,
                (chunkPos, visitor) -> {
                    chunk.acceptAsRoot(visitor);
                    return CompletableFuture.completedFuture(null);
                });
    }
}

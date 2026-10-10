package art.arcane.iris.nativegen;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.NativeStructureVolume;
import art.arcane.volmlib.util.collection.KList;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderSet;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.SwampHutPiece;
import net.minecraft.world.level.levelgen.structure.structures.SwampHutStructure;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NativeStructureVolumeIndexTest {
    private static final String STRUCTURE_KEY = "minecraft:swamp_hut";

    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void assembledPiecesBecomeWorldSpacePieceVolumes() {
        StructureStart start = swampHut(0, 0, 4, 6);
        BoundingBox bounds = start.getPieces().getFirst().getBoundingBox();

        KList<NativeStructureVolume> volumes = NativeStructureVolumeIndex.appendPieces(null, STRUCTURE_KEY, start);

        assertEquals(1, volumes.size());
        NativeStructureVolume volume = volumes.getFirst();
        assertEquals(STRUCTURE_KEY, volume.structure());
        assertEquals(bounds.minX(), volume.minX());
        assertEquals(bounds.minY(), volume.minY());
        assertEquals(bounds.minZ(), volume.minZ());
        assertEquals(bounds.maxX(), volume.maxX());
        assertEquals(bounds.maxY(), volume.maxY());
        assertEquals(bounds.maxZ(), volume.maxZ());
    }

    @Test
    public void invalidStartsContributeNoVolumes() {
        assertNull(NativeStructureVolumeIndex.appendPieces(null, STRUCTURE_KEY, StructureStart.INVALID_START));
        assertNull(NativeStructureVolumeIndex.appendPieces(null, STRUCTURE_KEY, null));
    }

    @Test
    public void coldAndCachedQueriesResolveIdenticalVolumes() {
        CountingResolver resolver = new CountingResolver(0, 0);
        NativeStructureVolumeIndex index = NativeStructureVolumeIndex.forTesting(resolver);

        KList<NativeStructureVolume> cold = index.resolve(null, 0, 0, 15, 15);
        int coldResolutions = resolver.resolutions();
        KList<NativeStructureVolume> cached = index.resolve(null, 0, 0, 15, 15);

        assertTrue(coldResolutions > 0);
        assertFalse(cold.isEmpty());
        assertEquals(cold, cached);
        assertEquals(coldResolutions, resolver.resolutions());
    }

    @Test
    public void independentIndexesResolveIdenticalVolumesFromTheSameSeed() {
        NativeStructureVolumeIndex first = NativeStructureVolumeIndex.forTesting(new CountingResolver(0, 0));
        NativeStructureVolumeIndex second = NativeStructureVolumeIndex.forTesting(new CountingResolver(0, 0));

        assertEquals(first.resolve(null, 0, 0, 15, 15), second.resolve(null, 0, 0, 15, 15));
        assertEquals(first.resolve(null, -32, -32, 47, 47), second.resolve(null, -32, -32, 47, 47));
    }

    @Test
    public void volumesOutsideTheQueryRectAreExcluded() {
        NativeStructureVolumeIndex index = NativeStructureVolumeIndex.forTesting(new CountingResolver(0, 0));
        BoundingBox bounds = swampHut(0, 0, 4, 6).getPieces().getFirst().getBoundingBox();

        KList<NativeStructureVolume> hit = index.resolve(null, bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ());
        KList<NativeStructureVolume> miss = index.resolve(null,
                bounds.maxX() + 1, bounds.minZ(), bounds.maxX() + 8, bounds.maxZ());

        assertEquals(1, hit.size());
        assertTrue(miss.isEmpty());
    }

    @Test
    public void startsWithinTheOriginReachContributeAndBeyondItDoNot() {
        int reach = NativeStructureVolumeIndex.originReachChunks();
        NativeStructureVolumeIndex reachable = NativeStructureVolumeIndex.forTesting(new CountingResolver(reach, 0));
        NativeStructureVolumeIndex unreachable = NativeStructureVolumeIndex.forTesting(new CountingResolver(reach + 1, 0));

        assertFalse(reachable.resolve(null, 0, 0, 15, 15).isEmpty());
        assertTrue(unreachable.resolve(null, 0, 0, 15, 15).isEmpty());
    }

    @Test
    public void volumeResolutionNeverReadsChunkOrOwnershipState() throws Exception {
        String source = Files.readString(Path.of(System.getProperty("iris.nativeStructureVolumeIndexSource")));

        assertFalse(source.contains("StructureManager"));
        assertFalse(source.contains("getStartForStructure"));
        assertFalse(source.contains("getAllStarts"));
        assertFalse(source.contains("ChunkAccess"));
        assertFalse(source.contains("NativeStructureOwnershipStore"));
        assertFalse(source.contains("NativeStructureOwnershipRecovery"));
    }

    @Test
    public void vanillaVolumesAreGatedOnPackPolicy() throws Exception {
        String source = Files.readString(Path.of(System.getProperty("iris.nativeStructureVolumeIndexSource")));

        assertTrue(source.contains("if (!NativeStructureGenerationPolicy.generates(engine, structureId))"));
        assertTrue(source.contains("NativeStructureLayouts.shift(engine"));
        assertTrue(source.contains("isStructureChunk("));
    }

    private static StructureStart swampHut(int chunkX, int chunkZ, int x, int z) {
        Structure source = new SwampHutStructure(new Structure.StructureSettings(HolderSet.empty()));
        SwampHutPiece piece = new SwampHutPiece(RandomSource.create(17L), x, z);
        return new StructureStart(source, new ChunkPos(chunkX, chunkZ), 0, new PiecesContainer(List.of(piece)));
    }

    private static final class CountingResolver implements NativeStructureVolumeIndex.OriginResolver {
        private final AtomicInteger resolutions = new AtomicInteger();
        private final int originChunkX;
        private final int originChunkZ;

        private CountingResolver(int originChunkX, int originChunkZ) {
            this.originChunkX = originChunkX;
            this.originChunkZ = originChunkZ;
        }

        private int resolutions() {
            return resolutions.get();
        }

        @Override
        public KList<NativeStructureVolume> volumesAt(Engine engine, int chunkX, int chunkZ) {
            resolutions.incrementAndGet();
            if (chunkX != originChunkX || chunkZ != originChunkZ) {
                return NativeStructureVolume.NONE;
            }
            return NativeStructureVolumeIndex.appendPieces(null, STRUCTURE_KEY, swampHut(chunkX, chunkZ, 4, 6));
        }
    }
}

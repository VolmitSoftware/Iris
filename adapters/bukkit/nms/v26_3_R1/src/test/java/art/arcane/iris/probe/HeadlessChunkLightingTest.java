package art.arcane.iris.probe;

import ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk;
import ca.spottedleaf.moonrise.patches.starlight.light.StarLightInterface;
import ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray;
import ca.spottedleaf.moonrise.patches.starlight.util.SaveUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.bukkit.Bukkit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class HeadlessChunkLightingTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void nativeLightCrossesChunkEdgesAndRoundTripsSavedNibblesWithoutServer() throws Exception {
        Fixture fixture = new Fixture(-1, -2);
        int edgeX = fixture.target.x() * 16;
        int z = fixture.target.z() * 16 + 8;
        for (ProtoChunk chunk : fixture.chunks) {
            for (int x = 0; x < 16; x++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    chunk.setBlockState(new BlockPos(chunk.getPos().getMinBlockX() + x, 0,
                            chunk.getPos().getMinBlockZ() + localZ), Blocks.STONE.defaultBlockState(), 0);
                }
            }
        }
        fixture.set(new BlockPos(edgeX - 1, 0, z), Blocks.AIR.defaultBlockState());
        fixture.set(new BlockPos(edgeX - 1, -12, z), Blocks.GLOWSTONE.defaultBlockState());
        fixture.finishFeatures();
        assertNull(Bukkit.getServer());
        HeadlessChunkLighting.light(fixture.request(true));
        ProtoChunk target = fixture.chunk(fixture.target);
        assertEquals(14, light(target, false, new BlockPos(edgeX, -12, z)));
        assertEquals(13, light(target, false, new BlockPos(edgeX + 1, -12, z)));
        assertEquals(14, light(target, true, new BlockPos(edgeX, -1, z)));
        assertEquals(15, light(target, true, new BlockPos(edgeX, 1, z)));
        assertEquals(0, light(target, true, new BlockPos(edgeX + 15, -1, z)));
        assertEquals(ChunkStatus.LIGHT, target.getPersistedStatus());
        assertTrue(target.isLightCorrect());
        assertEquals(ChunkStatus.FEATURES, fixture.chunk(new ChunkPos(fixture.target.x() - 2, fixture.target.z())).getPersistedStatus());
        assertFalse(fixture.chunk(new ChunkPos(fixture.target.x() - 2, fixture.target.z())).isLightCorrect());

        try (MultiPackResourceManager resources = new MultiPackResourceManager(PackType.SERVER_DATA,
                List.of(ServerPacksSource.createVanillaPackSource().fullResources()));
             LevelStorageSource.LevelStorageAccess storage = LevelStorageSource.createDefault(
                     temporary.newFolder().toPath()).createAccess("lighting")) {
            StructureTemplateManager templates = new StructureTemplateManager(resources, storage,
                    DataFixers.getDataFixer(), BuiltInRegistries.BLOCK);
            HeadlessChunkSerialization.Context context = new HeadlessChunkSerialization.Context(
                    new StructurePieceSerializationContext(resources, fixture.registries, templates), fixture.containers, 0L);
            CompoundTag tag = HeadlessChunkSerialization.copyOf(target, context).write();
            assertEquals("minecraft:light", tag.getStringOr("Status", ""));
            assertEquals(SaveUtil.STARLIGHT_LIGHT_VERSION, tag.getIntOr(SaveUtil.STARLIGHT_VERSION_TAG, -1));
            assertTrue(tag.contains("isLightOn"));
            assertFalse(tag.getBooleanOr("isLightOn", true));
            assertThrows(ClassCastException.class, () -> SerializableChunkData.parse(target, fixture.containers, tag));
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, bytes);
            CompoundTag restored = NbtIo.readCompressed(new ByteArrayInputStream(bytes.toByteArray()), NbtAccounter.unlimitedHeap());
            assertEquals(tag, restored);
            List<CompoundTag> sections = restored.getListOrEmpty("sections").compoundStream().toList();
            assertEquals(target.getSectionsCount() + 2, sections.size());
            for (CompoundTag section : sections) {
                int index = section.getByteOr("Y", (byte) 0) - target.getMinSectionY() + 1;
                assertTrue(section.contains(SaveUtil.BLOCKLIGHT_STATE_TAG));
                assertTrue(section.contains(SaveUtil.SKYLIGHT_STATE_TAG));
                SWMRNibbleArray block = new SWMRNibbleArray(section.getByteArray("BlockLight").orElse(null),
                        section.getIntOr(SaveUtil.BLOCKLIGHT_STATE_TAG, 0));
                SWMRNibbleArray sky = new SWMRNibbleArray(section.getByteArray("SkyLight").orElse(null),
                        section.getIntOr(SaveUtil.SKYLIGHT_STATE_TAG, 0));
                assertNibbleEquals(((StarlightChunk) target).starlight$getBlockNibbles()[index], block);
                assertNibbleEquals(((StarlightChunk) target).starlight$getSkyNibbles()[index], sky);
            }
        }
        assertNull(Bukkit.getServer());
    }

    @Test
    public void nativeOpacityAndShapeRulesApplyAcrossTheBoundaryWithoutSkylight() {
        for (BlockState barrier : List.of(Blocks.GLASS.defaultBlockState(), Blocks.TINTED_GLASS.defaultBlockState(),
                Blocks.STONE_SLAB.defaultBlockState(), Blocks.STONE.defaultBlockState())) {
            Fixture fixture = new Fixture(0, 0);
            for (int x = -2; x <= 3; x++) {
                for (int y = -13; y <= -11; y++) {
                    for (int z = 7; z <= 9; z++) {
                        fixture.set(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState());
                    }
                }
            }
            for (int x = -1; x <= 2; x++) {
                fixture.set(new BlockPos(x, -12, 8), Blocks.AIR.defaultBlockState());
            }
            fixture.set(new BlockPos(-1, -12, 8), Blocks.GLOWSTONE.defaultBlockState());
            fixture.set(new BlockPos(0, -12, 8), barrier);
            fixture.finishFeatures();
            HeadlessChunkLighting.light(fixture.request(false));
            ProtoChunk target = fixture.chunk(fixture.target);
            int expected = barrier.is(Blocks.GLASS) || barrier.is(Blocks.STONE_SLAB) ? 13 : 0;
            assertEquals(barrier.toString(), expected, light(target, false, new BlockPos(1, -12, 8)));
            assertEquals(0, light(target, true, new BlockPos(1, 20, 8)));
        }
        assertNull(Bukkit.getServer());
    }

    @Test
    public void emptySectionsRetainImplicitSkyLightAcrossFullHeightAndPadding() {
        for (boolean sky : List.of(true, false)) {
            Fixture fixture = new Fixture(0, 0);
            fixture.finishFeatures();
            HeadlessChunkLighting.light(fixture.request(sky));
            ProtoChunk target = fixture.chunk(fixture.target);
            for (int y = target.getMinY() - 16; y <= target.getMaxY() + 16; y++) {
                assertEquals("sky y=" + y, sky ? 15 : 0, light(target, true, new BlockPos(8, y, 8)));
                assertEquals("block y=" + y, 0, light(target, false, new BlockPos(8, y, 8)));
            }
        }
    }

    @Test
    public void interruptionLeavesChunkUnlitAndRetainsInterrupt() {
        Fixture fixture = new Fixture(0, 0);
        fixture.finishFeatures();
        Thread.currentThread().interrupt();
        try {
            assertThrows(IllegalStateException.class, () -> HeadlessChunkLighting.light(fixture.request(true)));
            assertTrue(Thread.currentThread().isInterrupted());
            assertFalse(fixture.chunk(fixture.target).isLightCorrect());
            assertEquals(ChunkStatus.FEATURES, fixture.chunk(fixture.target).getPersistedStatus());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void rejectsIncompleteNeighborsBeforePromotingOrMutatingLight() {
        Fixture fixture = new Fixture(0, 0);
        fixture.finishFeatures();
        fixture.chunks.removeFirst();
        ProtoChunk target = fixture.chunk(fixture.target);
        SWMRNibbleArray[] previous = ((StarlightChunk) target).starlight$getBlockNibbles();
        assertThrows(IllegalArgumentException.class, () -> HeadlessChunkLighting.light(fixture.request(true)));
        assertEquals(ChunkStatus.FEATURES, target.getPersistedStatus());
        assertFalse(target.isLightCorrect());
        assertArrayEquals(previous, ((StarlightChunk) target).starlight$getBlockNibbles());
        fixture.chunks.add(new ProtoChunk(new ChunkPos(-2, -2), UpgradeData.EMPTY, target, fixture.containers, null));
        assertThrows(IllegalArgumentException.class, () -> HeadlessChunkLighting.light(fixture.request(true)));
    }

    @Test
    public void directStarlightRequiresLevelButNativeLightEnginesDoNot() {
        Fixture fixture = new Fixture(0, 0);
        ProtoChunk chunk = fixture.chunk(fixture.target);
        LightChunkGetter source = new LightChunkGetter() {
            @Override
            public LightChunk getChunkForLighting(int x, int z) {
                return chunk;
            }

            @Override
            public BlockGetter getLevel() {
                return chunk;
            }
        };
        assertThrows(ClassCastException.class, () -> new StarLightInterface(source, true, true, null));
        assertNull(Bukkit.getServer());
    }

    private static int light(ProtoChunk chunk, boolean sky, BlockPos position) {
        StarlightChunk light = (StarlightChunk) chunk;
        SWMRNibbleArray[] nibbles = sky ? light.starlight$getSkyNibbles() : light.starlight$getBlockNibbles();
        return nibbles[(position.getY() >> 4) - chunk.getMinSectionY() + 1]
                .getVisible(position.getX(), position.getY(), position.getZ());
    }

    private static void assertNibbleEquals(SWMRNibbleArray expected, SWMRNibbleArray actual) {
        for (int index = 0; index < 4096; index++) {
            assertEquals(expected.getVisible(index), actual.getVisible(index));
        }
    }

    private static final class Fixture {
        private final RegistryAccess registries = HeadlessNativeTestRegistries.get();
        private final PalettedContainerFactory containers = PalettedContainerFactory.create(registries);
        private final List<ProtoChunk> chunks = new ArrayList<>();
        private final ChunkPos target;

        private Fixture(int x, int z) {
            target = new ChunkPos(x, z);
            LevelHeightAccessor height = LevelHeightAccessor.create(-64, 128);
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    chunks.add(new ProtoChunk(new ChunkPos(x + dx, z + dz), UpgradeData.EMPTY, height, containers, null));
                }
            }
        }

        private ProtoChunk chunk(ChunkPos position) {
            for (ProtoChunk chunk : chunks) {
                if (chunk.getPos().equals(position)) {
                    return chunk;
                }
            }
            throw new IllegalArgumentException("Missing fixture chunk " + position);
        }

        private void set(BlockPos position, BlockState state) {
            chunk(new ChunkPos(position.getX() >> 4, position.getZ() >> 4)).setBlockState(position, state, 0);
        }

        private void finishFeatures() {
            for (ProtoChunk chunk : chunks) {
                chunk.setPersistedStatus(ChunkStatus.FEATURES);
            }
        }

        private HeadlessChunkLighting.Request request(boolean sky) {
            return new HeadlessChunkLighting.Request(chunks, Set.of(target), sky);
        }
    }
}

package art.arcane.iris.pack.validation;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.object.IObjectPlacer;
import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.structure.object.IrisObjectReplace;
import art.arcane.iris.structure.object.ObjectPlaceMode;
import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ContentGatePlaceholderTest {
    private static CompatFixtures.FakeRegistries registries;

    @BeforeClass
    public static void bindPlatform() {
        registries = CompatFixtures.registries("minecraft:sulfur", "minecraft:brimstone");
        CompatFixtures.bind(registries);
    }

    @AfterClass
    public static void unbindPlatform() {
        CompatFixtures.unbind();
    }

    private static ContentGate gate() {
        return new ContentGate(registries, Map.of(), new PackCompatReport());
    }

    @Test
    public void placeholderOnlyForMissingKeysAndOnlyWhenReady() {
        ContentGate gate = gate();
        PlatformBlockState present = gate.resolveBlockOrPlaceholder("STONE");
        assertFalse(MissingBlockState.isPlaceholder(present));
        assertEquals("minecraft:stone", present.key());

        PlatformBlockState missing = gate.resolveBlockOrPlaceholder("minecraft:sulfur[lit=true]");
        assertTrue(MissingBlockState.isPlaceholder(missing));
        assertEquals("minecraft:sulfur[lit=true]", missing.key());
        assertEquals("minecraft:sulfur", missing.materialKey());
        assertEquals("minecraft", missing.namespace());
        assertSame(missing, gate.resolveBlockOrPlaceholder("minecraft:sulfur[lit=true]"));
        assertNull(gate.resolveBlockOrPlaceholder(" "));

        ContentGate unbound = new ContentGate(null, Map.of(), new PackCompatReport());
        assertNull(unbound.resolveBlockOrPlaceholder("minecraft:sulfur"));
    }

    @Test
    public void placeholderBehavesAsAirButKeepsItsKey() {
        PlatformBlockState air = registries.air();
        MissingBlockState placeholder = MissingBlockState.of("minecraft:sulfur[lit=true,axis=y]", air);
        assertTrue(placeholder.isAir());
        assertFalse(placeholder.isSolid());
        assertFalse(placeholder.isCustom());
        assertFalse(placeholder.hasTileEntity());
        assertEquals(air.nativeHandle(), placeholder.nativeHandle());
        assertEquals("minecraft:sulfur", placeholder.missingKey());

        MissingBlockState exact = MissingBlockState.of("minecraft:sulfur[lit=true]", air);
        MissingBlockState other = MissingBlockState.of("minecraft:brimstone", air);
        assertTrue(exact.matches(placeholder));
        assertFalse(placeholder.matches(exact));
        assertFalse(exact.matches(other));
        assertFalse(exact.matches(air));
        assertFalse(air.matches(exact));
        assertEquals(MissingBlockState.of("minecraft:sulfur[lit=true]", air), exact);
        assertEquals(exact.hashCode(), MissingBlockState.of("minecraft:sulfur[lit=true]", air).hashCode());
        assertEquals("minecraft:sulfur[lit=true,axis=y]", exact.withProperty("axis", "y").key());
    }

    @Test
    public void blockDataPlaceholderFollowsTheChainBeforeGivingUp() throws Exception {
        IrisData data = IrisData.get(Files.createTempDirectory("iris-compat-placeholder").toFile());
        PlatformBlockState present = new IrisBlockData("minecraft:stone").getBlockDataOrPlaceholder(data);
        assertFalse(MissingBlockState.isPlaceholder(present));
        assertEquals("minecraft:stone", present.key());

        PlatformBlockState revived = new IrisBlockData("minecraft:sulfur").setBackup(new IrisBlockData("minecraft:sand")).getBlockDataOrPlaceholder(data);
        assertEquals("minecraft:sand", revived.key());
        assertFalse(MissingBlockState.isPlaceholder(revived));

        IrisBlockData missing = new IrisBlockData("minecraft:sulfur");
        missing.getData().put("lit", true);
        PlatformBlockState placeholder = missing.getBlockDataOrPlaceholder(data);
        assertTrue(MissingBlockState.isPlaceholder(placeholder));
        assertEquals("minecraft:sulfur[lit=true]", placeholder.key());
        assertTrue(missing.getBlockData(data).isAir());
        assertFalse(MissingBlockState.isPlaceholder(missing.getBlockData(data)));
    }

    @Test
    public void fullChanceEditRuleRewritesAMissingPaletteKeyDuringPlacement() throws Exception {
        File pack = Files.createTempDirectory("iris-compat-rescue").toFile();
        writeObject(pack, "rescue", "minecraft:sulfur[lit=true]");
        IrisData data = IrisData.get(pack);
        IrisObject object = data.getObjectLoader().load("rescue");
        assertNotNull(object);
        PlatformBlockState loaded = object.getBlocks().values().iterator().next();
        assertTrue(loaded.toString(), MissingBlockState.isPlaceholder(loaded));

        IrisObjectPlacement placement = placement();
        placement.getEdit().add(replace(1F, false, "minecraft:sulfur", "minecraft:stone"));
        RecordingPlacer placer = new RecordingPlacer();
        object.place(0, 64, 0, placer, placement, new RNG(7L), data);
        assertEquals(1, placer.writes.size());
        assertEquals("minecraft:stone", placer.writes.getFirst().key());

        IrisObjectPlacement exact = placement();
        exact.getEdit().add(replace(1F, true, "minecraft:sulfur[lit=true]", "minecraft:sand"));
        RecordingPlacer exactPlacer = new RecordingPlacer();
        object.place(0, 64, 0, exactPlacer, exact, new RNG(7L), data);
        assertEquals("minecraft:sand", exactPlacer.writes.getFirst().key());

        IrisObjectPlacement mismatch = placement();
        mismatch.getEdit().add(replace(1F, true, "minecraft:sulfur[lit=false]", "minecraft:sand"));
        RecordingPlacer mismatchPlacer = new RecordingPlacer();
        object.place(0, 64, 0, mismatchPlacer, mismatch, new RNG(7L), data);
        PlatformBlockState untouched = mismatchPlacer.writes.getFirst();
        assertTrue(MissingBlockState.isPlaceholder(untouched));
        assertTrue(untouched.isAir());
        assertEquals(registries.air().nativeHandle(), untouched.nativeHandle());
    }

    private static IrisObjectPlacement placement() {
        IrisObjectPlacement placement = new IrisObjectPlacement();
        placement.setPlace(new KList<>(List.of("rescue")));
        placement.setMode(ObjectPlaceMode.CENTER_HEIGHT);
        placement.setRequireSurfaceSupport(false);
        return placement;
    }

    private static IrisObjectReplace replace(float chance, boolean exact, String find, String to) {
        IrisObjectReplace rule = new IrisObjectReplace();
        rule.setChance(chance);
        rule.setExact(exact);
        rule.getFind().add(IrisBlockData.from(find));
        rule.getReplace().qclear();
        rule.getReplace().add(to);
        return rule;
    }

    /** A one-block V2 .iob whose only palette entry is {@code state}. */
    private static void writeObject(File pack, String key, String state) throws Exception {
        File file = new File(pack, "objects/" + key + ".iob");
        assertTrue(file.getParentFile().mkdirs());
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
            out.writeInt(1);
            out.writeInt(1);
            out.writeInt(1);
            out.writeUTF("Iris V2 IOB;");
            out.writeShort(1);
            out.writeUTF(state);
            out.writeInt(1);
            out.writeShort(0);
            out.writeShort(0);
            out.writeShort(0);
            out.writeShort(0);
            out.writeInt(0);
        }
    }

    private static final class RecordingPlacer implements IObjectPlacer {
        private final List<PlatformBlockState> writes = new ArrayList<>();

        @Override
        public int getHighest(int x, int z, IrisData data) {
            return 64;
        }

        @Override
        public int getHighest(int x, int z, IrisData data, boolean ignoreFluid) {
            return 64;
        }

        @Override
        public void set(int x, int y, int z, PlatformBlockState d) {
            writes.add(d);
        }

        @Override
        public PlatformBlockState get(int x, int y, int z) {
            return null;
        }

        @Override
        public boolean isPreventingDecay() {
            return false;
        }

        @Override
        public boolean isCarved(int x, int y, int z) {
            return false;
        }

        @Override
        public boolean isSolid(int x, int y, int z) {
            return false;
        }

        @Override
        public boolean isUnderwater(int x, int z) {
            return false;
        }

        @Override
        public int getFluidHeight() {
            return 0;
        }

        @Override
        public boolean isDebugSmartBore() {
            return false;
        }

        @Override
        public void setTile(int xx, int yy, int zz, TileData tile) {
        }

        @Override
        public <T> void setData(int xx, int yy, int zz, T data) {
        }

        @Override
        public <T> T getData(int xx, int yy, int zz, Class<T> t) {
            return null;
        }

        @Override
        public Engine getEngine() {
            return null;
        }
    }
}

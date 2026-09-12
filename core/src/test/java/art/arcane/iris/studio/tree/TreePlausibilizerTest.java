package art.arcane.iris.studio.tree;

import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TreePlausibilizerTest {
    @ClassRule
    public static final PlatformBinding PLATFORM = PlatformBinding.mockPlatform();

    private static final String OAK_LOG = "minecraft:oak_log[axis=y]";
    private static final String BIRCH_LOG = "minecraft:birch_log[axis=y]";
    private static final String OAK_LEAVES = "minecraft:oak_leaves[distance=7,persistent=true,waterlogged=false]";
    private static final String DIRT = "minecraft:dirt";
    private static final String MUSHROOM_STEM = "minecraft:mushroom_stem";
    private static final String MUSHROOM_BLOCK = "minecraft:red_mushroom_block";
    private static final int[][] NEIGHBORS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
    };

    private record Pos(int x, int y, int z) {
    }

    private static final class TestState implements PlatformBlockState {
        private final String key;

        private TestState(String raw) {
            this.key = canonical(raw);
        }

        static TestState of(String raw) {
            return new TestState(raw);
        }

        private static String canonical(String raw) {
            int bracket = raw.indexOf('[');
            if (bracket < 0) {
                return raw;
            }
            String material = raw.substring(0, bracket);
            String body = raw.substring(bracket + 1, raw.length() - 1);
            TreeMap<String, String> props = new TreeMap<>();
            for (String pair : body.split(",")) {
                if (pair.isEmpty()) {
                    continue;
                }
                int eq = pair.indexOf('=');
                props.put(pair.substring(0, eq), pair.substring(eq + 1));
            }
            return render(material, props);
        }

        private static String render(String material, TreeMap<String, String> props) {
            if (props.isEmpty()) {
                return material;
            }
            StringBuilder sb = new StringBuilder(material).append('[');
            boolean first = true;
            for (Map.Entry<String, String> e : props.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(e.getKey()).append('=').append(e.getValue());
            }
            return sb.append(']').toString();
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public String namespace() {
            return "minecraft";
        }

        @Override
        public PlatformBlockState withProperty(String name, String value) {
            int bracket = key.indexOf('[');
            String material = bracket < 0 ? key : key.substring(0, bracket);
            TreeMap<String, String> props = new TreeMap<>();
            if (bracket >= 0) {
                String body = key.substring(bracket + 1, key.length() - 1);
                for (String pair : body.split(",")) {
                    if (pair.isEmpty()) {
                        continue;
                    }
                    int eq = pair.indexOf('=');
                    props.put(pair.substring(0, eq), pair.substring(eq + 1));
                }
            }
            props.put(name, value);
            return new TestState(render(material, props));
        }

        @Override
        public Object nativeHandle() {
            return null;
        }

        @Override
        public boolean matches(PlatformBlockState state) {
            return state != null && key.equals(state.key());
        }

        @Override
        public boolean canPlaceOnto(PlatformBlockState onto) {
            return true;
        }

        @Override
        public boolean isAir() {
            return false;
        }

        @Override
        public boolean isSolid() {
            return true;
        }

        @Override
        public boolean isOccluding() {
            return false;
        }

        @Override
        public boolean isCustom() {
            return false;
        }

        @Override
        public boolean isFluid() {
            return false;
        }

        @Override
        public boolean isWater() {
            return false;
        }

        @Override
        public boolean isWaterLogged() {
            return false;
        }

        @Override
        public boolean isLit() {
            return false;
        }

        @Override
        public boolean isUpdatable() {
            return false;
        }

        @Override
        public boolean isFoliage() {
            return false;
        }

        @Override
        public boolean isTreeBlock() {
            return false;
        }

        @Override
        public boolean isFoliagePlantable() {
            return false;
        }

        @Override
        public boolean isDecorant() {
            return false;
        }

        @Override
        public boolean isStorage() {
            return false;
        }

        @Override
        public boolean isStorageChest() {
            return false;
        }

        @Override
        public boolean isOre() {
            return false;
        }

        @Override
        public boolean isDeepSlate() {
            return false;
        }

        @Override
        public boolean isVineBlock() {
            return false;
        }

        @Override
        public boolean hasTileEntity() {
            return false;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof TestState other && key.equals(other.key);
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }
    }

    private static IrisObject emptyObject() {
        return new IrisObject(32, 32, 32);
    }

    private static void put(IrisObject obj, int x, int y, int z, String state) {
        obj.getBlocks().put(new IrisBlockVector(x, y, z), TestState.of(state));
    }

    private static void trunk(IrisObject obj, int height) {
        for (int y = 0; y < height; y++) {
            put(obj, 0, y, 0, OAK_LOG);
        }
    }

    private static void blob(IrisObject obj, int cx, int cy, int cz, int r, String state) {
        for (int x = cx - r; x <= cx + r; x++) {
            for (int y = cy - r; y <= cy + r; y++) {
                for (int z = cz - r; z <= cz + r; z++) {
                    put(obj, x, y, z, state);
                }
            }
        }
    }

    private static Map<Pos, String> snapshot(IrisObject obj) {
        Map<Pos, String> out = new HashMap<>();
        for (Map.Entry<IrisBlockVector, PlatformBlockState> e : obj.getBlocks()) {
            IrisBlockVector v = e.getKey();
            out.put(new Pos(v.getBlockX(), v.getBlockY(), v.getBlockZ()), e.getValue().key());
        }
        return out;
    }

    private static String materialOf(String key) {
        int bracket = key.indexOf('[');
        return bracket < 0 ? key : key.substring(0, bracket);
    }

    private static String propertyOf(String key, String name) {
        int bracket = key.indexOf('[');
        if (bracket < 0) {
            return null;
        }
        String body = key.substring(bracket + 1, key.length() - 1);
        for (String pair : body.split(",")) {
            int eq = pair.indexOf('=');
            if (pair.substring(0, eq).equals(name)) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }

    private static boolean isWoodKey(String key) {
        String name = materialOf(key);
        return name.endsWith("_log") || name.endsWith("_wood") || name.endsWith("_hyphae");
    }

    private static boolean isLeafKey(String key) {
        return propertyOf(key, "distance") != null && propertyOf(key, "persistent") != null;
    }

    private static Set<Pos> woodPositions(Map<Pos, String> snap) {
        Set<Pos> out = new HashSet<>();
        for (Map.Entry<Pos, String> e : snap.entrySet()) {
            if (isWoodKey(e.getValue())) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    private static Set<Pos> leafPositions(Map<Pos, String> snap) {
        Set<Pos> out = new HashSet<>();
        for (Map.Entry<Pos, String> e : snap.entrySet()) {
            if (isLeafKey(e.getValue())) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    private static Map<Pos, Integer> mirrorDistances(Map<Pos, String> snap) {
        Set<Pos> wood = woodPositions(snap);
        Set<Pos> leaves = leafPositions(snap);
        Map<Pos, Integer> dist = new HashMap<>();
        Deque<Pos> queue = new ArrayDeque<>();
        for (Pos leaf : leaves) {
            for (int[] n : NEIGHBORS) {
                if (wood.contains(new Pos(leaf.x() + n[0], leaf.y() + n[1], leaf.z() + n[2]))) {
                    dist.put(leaf, 1);
                    queue.add(leaf);
                    break;
                }
            }
        }
        while (!queue.isEmpty()) {
            Pos pos = queue.poll();
            int d = dist.get(pos);
            if (d >= 6) {
                continue;
            }
            for (int[] n : NEIGHBORS) {
                Pos nk = new Pos(pos.x() + n[0], pos.y() + n[1], pos.z() + n[2]);
                if (leaves.contains(nk) && !dist.containsKey(nk)) {
                    dist.put(nk, d + 1);
                    queue.add(nk);
                }
            }
        }
        return dist;
    }

    @Test
    public void everyLeafLegalAfterApply() {
        IrisObject obj = emptyObject();
        trunk(obj, 8);
        blob(obj, 10, 8, 0, 2, OAK_LEAVES);

        TreePlausibilizer.Result r = TreePlausibilizer.apply(obj, 42L, TreePlausibilizer.DEFAULT_REACH);

        assertEquals(0, r.unreachableAfter());
        Map<Pos, String> snap = snapshot(obj);
        Map<Pos, Integer> dist = mirrorDistances(snap);
        Set<Pos> leaves = leafPositions(snap);
        assertFalse(leaves.isEmpty());
        for (Pos leaf : leaves) {
            String key = snap.get(leaf);
            assertEquals("persistent at " + leaf, "false", propertyOf(key, "persistent"));
            Integer d = dist.get(leaf);
            assertNotNull("mirror distance at " + leaf, d);
            assertTrue("distance in range at " + leaf, d >= 1 && d <= 6);
            assertEquals("stored distance at " + leaf, String.valueOf(d), propertyOf(key, "distance"));
        }
    }

    @Test
    public void branchWoodIsDominantTrunkMaterial() {
        IrisObject obj = emptyObject();
        trunk(obj, 8);
        put(obj, 3, 0, 0, BIRCH_LOG);
        put(obj, 3, 1, 0, BIRCH_LOG);
        blob(obj, 11, 7, 0, 1, OAK_LEAVES);
        Set<Pos> originalWood = woodPositions(snapshot(obj));

        TreePlausibilizer.Result r = TreePlausibilizer.apply(obj, 42L, TreePlausibilizer.DEFAULT_REACH);

        assertTrue(r.woodPlaced() + r.leavesConvertedToWood() > 0);
        Map<Pos, String> snap = snapshot(obj);
        for (Pos pos : woodPositions(snap)) {
            if (originalWood.contains(pos)) {
                continue;
            }
            assertEquals("new wood material at " + pos, "minecraft:oak_log", materialOf(snap.get(pos)));
        }
    }

    @Test
    public void clusterBeyondReachIsPinned() {
        IrisObject obj = emptyObject();
        trunk(obj, 4);
        blob(obj, 20, 2, 0, 1, OAK_LEAVES);

        TreePlausibilizer.Result r = TreePlausibilizer.apply(obj, 42L, 5);

        assertEquals(0, r.woodPlaced());
        assertEquals(0, r.leavesConvertedToWood());
        assertEquals(27, r.leavesPinnedPersistent());
        Map<Pos, String> snap = snapshot(obj);
        for (Pos leaf : leafPositions(snap)) {
            String key = snap.get(leaf);
            assertEquals("persistent at " + leaf, "true", propertyOf(key, "persistent"));
            assertEquals("distance at " + leaf, "7", propertyOf(key, "distance"));
        }
    }

    @Test
    public void reachZeroIsUnlimited() {
        IrisObject obj = emptyObject();
        trunk(obj, 4);
        blob(obj, 20, 2, 0, 1, OAK_LEAVES);

        TreePlausibilizer.Result r = TreePlausibilizer.apply(obj, 42L, 0);

        assertEquals(0, r.unreachableAfter());
        assertEquals(0, r.leavesPinnedPersistent());
        assertTrue(r.woodPlaced() > 0);
        Map<Pos, String> snap = snapshot(obj);
        for (Pos leaf : leafPositions(snap)) {
            assertEquals("persistent at " + leaf, "false", propertyOf(snap.get(leaf), "persistent"));
        }
    }

    @Test
    public void deterministicOutput() {
        IrisObject a = emptyObject();
        trunk(a, 8);
        blob(a, 10, 8, 0, 2, OAK_LEAVES);
        IrisObject b = emptyObject();
        trunk(b, 8);
        blob(b, 10, 8, 0, 2, OAK_LEAVES);

        TreePlausibilizer.apply(a, 42L, TreePlausibilizer.DEFAULT_REACH);
        TreePlausibilizer.apply(b, 42L, TreePlausibilizer.DEFAULT_REACH);

        assertEquals(snapshot(a), snapshot(b));
    }

    @Test
    public void noLeavesDeletedOrAdded() {
        IrisObject obj = emptyObject();
        trunk(obj, 8);
        blob(obj, 10, 8, 0, 2, OAK_LEAVES);
        Map<Pos, String> before = snapshot(obj);
        int initialLeaves = leafPositions(before).size();
        int initialSize = before.size();

        TreePlausibilizer.Result r = TreePlausibilizer.apply(obj, 42L, TreePlausibilizer.DEFAULT_REACH);

        Map<Pos, String> after = snapshot(obj);
        assertEquals(initialLeaves - r.leavesConvertedToWood(), leafPositions(after).size());
        assertEquals(initialSize + r.woodPlaced(), after.size());
        assertTrue(after.keySet().containsAll(before.keySet()));
    }

    @Test
    public void analyzeDoesNotMutate() {
        IrisObject a = emptyObject();
        trunk(a, 8);
        blob(a, 10, 8, 0, 2, OAK_LEAVES);
        IrisObject b = emptyObject();
        trunk(b, 8);
        blob(b, 10, 8, 0, 2, OAK_LEAVES);
        Map<Pos, String> before = snapshot(a);

        TreePlausibilizer.Result analyzed = TreePlausibilizer.analyze(a, 42L, TreePlausibilizer.DEFAULT_REACH);
        TreePlausibilizer.Result applied = TreePlausibilizer.apply(b, 42L, TreePlausibilizer.DEFAULT_REACH);

        assertEquals(before, snapshot(a));
        assertEquals(applied, analyzed);
        assertTrue(analyzed.mutated());
    }

    @Test
    public void loglessHedgePinsEverything() {
        IrisObject obj = emptyObject();
        blob(obj, 0, 0, 0, 1, OAK_LEAVES);

        TreePlausibilizer.Result r = TreePlausibilizer.apply(obj, 42L, TreePlausibilizer.DEFAULT_REACH);

        assertEquals(0, r.woodPlaced());
        assertEquals(27, r.leavesPinnedPersistent());
        Map<Pos, String> snap = snapshot(obj);
        for (Pos leaf : leafPositions(snap)) {
            String key = snap.get(leaf);
            assertEquals("persistent at " + leaf, "true", propertyOf(key, "persistent"));
            assertEquals("distance at " + leaf, "7", propertyOf(key, "distance"));
        }
    }

    @Test
    public void mushroomAndNetherCanopyUntouched() {
        IrisObject obj = emptyObject();
        for (int y = 0; y < 5; y++) {
            put(obj, 0, y, 0, MUSHROOM_STEM);
        }
        blob(obj, 0, 6, 0, 2, MUSHROOM_BLOCK);
        Map<Pos, String> before = snapshot(obj);

        TreePlausibilizer.Result r = TreePlausibilizer.apply(obj, 42L, TreePlausibilizer.DEFAULT_REACH);

        assertEquals(0, r.totalLeaves());
        assertFalse(r.mutated());
        assertEquals(before, snapshot(obj));
    }

    @Test
    public void branchNeverOverwritesForeignBlocks() {
        IrisObject obj = emptyObject();
        trunk(obj, 9);
        for (int y = 0; y <= 8; y++) {
            for (int z = -2; z <= 2; z++) {
                put(obj, 5, y, z, DIRT);
            }
        }
        blob(obj, 10, 4, 0, 1, OAK_LEAVES);

        TreePlausibilizer.apply(obj, 42L, TreePlausibilizer.DEFAULT_REACH);

        Map<Pos, String> snap = snapshot(obj);
        for (int y = 0; y <= 8; y++) {
            for (int z = -2; z <= 2; z++) {
                assertEquals("dirt intact at 5," + y + "," + z, DIRT, snap.get(new Pos(5, y, z)));
            }
        }
    }

    @Test
    public void exactDistancesWritten() {
        IrisObject obj = emptyObject();
        trunk(obj, 4);
        for (int x = 1; x <= 6; x++) {
            put(obj, x, 2, 0, OAK_LEAVES);
        }

        TreePlausibilizer.Result r = TreePlausibilizer.apply(obj, 42L, TreePlausibilizer.DEFAULT_REACH);

        assertEquals(0, r.woodPlaced());
        assertEquals(0, r.leavesConvertedToWood());
        assertEquals(6, r.distancesRewritten());
        Map<Pos, String> snap = snapshot(obj);
        for (int x = 1; x <= 6; x++) {
            String key = snap.get(new Pos(x, 2, 0));
            assertEquals("distance at x=" + x, String.valueOf(x), propertyOf(key, "distance"));
            assertEquals("persistent at x=" + x, "false", propertyOf(key, "persistent"));
        }
    }

    @Test
    public void applyIsIdempotent() {
        IrisObject obj = emptyObject();
        trunk(obj, 8);
        blob(obj, 10, 8, 0, 2, OAK_LEAVES);

        TreePlausibilizer.Result first = TreePlausibilizer.apply(obj, 42L, TreePlausibilizer.DEFAULT_REACH);
        Map<Pos, String> afterFirst = snapshot(obj);
        TreePlausibilizer.Result second = TreePlausibilizer.apply(obj, 42L, TreePlausibilizer.DEFAULT_REACH);

        assertTrue(first.mutated());
        assertFalse(second.mutated());
        assertEquals(afterFirst, snapshot(obj));
    }

    @Test
    public void seedOfIsStable() {
        assertEquals(TreePlausibilizer.seedOf("trees/oak/oakFancy6"), TreePlausibilizer.seedOf("trees/oak/oakFancy6"));
        assertTrue(TreePlausibilizer.seedOf("trees/oak/oakFancy6") != TreePlausibilizer.seedOf("trees/oak/oakFancy5"));
    }
}

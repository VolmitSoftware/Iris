package art.arcane.iris.pack.datapack.export.compile.object;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.TemplateBlock;
import art.arcane.iris.pack.datapack.export.TemplateVolume;
import art.arcane.iris.pack.datapack.export.VanillaBlockState;
import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;
import art.arcane.iris.pack.datapack.export.compile.WeightedState;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class ObjectVariantBaker {
    private static final Set<String> AIR = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");
    private static final String TALL_SEAGRASS = "minecraft:tall_seagrass";
    private static final Set<String> SEA_PLANTS = Set.of("minecraft:sea_pickle", "minecraft:seagrass", TALL_SEAGRASS);
    private static final String CORAL = "_coral";
    private static final String CORAL_FAN = "_coral_fan";
    private static final String WALL_FAN = "_coral_wall_fan";
    private static final String SNOW = "minecraft:snow";
    private static final int SNOW_LAYER_STEPS = 7;
    private static final int WARP_SPACING_X = 4099;
    private static final int WARP_SPACING_Z = 7919;
    private static final long EDIT_SALT = 0x5DEECE66DL;
    private static final long PICK_SALT = 0x2545F4914F6CDD1DL;
    private static final long SNOW_SALT = 0x9E3779B97F4A7C15L;
    private static final Comparator<Voxel> VOXEL_ORDER = Comparator
            .comparingInt(Voxel::y)
            .thenComparingInt(Voxel::x)
            .thenComparingInt(Voxel::z);

    public static String templateId(String namespace, String objectPath, VariantSpec spec, DataVersion target) {
        VanillaResourceIdentifier.requireNamespace(namespace, "template namespace");
        return namespace + ":object/" + objectPath + "/" + ContentIds.hash(spec.descriptor() + ";target=" + target.name());
    }

    public BakedVariant bake(String templateId, ObjectSource source, VariantSpec spec) {
        long seed = ContentIds.seed(spec.descriptor());
        TemplateVolume template = source.volume();
        List<Voxel> voxels = prepare(template, spec, seed, rootCells(source.roots()));
        if (!spec.reshapes()) {
            List<Voxel> kept = supported(voxels);
            return new BakedVariant(templateId, volume(template.sizeX(), template.sizeY(), template.sizeZ(), kept), source.baseY(),
                    roots(kept, source.roots().reach()));
        }
        int sizeX = template.sizeX();
        int sizeY = template.sizeY();
        int sizeZ = template.sizeZ();
        if (spec.scale() != 1) {
            sizeX = scaledSize(sizeX, spec.scale());
            sizeY = scaledSize(sizeY, spec.scale());
            sizeZ = scaledSize(sizeZ, spec.scale());
            voxels = scale(voxels, spec.scale(), sizeX, sizeY, sizeZ);
        }
        voxels = anchor(voxels, spec.rotation(), sizeX / 2, sizeY / 2, sizeZ / 2);
        if (spec.warp() != null) {
            voxels = warp(voxels, spec.warp());
        }
        if (spec.snow() > 0) {
            voxels = snow(voxels, spec.snow(), seed);
        }
        return reshaped(templateId, supported(voxels), source.baseY(), (int) Math.ceil(source.roots().reach() * spec.scale()));
    }

    public static List<BakedVariant> alignBases(List<BakedVariant> variants) {
        int lowest = 0;
        for (BakedVariant variant : variants) {
            lowest = Math.min(lowest, variant.baseY());
        }
        List<BakedVariant> aligned = new ArrayList<>(variants.size());
        for (BakedVariant variant : variants) {
            int lift = variant.baseY() - lowest;
            if (lift == 0) {
                aligned.add(variant);
                continue;
            }
            TemplateVolume volume = variant.volume();
            List<TemplateBlock> blocks = new ArrayList<>(volume.blocks().size());
            for (TemplateBlock block : volume.blocks()) {
                blocks.add(new TemplateBlock(block.x(), block.y() + lift, block.z(), block.state(), block.nbt()));
            }
            aligned.add(new BakedVariant(variant.id() + "_lift" + lift,
                    new TemplateVolume(volume.sizeX(), volume.sizeY() + lift, volume.sizeZ(), blocks), lowest, variant.roots().lift(lift)));
        }
        return aligned;
    }

    private List<Voxel> prepare(TemplateVolume source, VariantSpec spec, long seed, Set<Long> roots) {
        List<Voxel> voxels = new ArrayList<>(source.blocks().size());
        for (TemplateBlock block : source.blocks()) {
            if (AIR.contains(block.state().name())) {
                continue;
            }
            VanillaBlockState state = block.state();
            CompoundTag nbt = block.nbt();
            for (int index = 0; index < spec.edits().size(); index++) {
                ObjectEdit edit = spec.edits().get(index);
                if (!edit.deterministic() && unit(seed, block.x(), block.y(), block.z(), index, EDIT_SALT) >= edit.chance()) {
                    continue;
                }
                if (!edit.matches(state)) {
                    continue;
                }
                VanillaBlockState replacement = pick(edit, unit(seed, block.x(), block.y(), block.z(), index, PICK_SALT));
                if (replacement.name().equals(state.name())) {
                    state = merge(state, replacement);
                } else {
                    state = replacement;
                    nbt = null;
                }
            }
            if (AIR.contains(state.name())) {
                continue;
            }
            if (spec.preventLeafDecay()) {
                state = persistentLeaves(state);
            }
            voxels.add(new Voxel(block.x(), block.y(), block.z(), state, nbt, roots.contains(key(block.x(), block.y(), block.z()))));
        }
        return voxels;
    }

    private static Set<Long> rootCells(TreeRoots roots) {
        Set<Long> cells = new HashSet<>(roots.cells().size() * 2);
        for (TemplateBlock cell : roots.cells()) {
            cells.add(key(cell.x(), cell.y(), cell.z()));
        }
        return cells;
    }

    private static TreeRoots roots(List<Voxel> voxels, int reach) {
        List<TemplateBlock> cells = new ArrayList<>();
        for (Voxel voxel : voxels) {
            if (voxel.root()) {
                cells.add(new TemplateBlock(voxel.x(), voxel.y(), voxel.z(), voxel.state(), null));
            }
        }
        return cells.isEmpty() ? TreeRoots.NONE : new TreeRoots(cells, reach);
    }

    private static VanillaBlockState pick(ObjectEdit edit, double roll) {
        double cursor = roll * edit.totalWeight();
        for (WeightedState state : edit.replace()) {
            cursor -= state.weight();
            if (cursor < 0) {
                return state.state();
            }
        }
        return edit.replace().getLast().state();
    }

    private static VanillaBlockState merge(VanillaBlockState original, VanillaBlockState replacement) {
        Map<String, String> properties = new TreeMap<>(original.properties());
        properties.putAll(replacement.properties());
        return new VanillaBlockState(original.name(), properties);
    }

    private static VanillaBlockState persistentLeaves(VanillaBlockState state) {
        Map<String, String> properties = state.properties();
        if (!properties.containsKey("distance") || !"false".equals(properties.get("persistent"))) {
            return state;
        }
        Map<String, String> persistent = new TreeMap<>(properties);
        persistent.put("persistent", "true");
        return new VanillaBlockState(state.name(), persistent);
    }

    private static List<Voxel> supported(List<Voxel> voxels) {
        Map<Long, Voxel> occupied = new LinkedHashMap<>();
        int lowest = Integer.MAX_VALUE;
        for (Voxel voxel : voxels) {
            occupied.put(key(voxel.x(), voxel.y(), voxel.z()), voxel);
            lowest = Math.min(lowest, voxel.y());
        }
        List<Voxel> kept = new ArrayList<>(voxels.size());
        for (Voxel voxel : voxels) {
            if (anchored(voxel, occupied, lowest)) {
                kept.add(voxel);
            }
        }
        return kept;
    }

    private static boolean anchored(Voxel voxel, Map<Long, Voxel> occupied, int lowest) {
        String name = voxel.state().name();
        if (name.endsWith(WALL_FAN)) {
            int[] behind = switch (voxel.state().properties().getOrDefault("facing", "north")) {
                case "south" -> new int[]{0, -1};
                case "east" -> new int[]{-1, 0};
                case "west" -> new int[]{1, 0};
                default -> new int[]{0, 1};
            };
            return support(occupied.get(key(voxel.x() + behind[0], voxel.y(), voxel.z() + behind[1])));
        }
        if (!SEA_PLANTS.contains(name) && !name.endsWith(CORAL) && !name.endsWith(CORAL_FAN)) {
            return true;
        }
        Voxel below = occupied.get(key(voxel.x(), voxel.y() - 1, voxel.z()));
        if (name.equals(TALL_SEAGRASS) && "upper".equals(voxel.state().properties().get("half"))) {
            return below != null && below.state().name().equals(TALL_SEAGRASS);
        }
        return voxel.y() == lowest || support(below);
    }

    private static boolean support(Voxel voxel) {
        if (voxel == null) {
            return false;
        }
        String name = voxel.state().name();
        return !SEA_PLANTS.contains(name) && !name.endsWith(CORAL) && !name.endsWith(CORAL_FAN) && !name.endsWith(WALL_FAN);
    }

    private static int scaledSize(int size, double scale) {
        return Math.max(1, (int) Math.ceil(size * scale));
    }

    private static List<Voxel> scale(List<Voxel> voxels, double scale, int sizeX, int sizeY, int sizeZ) {
        List<Voxel> sorted = new ArrayList<>(voxels);
        sorted.sort(VOXEL_ORDER);
        Map<Long, Voxel> scaled = new LinkedHashMap<>();
        for (Voxel voxel : sorted) {
            int minX = Math.min(sizeX - 1, (int) Math.floor(voxel.x() * scale));
            int minY = Math.min(sizeY - 1, (int) Math.floor(voxel.y() * scale));
            int minZ = Math.min(sizeZ - 1, (int) Math.floor(voxel.z() * scale));
            int maxX = Math.min(sizeX - 1, Math.max(minX, (int) Math.ceil((voxel.x() + 1) * scale) - 1));
            int maxY = Math.min(sizeY - 1, Math.max(minY, (int) Math.ceil((voxel.y() + 1) * scale) - 1));
            int maxZ = Math.min(sizeZ - 1, Math.max(minZ, (int) Math.ceil((voxel.z() + 1) * scale) - 1));
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        scaled.put(key(x, y, z), new Voxel(x, y, z, voxel.state(), voxel.nbt(), voxel.root()));
                    }
                }
            }
        }
        return new ArrayList<>(scaled.values());
    }

    private static List<Voxel> anchor(List<Voxel> voxels, VariantSpec.Rotation rotation, int centerX, int centerY, int centerZ) {
        BlockStateRotation states = BlockStateRotation.of(rotation.x(), rotation.y(), rotation.z());
        double[] lift = rotate(0, centerY, 0, rotation);
        int rotatedCenterY = (int) Math.floor(lift[1]);
        List<Voxel> sorted = new ArrayList<>(voxels);
        sorted.sort(VOXEL_ORDER);
        Map<Long, Voxel> anchored = new LinkedHashMap<>();
        for (Voxel voxel : sorted) {
            double[] rotated = rotate(voxel.x() - centerX, voxel.y() - centerY, voxel.z() - centerZ, rotation);
            int x = (int) Math.floor(rotated[0]);
            int y = (int) Math.floor(rotated[1]) + rotatedCenterY;
            int z = (int) Math.floor(rotated[2]);
            anchored.put(key(x, y, z), new Voxel(x, y, z, states.apply(voxel.state()), voxel.nbt(), voxel.root()));
        }
        return new ArrayList<>(anchored.values());
    }

    private static double[] rotate(double x, double y, double z, VariantSpec.Rotation rotation) {
        double rx = x;
        double ry = y;
        double rz = z;
        if (rotation.x() != 0) {
            double angle = Math.toRadians(rotation.x());
            double cos = cos(angle);
            double sin = sin(angle);
            double nextY = cos * ry - sin * rz;
            rz = sin * ry + cos * rz;
            ry = nextY;
        }
        if (rotation.z() != 0) {
            double angle = Math.toRadians(rotation.z());
            double cos = cos(angle);
            double sin = sin(angle);
            double nextX = cos * rx - sin * ry;
            ry = sin * rx + cos * ry;
            rx = nextX;
        }
        if (rotation.y() != 0) {
            double angle = Math.toRadians(rotation.y());
            double cos = cos(angle);
            double sin = sin(angle);
            double nextX = cos * rx + sin * rz;
            rz = -sin * rx + cos * rz;
            rx = nextX;
        }
        return new double[]{rx, ry, rz};
    }

    private static double cos(double angle) {
        double value = Math.cos(angle);
        return Math.abs(value) < 1.0e-12 ? 0 : value;
    }

    private static double sin(double angle) {
        double value = Math.sin(angle);
        return Math.abs(value) < 1.0e-12 ? 0 : value;
    }

    private static List<Voxel> warp(List<Voxel> voxels, VariantWarp warp) {
        double originX = (double) warp.index() * WARP_SPACING_X;
        double originZ = (double) warp.index() * WARP_SPACING_Z;
        List<Voxel> sorted = new ArrayList<>(voxels);
        sorted.sort(VOXEL_ORDER);
        Map<Long, Voxel> warped = new LinkedHashMap<>();
        for (Voxel voxel : sorted) {
            double worldX = voxel.x() + originX;
            double worldZ = voxel.z() + originZ;
            int x = (int) (voxel.x() + warp.field().offset(worldX, voxel.y(), worldZ));
            int z = (int) (voxel.z() + warp.field().offset(worldZ, voxel.y(), worldX));
            warped.put(key(x, voxel.y(), z), new Voxel(x, voxel.y(), z, voxel.state(), voxel.nbt(), voxel.root()));
        }
        return new ArrayList<>(warped.values());
    }

    private static List<Voxel> snow(List<Voxel> voxels, double snow, long seed) {
        Map<Long, Voxel> tops = new TreeMap<>();
        Map<Long, Voxel> occupied = new LinkedHashMap<>();
        for (Voxel voxel : voxels) {
            occupied.put(key(voxel.x(), voxel.y(), voxel.z()), voxel);
            long column = key(voxel.x(), 0, voxel.z());
            Voxel top = tops.get(column);
            if (top == null || top.y() < voxel.y()) {
                tops.put(column, voxel);
            }
        }
        int steps = (int) (snow * SNOW_LAYER_STEPS);
        List<Voxel> covered = new ArrayList<>(voxels);
        for (Voxel top : tops.values()) {
            if (occupied.containsKey(key(top.x(), top.y() + 1, top.z()))) {
                continue;
            }
            int index = steps <= 0 ? 0 : (int) Math.floor(unit(seed, top.x(), top.y(), top.z(), 0, SNOW_SALT) * steps);
            int layers = Math.max(0, Math.min(index, SNOW_LAYER_STEPS)) + 1;
            covered.add(new Voxel(top.x(), top.y() + 1, top.z(),
                    new VanillaBlockState(SNOW, Map.of("layers", Integer.toString(layers))), null, false));
        }
        return covered;
    }

    private BakedVariant reshaped(String templateId, List<Voxel> voxels, int baseY, int reach) {
        if (voxels.isEmpty()) {
            return new BakedVariant(templateId, new TemplateVolume(1, 1, 1, List.of()), 0, TreeRoots.NONE);
        }
        int extentX = 0;
        int extentZ = 0;
        int minY = 0;
        int maxY = Integer.MIN_VALUE;
        for (Voxel voxel : voxels) {
            extentX = Math.max(extentX, Math.abs(voxel.x()));
            extentZ = Math.max(extentZ, Math.abs(voxel.z()));
            minY = Math.min(minY, voxel.y());
            maxY = Math.max(maxY, voxel.y());
        }
        List<Voxel> local = new ArrayList<>(voxels.size());
        for (Voxel voxel : voxels) {
            local.add(new Voxel(voxel.x() + extentX, voxel.y() - minY, voxel.z() + extentZ, voxel.state(), voxel.nbt(), voxel.root()));
        }
        TemplateVolume volume = volume(extentX * 2 + 1, maxY - minY + 1, extentZ * 2 + 1, local);
        return new BakedVariant(templateId, volume, minY + baseY, roots(local, reach));
    }

    private static TemplateVolume volume(int sizeX, int sizeY, int sizeZ, List<Voxel> voxels) {
        List<Voxel> sorted = new ArrayList<>(voxels);
        sorted.sort(VOXEL_ORDER);
        List<TemplateBlock> blocks = new ArrayList<>(sorted.size());
        for (Voxel voxel : sorted) {
            blocks.add(new TemplateBlock(voxel.x(), voxel.y(), voxel.z(), voxel.state(), voxel.nbt()));
        }
        return new TemplateVolume(sizeX, sizeY, sizeZ, blocks);
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
    }

    private static double unit(long seed, int x, int y, int z, int index, long salt) {
        long mixed = mix(seed ^ salt);
        mixed = mix(mixed ^ x);
        mixed = mix(mixed ^ ((long) y << 21));
        mixed = mix(mixed ^ ((long) z << 42));
        mixed = mix(mixed ^ index);
        return (mixed >>> 11) * 0x1.0p-53;
    }

    private static long mix(long value) {
        long mixed = value + 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }

    private record Voxel(int x, int y, int z, VanillaBlockState state, CompoundTag nbt, boolean root) {
    }
}

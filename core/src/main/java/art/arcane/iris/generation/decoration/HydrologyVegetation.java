package art.arcane.iris.generation.decoration;

import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.math.RNG;

import java.util.ArrayList;
import java.util.List;

public final class HydrologyVegetation {
    private static final long SALT = 0x4859445256454745L;
    private static final long GOLDEN = 0x9e3779b97f4a7c15L;
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final long seed;
    private final Engine engine;
    private final RNG floorRng;
    private final RNG surfaceRng;
    private final RNG shoreRng;

    public HydrologyVegetation(long seed, Engine engine) {
        this.seed = seed ^ SALT;
        this.engine = engine;
        this.floorRng = new RNG(DecoratorCore.partSeed(this.seed, IrisDecorationPart.SEA_FLOOR));
        this.surfaceRng = new RNG(DecoratorCore.partSeed(this.seed, IrisDecorationPart.SEA_SURFACE));
        this.shoreRng = new RNG(DecoratorCore.partSeed(this.seed, IrisDecorationPart.SHORE_LINE));
    }

    public void decorate(Hunk<NativeBlockState> output, int localX, int localZ, int x, int z, WaterRole role, Water water, IrisData data) {
        Placement placement = placement(role);
        if (placement == Placement.NONE) {
            return;
        }
        Decorators decorators = water.decorators(x, z);
        if (decorators.empty()) {
            return;
        }
        int ground = water.ground(x, z);
        if (ground < 0 || ground + 1 >= output.getHeight()) {
            return;
        }
        Column column = new Column(output, localX, localZ, x, z, ground, water, data);
        if (placement == Placement.SHORE) {
            decorateShore(column, decorators.shore);
            return;
        }
        int head = water.head(x, z);
        if (head <= ground || head + 1 >= output.getHeight()) {
            return;
        }
        decorateFloor(column, head, decorators.floor);
        if (placement == Placement.STILL && decorators.surface.length > 0 && still(water, x, z, head)) {
            decorateSurface(column, head, decorators.surface);
        }
    }

    private static Placement placement(WaterRole role) {
        return switch (role) {
            case CHANNEL, POND -> Placement.STILL;
            case CASCADE, WATERFALL -> Placement.FLOOR;
            case BANK, SHORE, POND_SHORE -> Placement.SHORE;
            case NONE, DAM, MOUTH, INLET -> Placement.NONE;
        };
    }

    private void decorateFloor(Column column, int head, IrisDecorator[] bucket) {
        int room = head - column.ground - 1;
        if (room < 1 || bucket.length == 0) {
            return;
        }
        IrisDecorator decorator = pick(bucket, floorRng, column, IrisDecorationPart.SEA_FLOOR);
        NativeBlockState support = column.output.get(column.localX, column.ground, column.localZ);
        if (decorator == null || !B.isSolid(support) || !column.accepts(decorator, support)) {
            return;
        }
        column.place(decorator, floorRng, column.ground + 1, room, Slot.FLOOR, null);
    }

    private void decorateSurface(Column column, int head, IrisDecorator[] bucket) {
        NativeBlockState support = column.output.get(column.localX, head, column.localZ);
        if (!isWaterSource(support)) {
            return;
        }
        IrisDecorator decorator = pick(bucket, surfaceRng, column, IrisDecorationPart.SEA_SURFACE);
        if (decorator == null || !column.accepts(decorator, support)) {
            return;
        }
        column.place(decorator, surfaceRng, head + 1, column.output.getHeight() - head - 1, Slot.SURFACE, null);
    }

    private void decorateShore(Column column, IrisDecorator[] bucket) {
        NativeBlockState support = column.output.get(column.localX, column.ground, column.localZ);
        if (bucket.length == 0 || support == null || !support.isSolid()) {
            return;
        }
        IrisDecorator decorator = pick(bucket, shoreRng, column, IrisDecorationPart.SHORE_LINE);
        if (decorator == null || !column.accepts(decorator, support)) {
            return;
        }
        column.place(decorator, shoreRng, column.ground + 1, column.output.getHeight() - column.ground - 1, Slot.SHORE, support);
    }

    private IrisDecorator pick(IrisDecorator[] bucket, RNG partRng, Column column, IrisDecorationPart part) {
        IrisDecorator picked = null;
        long choice = seed + part.ordinal();
        int count = 0;
        for (IrisDecorator decorator : bucket) {
            if (!decorator.passesChanceGate(partRng, column.x, column.z, column.data)) {
                continue;
            }
            count++;
            choice = mix(choice ^ mix(column.x * 0x632be59bd9b4e019L + column.z * 0x8cb92ba72f3d8dd7L + count));
            if (count == 1 || Long.remainderUnsigned(choice, count) == 0L) {
                picked = decorator;
            }
        }
        if (picked == null || picked.isForcePlace() || picked.getSlopeCondition().isDefault()
                || picked.getSlopeCondition().isValid(column.water.slope(column.x, column.z))) {
            return picked;
        }
        return null;
    }

    static boolean still(Water water, int x, int z, int head) {
        for (int[] side : SIDES) {
            int neighbourX = x + side[0];
            int neighbourZ = z + side[1];
            int neighbourHead = water.head(neighbourX, neighbourZ);
            if (neighbourHead != head && water.ground(neighbourX, neighbourZ) < neighbourHead) {
                return false;
            }
        }
        return true;
    }

    static boolean isWaterSource(NativeBlockState state) {
        if (state == null || !state.isWater()) {
            return false;
        }
        String level = IrisProceduralBlocks.propertyValue(state, "level");
        return level == null || level.equals("0");
    }

    static boolean holdsWater(NativeBlockState state) {
        return B.isFluid(state) || B.isWaterLogged(state) || IrisSurfaceDecorator.isAquaticPlacement(state);
    }

    static boolean plannedWater(Water water, int x, int z, int y) {
        int ground = water.ground(x, z);
        int top = water.head(x, z);
        if (y <= ground || top <= ground) {
            return false;
        }
        for (int[] side : SIDES) {
            int neighbourHead = water.head(x + side[0], z + side[1]);
            if (neighbourHead > top && water.ground(x + side[0], z + side[1]) < neighbourHead) {
                top = neighbourHead;
            }
        }
        return y <= top;
    }

    private static boolean twoBlock(NativeBlockState state) {
        String half = IrisProceduralBlocks.propertyValue(state, "half");
        return "lower".equals(half) || "upper".equals(half);
    }

    private static long mix(long value) {
        long mixed = (value + GOLDEN ^ (value + GOLDEN >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        return mixed ^ (mixed >>> 31);
    }

    public interface Water {
        int ground(int x, int z);

        int head(int x, int z);

        double slope(int x, int z);

        Decorators decorators(int x, int z);
    }

    private enum Placement {
        NONE,
        SHORE,
        FLOOR,
        STILL
    }

    private enum Slot {
        FLOOR,
        SURFACE,
        SHORE
    }

    private final class Column {
        private final Hunk<NativeBlockState> output;
        private final int localX;
        private final int localZ;
        private final int x;
        private final int z;
        private final int ground;
        private final Water water;
        private final IrisData data;

        private Column(Hunk<NativeBlockState> output, int localX, int localZ, int x, int z, int ground, Water water, IrisData data) {
            this.output = output;
            this.localX = localX;
            this.localZ = localZ;
            this.x = x;
            this.z = z;
            this.ground = ground;
            this.water = water;
            this.data = data;
        }

        private boolean accepts(IrisDecorator decorator, NativeBlockState support) {
            return decorator.isForcePlace() || DecoratorCore.allowsSurface(decorator, support, data);
        }

        private int stack(IrisDecorator decorator, RNG rng, int room) {
            return decorator.isStacking() ? Math.min(room, DecoratorCore.computeStack(decorator, rng, x, z, data, room)) : 1;
        }

        private void place(IrisDecorator decorator, RNG rng, int baseY, int room, Slot slot, NativeBlockState support) {
            NativeBlockState first = decorator.pickBlockData(rng, data, x, z);
            if (first == null) {
                return;
            }
            if (twoBlock(first)) {
                if (room >= 2) {
                    placePair(decorator, first, baseY, slot, support);
                }
                return;
            }
            int stack = stack(decorator, rng, room);
            NativeBlockState sampled = first;
            boolean sampledTop = false;
            for (int index = 0; index < stack; index++) {
                int y = baseY + index;
                boolean top = (stack == 1 ? 1D : (double) index / (stack - 1)) >= decorator.getTopThreshold();
                if (top != sampledTop) {
                    sampled = top ? decorator.pickBlockDataTop(rng, data, x, z) : decorator.pickBlockData(rng, data, x, z);
                    sampledTop = top;
                }
                NativeBlockState block = sampled == null ? null : fit(sampled, y, slot, decorator, index == 0 ? support : null);
                if (block == null) {
                    return;
                }
                output.set(localX, y, localZ, block);
            }
        }

        private void placePair(IrisDecorator decorator, NativeBlockState block, int y, Slot slot, NativeBlockState support) {
            NativeBlockState lower = fit(block.withProperty("half", "lower"), y, slot, decorator, support);
            NativeBlockState upper = lower == null ? null : fit(block.withProperty("half", "upper"), y + 1, slot, decorator, null);
            if (upper != null) {
                output.set(localX, y, localZ, lower);
                output.set(localX, y + 1, localZ, upper);
            }
        }

        private NativeBlockState fit(NativeBlockState block, int y, Slot slot, IrisDecorator decorator, NativeBlockState support) {
            NativeBlockState existing = output.get(localX, y, localZ);
            if (slot == Slot.FLOOR) {
                NativeBlockState placed = IrisProceduralBlocks.normalizeWaterlogging(block, existing, true);
                return isWaterSource(existing) && IrisSurfaceDecorator.isAquaticPlacement(placed) ? placed : null;
            }
            if (!B.isAir(existing) || holdsWater(block)) {
                return null;
            }
            if (slot == Slot.SURFACE) {
                return block;
            }
            if (besideWater(y) || support != null && !DecoratorCore.isValidShorelineSupport(decorator, block, support)) {
                return null;
            }
            return IrisSugarCane.canPlace(block, output, localX, y, localZ, x, z, engine) ? block : null;
        }

        private boolean besideWater(int y) {
            for (int[] side : SIDES) {
                int neighbourX = localX + side[0];
                int neighbourZ = localZ + side[1];
                if (neighbourX >= 0 && neighbourX < output.getWidth() && neighbourZ >= 0 && neighbourZ < output.getDepth()) {
                    if (holdsWater(output.get(neighbourX, y, neighbourZ))) {
                        return true;
                    }
                } else if (plannedWater(water, x + side[0], z + side[1], y)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static final class Decorators {
        public static final Decorators EMPTY = new Decorators(new IrisDecorator[0], new IrisDecorator[0], new IrisDecorator[0]);

        private final IrisDecorator[] floor;
        private final IrisDecorator[] surface;
        private final IrisDecorator[] shore;

        private Decorators(IrisDecorator[] floor, IrisDecorator[] surface, IrisDecorator[] shore) {
            this.floor = floor;
            this.surface = surface;
            this.shore = shore;
        }

        public static Decorators compile(List<IrisDecorator> decorators, IrisData data, String path) {
            HydrologySettings.requireDecorators(decorators, path);
            if (decorators == null || decorators.isEmpty()) {
                return EMPTY;
            }
            List<IrisDecorator> floor = new ArrayList<>();
            List<IrisDecorator> surface = new ArrayList<>();
            List<IrisDecorator> shore = new ArrayList<>();
            for (int index = 0; index < decorators.size(); index++) {
                IrisDecorator decorator = decorators.get(index);
                String field = path + "[" + index + "]";
                IrisDecorationPart part = decorator.getPartOf();
                requirePlaced(decorator.getPalette(), data, field + ".palette", part);
                requirePlaced(decorator.getTopPalette(), data, field + ".topPalette", part);
                requireKnown(decorator.getWhitelist(), data, field + ".whitelist");
                requireKnown(decorator.getBlacklist(), data, field + ".blacklist");
                switch (part) {
                    case SEA_FLOOR -> floor.add(decorator);
                    case SEA_SURFACE -> surface.add(decorator);
                    default -> shore.add(decorator);
                }
            }
            return new Decorators(floor.toArray(new IrisDecorator[0]), surface.toArray(new IrisDecorator[0]), shore.toArray(new IrisDecorator[0]));
        }

        public boolean empty() {
            return floor.length == 0 && surface.length == 0 && shore.length == 0;
        }

        private static void requirePlaced(List<IrisBlockData> blocks, IrisData data, String path, IrisDecorationPart part) {
            if (blocks == null) {
                return;
            }
            for (int index = 0; index < blocks.size(); index++) {
                String field = path + "[" + index + "]";
                String name = blocks.get(index).getBlock();
                NativeBlockState state = requireKnown(blocks.get(index), data, field);
                if (state.isFluid()) {
                    throw new IllegalArgumentException(field + " names the fluid '" + name + "'; water decorators never place fluids");
                }
                if (part == IrisDecorationPart.SEA_FLOOR && !waterCompatible(state)) {
                    throw new IllegalArgumentException(field + " names '" + name + "', which cannot stand in water; SEA_FLOOR decorators place"
                            + " only waterloggable or water plants such as seagrass, kelp, coral and sea pickles");
                }
                if (part != IrisDecorationPart.SEA_FLOOR && holdsWater(state)) {
                    throw new IllegalArgumentException(field + " names '" + name + "', which holds water; " + part
                            + " decorators grow in air and place only blocks without water, never waterlogged blocks, seagrass, kelp, sea pickles or coral");
                }
            }
        }

        private static void requireKnown(List<IrisBlockData> blocks, IrisData data, String path) {
            if (blocks == null) {
                return;
            }
            for (int index = 0; index < blocks.size(); index++) {
                requireKnown(blocks.get(index), data, path + "[" + index + "]");
            }
        }

        private static NativeBlockState requireKnown(IrisBlockData block, IrisData data, String path) {
            NativeBlockState state = block.getBlockData(data);
            if (state == null || state.isAir()) {
                throw new IllegalArgumentException(path + " names the block '" + block.getBlock()
                        + "', which does not exist on this server; water decorators never place air");
            }
            return state;
        }

        private static boolean waterCompatible(NativeBlockState state) {
            return IrisSurfaceDecorator.isAquaticPlacement(state) || IrisProceduralBlocks.propertyValue(state, "waterlogged") != null;
        }
    }
}

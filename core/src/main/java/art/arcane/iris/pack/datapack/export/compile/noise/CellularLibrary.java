package art.arcane.iris.pack.datapack.export.compile.noise;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class CellularLibrary {
    public static final double JITTER_RADIUS = 0.45D;
    public static final int JITTER_TURNS = 7;
    public static final int VOLUME_CELL_XZ = 4;
    public static final int VOLUME_CELL_Y = 8;
    private static final DataVersion TARGET = DataVersion.V26_3;
    private static final double COORDINATE_STEP = 64.0D;
    private static final double HORIZONTAL_EXTENT = 30_000_000.0D;
    private static final double VERTICAL_EXTENT = 4096.0D;
    private static final double MIN_CELL_BLOCKS = COORDINATE_STEP / 1.0e6D;
    private static final double MAX_CELL_BLOCKS = 1.0e6D;
    private static final double RANGE_LIMIT = 1.0e6D;
    private static final int POLAR_TURNS = 4;
    private static final String NAMESPACE_PREFIX = "cell/";
    private static final String COORDINATE_PREFIX = "cell/coord/";
    private static final String QUADRANT_SEGMENT = "/quadrant";

    private CellularLibrary() {
    }

    public static CellularOutputs build(String instanceId, double cellSizeBlocks, Field warpX, Field warpZ, CellularMode mode, NoiseRegistry registry) {
        VanillaResourceIdentifier.requirePath(instanceId, "cellular instance id");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(registry, "registry");
        requireCellSize(cellSizeBlocks);
        Construction construction = new Construction(NAMESPACE_PREFIX + instanceId + modeSegment(mode) + "/", instanceId, cellSizeBlocks, CellHashNoise.register(registry), warpX, warpZ);
        return switch (mode) {
            case GRID_3X3 -> construction.grid();
            case QUADRANT_2X2 -> construction.quadrant();
            case GRID_3X3X3 -> construction.volume();
        };
    }

    static String modeSegment(CellularMode mode) {
        return mode == CellularMode.QUADRANT_2X2 ? QUADRANT_SEGMENT : "";
    }

    static Field cellCoordinate(String name, Axis axis, double cellSizeBlocks, Field warpBlocks) {
        requireCellSize(cellSizeBlocks);
        Field base = blockCoordinate(axis);
        Field scaled = cellSizeBlocks == COORDINATE_STEP ? base : multiply(base, COORDINATE_STEP / cellSizeBlocks);
        Field warped = warpBlocks == null ? scaled : add(scaled, multiply(warpBlocks, 1.0D / cellSizeBlocks));
        return new Field.Shared(name, warped);
    }

    private static Field blockCoordinate(Axis axis) {
        double extent = axis == Axis.Y ? VERTICAL_EXTENT : HORIZONTAL_EXTENT;
        double value = extent / COORDINATE_STEP;
        Field positive = new Field.Coord(axis, Field.Tiling.CLAMP, 0.0D, extent, 0.0D, value);
        Field negative = new Field.Coord(axis, Field.Tiling.CLAMP, 0.0D, -extent, 0.0D, -value);
        return new Field.Shared(COORDINATE_PREFIX + axis.name().toLowerCase(Locale.ROOT), add(positive, negative));
    }

    private static void requireCellSize(double cellSizeBlocks) {
        if (!(cellSizeBlocks >= MIN_CELL_BLOCKS && cellSizeBlocks <= MAX_CELL_BLOCKS)) {
            throw new IllegalArgumentException("Cell size must be within [" + MIN_CELL_BLOCKS + ", " + MAX_CELL_BLOCKS + "] blocks, got " + cellSizeBlocks);
        }
    }

    private static Field add(Field left, Field right) {
        return new Field.Binary(Field.BinaryOp.ADD, left, right);
    }

    private static Field subtract(Field left, Field right) {
        return new Field.Binary(Field.BinaryOp.SUB, left, right);
    }

    private static Field multiply(Field left, double factor) {
        return new Field.Binary(Field.BinaryOp.MUL, left, new Field.Const(factor));
    }

    private static Field unary(Field.UnaryOp op, Field input) {
        return new Field.Unary(op, input);
    }

    private static String tag(int offset) {
        return switch (offset) {
            case -1 -> "m1";
            case 0 -> "0";
            case 1 -> "p1";
            default -> throw new IllegalArgumentException("Cell offset must be -1, 0 or 1, got " + offset);
        };
    }

    private static double planarJitterX(int offset, double fraction) {
        return offset + JITTER_RADIUS * Math.cos(2.0D * Math.PI * JITTER_TURNS * fraction);
    }

    private static double planarJitterZ(int offset, double fraction) {
        return offset + JITTER_RADIUS * Math.sin(2.0D * Math.PI * JITTER_TURNS * fraction);
    }

    private static double polar(double fraction) {
        double phase = POLAR_TURNS * fraction - Math.floor(POLAR_TURNS * fraction);
        return 1.0D - 2.0D * Math.abs(2.0D * phase - 1.0D);
    }

    private static double volumeJitterX(int offset, double fraction) {
        double height = polar(fraction);
        return offset + JITTER_RADIUS * Math.sqrt(Math.max(0.0D, 1.0D - height * height)) * Math.cos(2.0D * Math.PI * JITTER_TURNS * fraction);
    }

    private static double volumeJitterY(int offset, double fraction) {
        return offset + JITTER_RADIUS * polar(fraction);
    }

    private static double volumeJitterZ(int offset, double fraction) {
        double height = polar(fraction);
        return offset + JITTER_RADIUS * Math.sqrt(Math.max(0.0D, 1.0D - height * height)) * Math.sin(2.0D * Math.PI * JITTER_TURNS * fraction);
    }

    private static final class Construction {
        private final String prefix;
        private final double cellSizeBlocks;
        private final String hashId;
        private final Field plane;
        private final double volumePlane;
        private final Field xs;
        private final Field u;
        private final Field ik;
        private final Field zs;
        private final Field w;
        private final Field jk;

        private Construction(String prefix, String instanceId, double cellSizeBlocks, String hashId, Field warpX, Field warpZ) {
            this.prefix = prefix;
            this.cellSizeBlocks = cellSizeBlocks;
            this.hashId = hashId;
            this.plane = new Field.Const(CellHashNoise.plane(instanceId));
            this.volumePlane = CellHashNoise.volumePlane(instanceId);
            this.xs = cellCoordinate(prefix + "xs", Axis.X, cellSizeBlocks, warpX);
            Field i = shared("i", new Field.Round(Field.RoundMode.ROUND, xs, new Field.Const(1.0D)));
            this.u = shared("u", subtract(xs, i));
            this.ik = shared("ik", multiply(i, CellHashNoise.LATTICE_SPACING));
            this.zs = cellCoordinate(prefix + "zs", Axis.Z, cellSizeBlocks, warpZ);
            Field j = shared("j", new Field.Round(Field.RoundMode.ROUND, zs, new Field.Const(1.0D)));
            this.w = shared("w", subtract(zs, j));
            this.jk = shared("jk", multiply(j, CellHashNoise.LATTICE_SPACING));
        }

        private CellularOutputs grid() {
            List<Candidate> candidates = new ArrayList<>(9);
            for (int di = -1; di <= 1; di++) {
                for (int dj = -1; dj <= 1; dj++) {
                    candidates.add(planar(di, dj));
                }
            }
            return planarOutputs("", candidates);
        }

        private CellularOutputs quadrant() {
            Field signX = shared("s", unary(Field.UnaryOp.SIGN, u));
            Field signZ = shared("t", unary(Field.UnaryOp.SIGN, w));
            Field towardX = shared("s_u", subtract(signX, u));
            Field towardZ = shared("t_w", subtract(signZ, w));
            Field neighbourX = shared("ik_s", add(ik, new Field.Binary(Field.BinaryOp.MUL, signX, new Field.Const(CellHashNoise.LATTICE_SPACING))));
            Field neighbourZ = shared("jk_t", add(jk, new Field.Binary(Field.BinaryOp.MUL, signZ, new Field.Const(CellHashNoise.LATTICE_SPACING))));
            List<Candidate> candidates = List.of(
                    planar(0, 0),
                    quadrantCandidate("s0", neighbourX, jk, towardX, null),
                    quadrantCandidate("0t", ik, neighbourZ, null, towardZ),
                    quadrantCandidate("st", neighbourX, neighbourZ, towardX, towardZ));
            return planarOutputs("q_", candidates);
        }

        private CellularOutputs volume() {
            Field ys = cellCoordinate(prefix + "ys", Axis.Y, cellSizeBlocks, null);
            Field l = shared("l", new Field.Round(Field.RoundMode.ROUND, ys, new Field.Const(1.0D)));
            Field v = shared("v", subtract(ys, l));
            Field lk = shared("lk", add(multiply(l, CellHashNoise.LATTICE_SPACING), new Field.Const(volumePlane)));
            List<Candidate> candidates = new ArrayList<>(27);
            for (int di = -1; di <= 1; di++) {
                for (int dl = -1; dl <= 1; dl++) {
                    for (int dj = -1; dj <= 1; dj++) {
                        candidates.add(volumeCandidate(di, dl, dj, lk, v));
                    }
                }
            }
            Candidate winner = tournament("v_m", candidates);
            Extremes extremes = extremes("v_f", candidates);
            return new CellularOutputs(
                    volumeGrid(shared("v_value", CellHashNoise.uniform(winner.hash(), TARGET))),
                    volumeGrid(extremes.nearest()),
                    volumeGrid(extremes.second()),
                    volumeGrid(winnerCoordinate("v_winner_x", xs, winner.offsetX())),
                    volumeGrid(winnerCoordinate("v_winner_z", zs, winner.offsetZ())));
        }

        private Candidate planar(int di, int dj) {
            String tag = tag(di) + "_" + tag(dj);
            Field hash = shared("h_" + tag, CellHashNoise.sample(hashId, neighbour(ik, "ik", di), plane, neighbour(jk, "jk", dj)));
            Field offsetX = shared("vx_" + tag, subtract(CellHashNoise.periodic(hash, TARGET, fraction -> planarJitterX(di, fraction)), u));
            Field offsetZ = shared("vz_" + tag, subtract(CellHashNoise.periodic(hash, TARGET, fraction -> planarJitterZ(dj, fraction)), w));
            return new Candidate(shared("d_" + tag, natural(List.of(offsetX, offsetZ))), hash, offsetX, offsetZ);
        }

        private Candidate quadrantCandidate(String tag, Field shiftX, Field shiftZ, Field towardX, Field towardZ) {
            Field hash = shared("q_h_" + tag, CellHashNoise.sample(hashId, shiftX, plane, shiftZ));
            Field jitterX = CellHashNoise.periodic(hash, TARGET, fraction -> planarJitterX(0, fraction));
            Field jitterZ = CellHashNoise.periodic(hash, TARGET, fraction -> planarJitterZ(0, fraction));
            Field offsetX = shared("q_vx_" + tag, towardX == null ? subtract(jitterX, u) : add(jitterX, towardX));
            Field offsetZ = shared("q_vz_" + tag, towardZ == null ? subtract(jitterZ, w) : add(jitterZ, towardZ));
            return new Candidate(shared("q_d_" + tag, natural(List.of(offsetX, offsetZ))), hash, offsetX, offsetZ);
        }

        private Candidate volumeCandidate(int di, int dl, int dj, Field lk, Field v) {
            String tag = tag(di) + "_" + tag(dl) + "_" + tag(dj);
            Field hash = shared("v_h_" + tag, CellHashNoise.sample(hashId, neighbour(ik, "ik", di), neighbour(lk, "lk", dl), neighbour(jk, "jk", dj)));
            Field offsetX = shared("v_vx_" + tag, subtract(CellHashNoise.periodic(hash, TARGET, fraction -> volumeJitterX(di, fraction)), u));
            Field offsetY = shared("v_vy_" + tag, subtract(CellHashNoise.periodic(hash, TARGET, fraction -> volumeJitterY(dl, fraction)), v));
            Field offsetZ = shared("v_vz_" + tag, subtract(CellHashNoise.periodic(hash, TARGET, fraction -> volumeJitterZ(dj, fraction)), w));
            return new Candidate(shared("v_d_" + tag, natural(List.of(offsetX, offsetY, offsetZ))), hash, offsetX, offsetZ);
        }

        private CellularOutputs planarOutputs(String modePrefix, List<Candidate> candidates) {
            Candidate winner = tournament(modePrefix + "m", candidates);
            Extremes extremes = extremes(modePrefix + "f", candidates);
            return new CellularOutputs(
                    shared(modePrefix + "value", CellHashNoise.uniform(winner.hash(), TARGET)),
                    extremes.nearest(),
                    extremes.second(),
                    winnerCoordinate(modePrefix + "winner_x", xs, winner.offsetX()),
                    winnerCoordinate(modePrefix + "winner_z", zs, winner.offsetZ()));
        }

        private Candidate tournament(String tagPrefix, List<Candidate> candidates) {
            List<Candidate> round = candidates;
            int level = 0;
            while (round.size() > 1) {
                List<Candidate> next = new ArrayList<>((round.size() + 1) / 2);
                for (int index = 0; index < round.size(); index += 2) {
                    if (index + 1 < round.size()) {
                        next.add(match(tagPrefix + level + "_" + index / 2, round.get(index), round.get(index + 1)));
                    } else {
                        next.add(round.get(index));
                    }
                }
                round = next;
                level++;
            }
            return round.getFirst();
        }

        private Candidate match(String tag, Candidate first, Candidate second) {
            Field difference = shared(tag + "_diff", subtract(first.distance(), second.distance()));
            return new Candidate(
                    shared(tag + "_d", new Field.Binary(Field.BinaryOp.MIN, first.distance(), second.distance())),
                    shared(tag + "_h", pick(difference, first.hash(), second.hash())),
                    shared(tag + "_vx", pick(difference, first.offsetX(), second.offsetX())),
                    shared(tag + "_vz", pick(difference, first.offsetZ(), second.offsetZ())));
        }

        private Extremes extremes(String tagPrefix, List<Candidate> candidates) {
            Field first = candidates.get(0).distance();
            Field second = candidates.get(1).distance();
            Field nearest = shared(tagPrefix + "1_1", new Field.Binary(Field.BinaryOp.MIN, first, second));
            Field runnerUp = shared(tagPrefix + "2_1", new Field.Binary(Field.BinaryOp.MAX, first, second));
            for (int index = 2; index < candidates.size(); index++) {
                Field distance = candidates.get(index).distance();
                runnerUp = shared(tagPrefix + "2_" + index, new Field.Binary(Field.BinaryOp.MIN, runnerUp, new Field.Binary(Field.BinaryOp.MAX, nearest, distance)));
                nearest = shared(tagPrefix + "1_" + index, new Field.Binary(Field.BinaryOp.MIN, nearest, distance));
            }
            return new Extremes(nearest, runnerUp);
        }

        private Field winnerCoordinate(String name, Field cellCoordinate, Field offset) {
            return shared(name, multiply(add(cellCoordinate, offset), cellSizeBlocks));
        }

        private Field neighbour(Field base, String name, int offset) {
            if (offset == 0) {
                return base;
            }
            return shared(name + "_" + tag(offset), add(base, new Field.Const(offset * CellHashNoise.LATTICE_SPACING)));
        }

        private Field natural(List<Field> offsets) {
            Field manhattan = null;
            Field euclidean = null;
            for (Field offset : offsets) {
                Field absolute = unary(Field.UnaryOp.ABS, offset);
                Field square = unary(Field.UnaryOp.SQUARE, offset);
                manhattan = manhattan == null ? absolute : add(manhattan, absolute);
                euclidean = euclidean == null ? square : add(euclidean, square);
            }
            return add(manhattan, euclidean);
        }

        private Field pick(Field difference, Field first, Field second) {
            return new Field.RangeIf(difference, -RANGE_LIMIT, 0.0D, first, second);
        }

        private Field volumeGrid(Field field) {
            return new Field.Grid(field, VOLUME_CELL_XZ, VOLUME_CELL_Y);
        }

        private Field shared(String name, Field expr) {
            return new Field.Shared(prefix + name, expr);
        }
    }

    private record Candidate(Field distance, Field hash, Field offsetX, Field offsetZ) {
    }

    private record Extremes(Field nearest, Field second) {
    }
}

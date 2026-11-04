package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.underground.Grotto;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondKind;
import art.arcane.iris.generation.hydrology.water.PondMask;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachStyle;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class HydrologyPlanCodec {
    private static final int MAGIC = 0x49485950;
    private static final byte WINDOW = 'W';
    private static final byte REGION = 'R';
    private static final int POINT_BYTES = 5 * Integer.BYTES + 1;
    private static final int REACH_BYTES = 2 * Long.BYTES + 4 * Integer.BYTES + 2 * POINT_BYTES;
    private static final int POND_BYTES = 2 * Long.BYTES + 1 + 15 * Integer.BYTES;
    private static final int GROTTO_POINT_BYTES = 6 * Integer.BYTES;
    private static final int GROTTO_HOLE_BYTES = 4 * Integer.BYTES;
    private static final int GROTTO_BYTES = Long.BYTES + 6 * Integer.BYTES + 2 * GROTTO_POINT_BYTES;

    private HydrologyPlanCodec() {
    }

    public static byte[] encode(HydrologySettings settings, WindowPlan plan) {
        return encode(settings, WINDOW, plan.key().wx(), plan.key().wz(), plan.reaches(), plan.ponds(), plan.grottos());
    }

    public static byte[] encode(HydrologySettings settings, RegionPlan plan) {
        return encode(settings, REGION, plan.key().rx(), plan.key().rz(), plan.majorReaches(), plan.lakes(), List.of());
    }

    public static Optional<WindowPlan> decodeWindow(HydrologySettings settings, byte[] data) {
        return decode(settings, WINDOW, data).map(body -> new WindowPlan(new WindowKey(body.x(), body.z()), body.reaches(), body.ponds(), body.grottos()));
    }

    public static Optional<RegionPlan> decodeRegion(HydrologySettings settings, byte[] data) {
        return decode(settings, REGION, data).map(body -> new RegionPlan(new RegionKey(body.x(), body.z()), body.reaches(), body.ponds()));
    }

    private static byte[] encode(HydrologySettings settings, byte kind, int x, int z, List<Reach> reaches, List<Pond> ponds, List<Grotto> grottos) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeByte(kind);
            out.writeLong(settings.fingerprint());
            out.writeInt(x);
            out.writeInt(z);
            out.writeInt(reaches.size());
            for (Reach reach : reaches) {
                writeReach(out, reach, tierIndex(settings, reach));
            }
            out.writeInt(ponds.size());
            for (Pond pond : ponds) {
                writePond(out, pond);
            }
            out.writeInt(grottos.size());
            for (Grotto grotto : grottos) {
                writeGrotto(out, grotto);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return bytes.toByteArray();
    }

    private static int tierIndex(HydrologySettings settings, Reach reach) {
        if (reach.tier().equals(settings.tidalTier())) {
            return settings.tiers().size();
        }
        int index = settings.tiers().indexOf(reach.tier());
        if (index < 0) {
            throw new IllegalArgumentException("Reach " + reach.id() + " uses tier " + reach.tier().name() + ", which is not in the settings.");
        }
        return index;
    }

    private static void writeReach(DataOutputStream out, Reach reach, int tier) throws IOException {
        out.writeLong(reach.id());
        out.writeLong(reach.downstreamId());
        out.writeInt(tier);
        out.writeInt(Float.floatToRawIntBits(reach.catchment()));
        out.writeInt(reach.xs().length);
        writeFloats(out, reach.xs());
        writeFloats(out, reach.zs());
        for (int grade : reach.grade()) {
            out.writeInt(grade);
        }
        writeFloats(out, reach.halfWidth());
        writeFloats(out, reach.depth());
        for (ReachStyle style : reach.style()) {
            out.writeByte(style.code());
        }
        out.writeInt(reach.straightFalls().length);
        for (int fall : reach.straightFalls()) {
            out.writeInt(fall);
        }
    }

    private static void writePond(DataOutputStream out, Pond pond) throws IOException {
        out.writeLong(pond.id());
        out.writeByte(pond.kind().code());
        out.writeInt(pond.level());
        out.writeInt(pond.floor());
        out.writeInt(pond.spillX());
        out.writeInt(pond.spillZ());
        out.writeInt(Float.floatToRawIntBits(pond.spillHeight()));
        PondMask mask = pond.mask();
        out.writeInt(mask.originX());
        out.writeInt(mask.originZ());
        out.writeInt(mask.step());
        out.writeInt(mask.width());
        out.writeInt(mask.depth());
        out.writeInt(mask.bits().length);
        for (long word : mask.bits()) {
            out.writeLong(word);
        }
        out.writeLong(pond.outletReachId());
        out.writeInt(pond.damRaise());
        if (pond.fluid() == null) {
            out.writeInt(-1);
        } else {
            byte[] fluid = pond.fluid().getBytes(StandardCharsets.UTF_8);
            out.writeInt(fluid.length);
            out.write(fluid);
        }
        out.writeInt(pond.floorX());
        out.writeInt(pond.floorZ());
    }

    private static void writeGrotto(DataOutputStream out, Grotto grotto) throws IOException {
        out.writeLong(grotto.id());
        out.writeInt(grotto.seaLevel());
        out.writeInt(Float.floatToRawIntBits(grotto.roughness()));
        out.writeInt(grotto.coast());
        out.writeInt(grotto.xs().length);
        writeFloats(out, grotto.xs());
        writeFloats(out, grotto.zs());
        writeFloats(out, grotto.halfWidth());
        writeFloats(out, grotto.roof());
        writeFloats(out, grotto.floor());
        writeFloats(out, grotto.lift());
        out.writeInt(grotto.holeX().length);
        writeFloats(out, grotto.holeX());
        writeFloats(out, grotto.holeZ());
        writeFloats(out, grotto.holeRadius());
        for (int top : grotto.holeTop()) {
            out.writeInt(top);
        }
        out.writeInt(grotto.plugs().length);
        for (long plug : grotto.plugs()) {
            out.writeLong(plug);
        }
    }

    private static void writeFloats(DataOutputStream out, float[] values) throws IOException {
        for (float value : values) {
            out.writeInt(Float.floatToRawIntBits(value));
        }
    }

    private static Optional<Body> decode(HydrologySettings settings, byte kind, byte[] data) {
        try {
            ByteBuffer in = ByteBuffer.wrap(data);
            if (in.getInt() != MAGIC || in.get() != kind || in.getLong() != settings.fingerprint()) {
                return Optional.empty();
            }
            int x = in.getInt();
            int z = in.getInt();
            int reachCount = count(in, REACH_BYTES);
            List<Reach> reaches = new ArrayList<>(reachCount);
            for (int index = 0; index < reachCount; index++) {
                reaches.add(readReach(in, settings));
            }
            int pondCount = count(in, POND_BYTES);
            List<Pond> ponds = new ArrayList<>(pondCount);
            for (int index = 0; index < pondCount; index++) {
                ponds.add(readPond(in));
            }
            int grottoCount = count(in, GROTTO_BYTES);
            List<Grotto> grottos = new ArrayList<>(grottoCount);
            for (int index = 0; index < grottoCount; index++) {
                grottos.add(readGrotto(in));
            }
            if (in.hasRemaining()) {
                return Optional.empty();
            }
            return Optional.of(new Body(x, z, reaches, ponds, grottos));
        } catch (BufferUnderflowException | IllegalArgumentException | IllegalStateException malformed) {
            return Optional.empty();
        }
    }

    private static Grotto readGrotto(ByteBuffer in) {
        long id = in.getLong();
        int seaLevel = in.getInt();
        float roughness = Float.intBitsToFloat(in.getInt());
        int coast = in.getInt();
        int points = count(in, GROTTO_POINT_BYTES);
        float[] xs = readFloats(in, points);
        float[] zs = readFloats(in, points);
        float[] halfWidth = readFloats(in, points);
        float[] roof = readFloats(in, points);
        float[] floor = readFloats(in, points);
        float[] lift = readFloats(in, points);
        int holes = count(in, GROTTO_HOLE_BYTES);
        float[] holeX = readFloats(in, holes);
        float[] holeZ = readFloats(in, holes);
        float[] holeRadius = readFloats(in, holes);
        int[] holeTop = new int[holes];
        for (int hole = 0; hole < holes; hole++) {
            holeTop[hole] = in.getInt();
        }
        long[] plugs = new long[count(in, Long.BYTES)];
        for (int plug = 0; plug < plugs.length; plug++) {
            plugs[plug] = in.getLong();
        }
        return new Grotto(id, seaLevel, roughness, coast, xs, zs, halfWidth, roof, floor, lift, holeX, holeZ, holeRadius, holeTop, plugs);
    }

    private static Reach readReach(ByteBuffer in, HydrologySettings settings) {
        long id = in.getLong();
        long downstreamId = in.getLong();
        List<HydrologyTier> tiers = settings.tiers();
        int tierIndex = index(in.getInt(), tiers.size() + 1);
        HydrologyTier tier = tierIndex == tiers.size() ? settings.tidalTier() : tiers.get(tierIndex);
        float catchment = Float.intBitsToFloat(in.getInt());
        int points = count(in, POINT_BYTES);
        float[] xs = readFloats(in, points);
        float[] zs = readFloats(in, points);
        int[] grade = new int[points];
        for (int point = 0; point < points; point++) {
            grade[point] = in.getInt();
        }
        float[] halfWidth = readFloats(in, points);
        float[] depth = readFloats(in, points);
        ReachStyle[] style = new ReachStyle[points];
        for (int point = 0; point < points; point++) {
            style[point] = ReachStyle.fromCode(Byte.toUnsignedInt(in.get()));
        }
        int[] straightFalls = new int[count(in, Integer.BYTES)];
        for (int fall = 0; fall < straightFalls.length; fall++) {
            straightFalls[fall] = in.getInt();
        }
        return new Reach(id, tier, downstreamId, xs, zs, grade, halfWidth, depth, style, catchment, straightFalls);
    }

    private static Pond readPond(ByteBuffer in) {
        long id = in.getLong();
        PondKind kind = PondKind.fromCode(Byte.toUnsignedInt(in.get()));
        int level = in.getInt();
        int floor = in.getInt();
        int spillX = in.getInt();
        int spillZ = in.getInt();
        float spillHeight = Float.intBitsToFloat(in.getInt());
        int originX = in.getInt();
        int originZ = in.getInt();
        int step = in.getInt();
        int width = in.getInt();
        int depth = in.getInt();
        long[] bits = new long[count(in, Long.BYTES)];
        for (int word = 0; word < bits.length; word++) {
            bits[word] = in.getLong();
        }
        PondMask mask = new PondMask(originX, originZ, step, width, depth, bits);
        long outletReachId = in.getLong();
        int damRaise = in.getInt();
        String fluid = readString(in);
        return new Pond(id, kind, level, floor, spillX, spillZ, spillHeight, mask, outletReachId, damRaise, fluid, in.getInt(), in.getInt());
    }

    private static String readString(ByteBuffer in) {
        int length = in.getInt();
        if (length == -1) {
            return null;
        }
        byte[] bytes = new byte[count(length, in, 1)];
        in.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static float[] readFloats(ByteBuffer in, int count) {
        float[] values = new float[count];
        for (int index = 0; index < count; index++) {
            values[index] = Float.intBitsToFloat(in.getInt());
        }
        return values;
    }

    private static int count(ByteBuffer in, int elementBytes) {
        return count(in.getInt(), in, elementBytes);
    }

    private static int count(int count, ByteBuffer in, int elementBytes) {
        if (count < 0 || (long) count * elementBytes > in.remaining()) {
            throw new IllegalArgumentException("Plan data declares " + count + " entries with " + in.remaining() + " bytes left.");
        }
        return count;
    }

    private static int index(int value, int size) {
        if (value < 0 || value >= size) {
            throw new IllegalArgumentException("Plan data index " + value + " is outside 0.." + (size - 1));
        }
        return value;
    }

    private record Body(int x, int z, List<Reach> reaches, List<Pond> ponds, List<Grotto> grottos) {
    }
}

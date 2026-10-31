package art.arcane.iris.pack.datapack.export.compile.noise;

import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.IrisNoiseGenerator;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.FieldEvaluator;
import art.arcane.iris.pack.datapack.export.ir.FieldValidator;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleBinaryOperator;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class SmoothNoiseFidelityTest {
    private static final int SIZE = 512;
    private static final int STRIDE = 23;
    private static final double PEAK_SMOOTHING = 0.2;
    private static final long IRIS_SEED = 90_210L;
    private static final long SALT = 77L;
    private static final String NAMESPACE = "iris_export";
    private static final String PACK_PROPERTY = "iris.exportProbePack";
    private static final DataVersion TARGET = DataVersion.V26_3;
    private static final ExportCapabilities CAPABILITIES = ExportCapabilities.forTarget(TARGET);
    private static final int[][] WINDOWS = {
            {0, 0}, {131_072, 0}, {0, -131_072}, {-262_144, 65_536},
            {65_536, 65_536}, {-65_536, -131_072}, {196_608, -65_536}, {-131_072, 196_608}
    };
    private static final CellularStyleLowering CELLULAR_STUB = (style, salt, registry, capabilities) -> new Field.Const(0.5);
    private static final NoiseStyleCompiler COMPILER = new NoiseStyleCompiler(CELLULAR_STUB);

    @Test
    public void simplexHistogramWithinW1() {
        IrisGeneratorStyle style = new IrisGeneratorStyle(NoiseStyle.SIMPLEX);
        NoiseRegistry registry = new NoiseRegistry(NAMESPACE);
        Field field = COMPILER.compile(style, SALT, registry, CAPABILITIES);
        CNG iris = style.createNoCache(new RNG(IRIS_SEED), null);
        double[] irisValues = grid((x, z) -> iris.noise(x, z), 0, 0, SIZE, STRIDE);
        double[] lowered = new double[0];
        for (int window = 0; window < 4; window++) {
            DoubleBinaryOperator sampler = lowered(field, registry, worldSeed(window));
            lowered = concat(lowered, grid(sampler, WINDOWS[window][0], WINDOWS[window][1], SIZE / 2, STRIDE));
        }
        double distance = wasserstein(irisValues, lowered) / deviation(irisValues);
        assertTrue("SIMPLEX marginal W1 is " + distance + " standard deviations", distance <= 0.03);
    }

    @Test
    public void fbmSpectrumPeakWithin10Percent() {
        IrisGeneratorStyle style = new IrisGeneratorStyle(NoiseStyle.QUADOCTAVE_FRACTAL_FBM_SIMPLEX).zoomed(0.5);
        NoiseRegistry registry = new NoiseRegistry(NAMESPACE);
        Field field = COMPILER.compile(style, SALT, registry, CAPABILITIES);
        CNG iris = style.createNoCache(new RNG(IRIS_SEED), null);
        double[] irisSpectrum = new double[SIZE / 2];
        double[] loweredSpectrum = new double[SIZE / 2];
        for (int window = 0; window < WINDOWS.length; window++) {
            int originX = WINDOWS[window][0];
            int originZ = WINDOWS[window][1];
            accumulate(irisSpectrum, spectrum(grid((x, z) -> iris.noise(x, z), originX, originZ, SIZE, 1)));
            accumulate(loweredSpectrum, spectrum(grid(lowered(field, registry, worldSeed(window)), originX, originZ, SIZE, 1)));
        }
        double irisPeak = peak(irisSpectrum);
        double loweredPeak = peak(loweredSpectrum);
        double ratio = loweredPeak / irisPeak;
        assertTrue("FBM dominant frequency " + loweredPeak + " against Iris " + irisPeak, Math.abs(ratio - 1.0) <= 0.10);
    }

    @Test
    public void ridgedUsesAbsFold() {
        IrisGeneratorStyle style = new IrisGeneratorStyle(NoiseStyle.TRIOCTAVE_FRACTAL_RM_SIMPLEX);
        NoiseRegistry registry = new NoiseRegistry(NAMESPACE);
        Field field = COMPILER.compile(style, SALT, registry, CAPABILITIES);
        List<Field> nodes = new ArrayList<>();
        collect(field, nodes);
        long noises = nodes.stream().filter(Field.Noise.class::isInstance).count();
        long folds = nodes.stream().filter(node -> node instanceof Field.Unary unary && unary.op() == Field.UnaryOp.ABS).count();
        assertEquals(3L, noises);
        assertEquals(3L, folds);
        assertEquals(3, registry.definitions().size());
        for (Field node : nodes) {
            if (node instanceof Field.Unary unary && unary.op() == Field.UnaryOp.ABS) {
                assertTrue(unary.input() instanceof Field.Curve curve && curve.input() instanceof Field.Noise);
            }
        }
    }

    @Test
    public void warpedStyleFragmentCountWithin15Percent() {
        IrisGeneratorStyle style = new IrisGeneratorStyle(NoiseStyle.NOWHERE).zoomed(0.5);
        NoiseRegistry registry = new NoiseRegistry(NAMESPACE);
        Field field = COMPILER.compile(style, SALT, registry, CAPABILITIES);
        CNG iris = style.createNoCache(new RNG(IRIS_SEED), null);
        long irisFragments = 0L;
        long loweredFragments = 0L;
        for (int window = 0; window < WINDOWS.length; window++) {
            int originX = WINDOWS[window][0];
            int originZ = WINDOWS[window][1];
            irisFragments += fragments(grid((x, z) -> iris.noise(x, z), originX, originZ, SIZE, 1));
            loweredFragments += fragments(grid(lowered(field, registry, worldSeed(window)), originX, originZ, SIZE, 1));
        }
        double ratio = (double) loweredFragments / irisFragments;
        assertTrue("NOWHERE fragments " + loweredFragments + " against Iris " + irisFragments, Math.abs(ratio - 1.0) <= 0.15);
    }

    @Test
    public void fittingIsDeterministic() {
        IrisGeneratorStyle style = new IrisGeneratorStyle(NoiseStyle.NOWHERE).zoomed(0.75);
        NoiseRegistry first = new NoiseRegistry(NAMESPACE);
        NoiseRegistry second = new NoiseRegistry(NAMESPACE);
        Field firstField = new NoiseStyleCompiler(CELLULAR_STUB).compile(style, SALT, first, CAPABILITIES);
        Field secondField = new NoiseStyleCompiler(CELLULAR_STUB).compile(style, SALT, second, CAPABILITIES);
        assertEquals(firstField, secondField);
        assertEquals(firstField.toString(), secondField.toString());
        assertEquals(first.definitions(), second.definitions());
    }

    @Test
    public void everyOverworldStyleCompiles() throws IOException {
        String pack = System.getProperty(PACK_PROPERTY);
        Assume.assumeTrue("Set -P" + PACK_PROPERTY + " to the overworld pack folder", pack != null && !pack.isBlank());
        Path root = Path.of(pack);
        Assume.assumeTrue("Pack folder " + root + " is missing", Files.isDirectory(root));
        Set<String> styles = new LinkedHashSet<>();
        List<JsonObject> generators = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
                String relative = root.relativize(file).toString();
                if (relative.startsWith("jigsaw") || relative.startsWith("objects") || relative.equals("Schema.json")) {
                    continue;
                }
                JsonElement json = parse(file);
                collectStyles(json, styles);
                if (relative.startsWith("generators") && json.isJsonObject()) {
                    generators.add(json.getAsJsonObject());
                }
            }
        }
        Gson gson = new Gson();
        Set<NoiseStyle> cellular = EnumSet.noneOf(NoiseStyle.class);
        CellularStyleLowering recording = (style, salt, registry, capabilities) -> {
            cellular.add(style.getStyle());
            return new Field.Const(0.5);
        };
        NoiseStyleCompiler compiler = new NoiseStyleCompiler(recording);
        NoiseRegistry registry = new NoiseRegistry(NAMESPACE);
        int compiled = 0;
        for (String json : styles) {
            IrisGeneratorStyle style = gson.fromJson(json, IrisGeneratorStyle.class);
            FieldValidator.requireTwoDimensional(compiler.compile(style, SALT + compiled, registry, CAPABILITIES), json);
            compiled++;
        }
        int layers = 0;
        for (JsonObject generator : generators) {
            JsonArray composite = generator.has("composite") ? generator.getAsJsonArray("composite") : new JsonArray();
            for (JsonElement layer : composite) {
                IrisNoiseGenerator noise = gson.fromJson(layer, IrisNoiseGenerator.class);
                FieldValidator.requireTwoDimensional(compiler.compileLayer(noise, SALT + layers, registry, CAPABILITIES), layer.toString());
                layers++;
            }
        }
        System.out.println("Compiled " + compiled + " overworld styles and " + layers + " generator layers into "
                + registry.definitions().size() + " noise definitions; cellular styles through the seam: " + cellular);
        assertTrue(compiled > 0);
        assertTrue(layers > 0);
    }

    private static void collectStyles(JsonElement element, Set<String> styles) {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectStyles(child, styles);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        if (isStyle(object)) {
            styles.add(object.toString());
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            collectStyles(entry.getValue(), styles);
        }
    }

    private static boolean isStyle(JsonObject object) {
        if (object.has("expression") || object.has("imageMap") || !object.has("style") || !object.get("style").isJsonPrimitive()) {
            return false;
        }
        String name = object.get("style").getAsString();
        return Arrays.stream(NoiseStyle.values()).anyMatch(style -> style.name().equals(name));
    }

    private static JsonElement parse(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        }
    }

    private static void collect(Field field, List<Field> nodes) {
        nodes.add(field);
        switch (field) {
            case Field.Noise noise -> {
                for (Field shift : new Field[]{noise.shiftX(), noise.shiftY(), noise.shiftZ()}) {
                    if (shift != null) {
                        collect(shift, nodes);
                    }
                }
            }
            case Field.Binary binary -> {
                collect(binary.left(), nodes);
                collect(binary.right(), nodes);
            }
            case Field.Unary unary -> collect(unary.input(), nodes);
            case Field.Curve curve -> collect(curve.input(), nodes);
            case Field.Shared shared -> collect(shared.expr(), nodes);
            default -> {
            }
        }
    }

    private static long worldSeed(int window) {
        return 4_242L + 7_919L * window;
    }

    private static DoubleBinaryOperator lowered(Field field, NoiseRegistry registry, long worldSeed) {
        Map<String, NoiseDef> noises = registry.byReference();
        return (x, z) -> FieldEvaluator.sample(field, noises, worldSeed, TARGET, (int) x, 0, (int) z);
    }

    private static double[] grid(DoubleBinaryOperator field, int originX, int originZ, int size, int stride) {
        double[] values = new double[size * size];
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                values[z * size + x] = field.applyAsDouble(originX + x * stride, originZ + z * stride);
            }
        }
        return values;
    }

    private static double[] concat(double[] first, double[] second) {
        double[] joined = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, joined, first.length, second.length);
        return joined;
    }

    private static double wasserstein(double[] first, double[] second) {
        double[] a = first.clone();
        double[] b = second.clone();
        Arrays.sort(a);
        Arrays.sort(b);
        double sum = 0.0;
        for (int index = 0; index < a.length; index++) {
            int matched = (int) Math.min(b.length - 1, (long) index * b.length / a.length);
            sum += Math.abs(a[index] - b[matched]);
        }
        return sum / a.length;
    }

    private static double deviation(double[] values) {
        double mean = Arrays.stream(values).average().orElse(0.0);
        double sum = 0.0;
        for (double value : values) {
            sum += (value - mean) * (value - mean);
        }
        return Math.sqrt(sum / values.length);
    }

    private static long fragments(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        double low = sorted[(int) (sorted.length * 0.15)];
        double high = sorted[(int) (sorted.length * 0.85)];
        return components(values, high, true) + components(values, low, false);
    }

    private static int components(double[] values, double threshold, boolean above) {
        boolean[] visited = new boolean[values.length];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int count = 0;
        for (int start = 0; start < values.length; start++) {
            if (visited[start] || !inside(values[start], threshold, above)) {
                continue;
            }
            count++;
            visited[start] = true;
            queue.add(start);
            while (!queue.isEmpty()) {
                int cell = queue.poll();
                int x = cell % SIZE;
                int z = cell / SIZE;
                int[] neighbours = {x > 0 ? cell - 1 : -1, x < SIZE - 1 ? cell + 1 : -1, z > 0 ? cell - SIZE : -1, z < SIZE - 1 ? cell + SIZE : -1};
                for (int neighbour : neighbours) {
                    if (neighbour >= 0 && !visited[neighbour] && inside(values[neighbour], threshold, above)) {
                        visited[neighbour] = true;
                        queue.add(neighbour);
                    }
                }
            }
        }
        return count;
    }

    private static boolean inside(double value, double threshold, boolean above) {
        return above ? value > threshold : value < threshold;
    }

    private static void accumulate(double[] total, double[] addition) {
        for (int index = 0; index < total.length; index++) {
            total[index] += addition[index];
        }
    }

    private static double[] spectrum(double[] values) {
        double mean = Arrays.stream(values).average().orElse(0.0);
        double[] real = new double[SIZE * SIZE];
        double[] imaginary = new double[SIZE * SIZE];
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                real[z * SIZE + x] = (values[z * SIZE + x] - mean) * hann(x) * hann(z);
            }
        }
        for (int row = 0; row < SIZE; row++) {
            fft(real, imaginary, row * SIZE, 1);
        }
        for (int column = 0; column < SIZE; column++) {
            fft(real, imaginary, column, SIZE);
        }
        double total = 0.0;
        double[] perOctave = new double[SIZE / 2];
        for (int kz = 0; kz < SIZE; kz++) {
            for (int kx = 0; kx < SIZE; kx++) {
                int fx = kx <= SIZE / 2 ? kx : kx - SIZE;
                int fz = kz <= SIZE / 2 ? kz : kz - SIZE;
                int radius = (int) Math.round(Math.sqrt(fx * fx + fz * fz));
                if (radius == 0 || radius >= perOctave.length) {
                    continue;
                }
                int index = kz * SIZE + kx;
                double power = (real[index] * real[index] + imaginary[index] * imaginary[index]) * radius;
                perOctave[radius] += power;
                total += power;
            }
        }
        for (int radius = 0; radius < perOctave.length; radius++) {
            perOctave[radius] /= total;
        }
        return perOctave;
    }

    private static double peak(double[] spectrum) {
        double[] smoothed = new double[spectrum.length];
        for (int radius = 2; radius < spectrum.length; radius++) {
            double sum = 0.0;
            int count = 0;
            for (int other = 2; other < spectrum.length; other++) {
                if (Math.abs(Math.log((double) other / radius)) <= PEAK_SMOOTHING) {
                    sum += spectrum[other];
                    count++;
                }
            }
            smoothed[radius] = sum / count;
        }
        int best = 3;
        for (int radius = 3; radius < spectrum.length - 1; radius++) {
            if (smoothed[radius] > smoothed[best]) {
                best = radius;
            }
        }
        double left = Math.log(smoothed[best - 1]);
        double centre = Math.log(smoothed[best]);
        double right = Math.log(smoothed[best + 1]);
        double curvature = left - 2.0 * centre + right;
        double offset = curvature == 0.0 ? 0.0 : 0.5 * (left - right) / curvature;
        return (best + offset) / SIZE;
    }

    private static double hann(int index) {
        return 0.5 - 0.5 * Math.cos(2.0 * Math.PI * index / (SIZE - 1));
    }

    private static void fft(double[] real, double[] imaginary, int offset, int stride) {
        for (int index = 1, swap = 0; index < SIZE; index++) {
            int bit = SIZE >> 1;
            for (; (swap & bit) != 0; bit >>= 1) {
                swap ^= bit;
            }
            swap ^= bit;
            if (index < swap) {
                exchange(real, offset + index * stride, offset + swap * stride);
                exchange(imaginary, offset + index * stride, offset + swap * stride);
            }
        }
        for (int length = 2; length <= SIZE; length <<= 1) {
            double angle = -2.0 * Math.PI / length;
            for (int start = 0; start < SIZE; start += length) {
                for (int step = 0; step < length / 2; step++) {
                    double cos = Math.cos(angle * step);
                    double sin = Math.sin(angle * step);
                    int even = offset + (start + step) * stride;
                    int odd = offset + (start + step + length / 2) * stride;
                    double oddReal = real[odd] * cos - imaginary[odd] * sin;
                    double oddImaginary = real[odd] * sin + imaginary[odd] * cos;
                    real[odd] = real[even] - oddReal;
                    imaginary[odd] = imaginary[even] - oddImaginary;
                    real[even] += oddReal;
                    imaginary[even] += oddImaginary;
                }
            }
        }
    }

    private static void exchange(double[] values, int first, int second) {
        double value = values[first];
        values[first] = values[second];
        values[second] = value;
    }
}

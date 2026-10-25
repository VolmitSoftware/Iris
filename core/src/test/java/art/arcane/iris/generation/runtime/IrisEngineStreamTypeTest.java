package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.noise.IrisExpression;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisEngineStreamTypeTest {
    private static final Map<WaterRole, Integer> ROLE_CODES = roleCodes();
    private static final Map<WaterRole, String> ROLE_LABELS = roleLabels();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void waterStreamsReadTheEffectiveWaterHeadAndTheStableRoleCode() {
        Engine engine = mock(Engine.class);
        IrisComplex complex = mock(IrisComplex.class);
        ProceduralStream<Double> heads = ProceduralStream.ofDouble((x, z) -> x == 4D && z == 9D ? 71D : 63D);
        ProceduralStream<WaterRole> roles = ProceduralStream.of((x, z) -> x == 4D && z == 9D ? WaterRole.POND : WaterRole.NONE,
                Interpolated.of(role -> 0D, value -> WaterRole.NONE));
        when(engine.getComplex()).thenReturn(complex);
        when(complex.getWaterHeadStream()).thenReturn(heads);
        when(complex.getWaterRoleStream()).thenReturn(roles);

        assertSame(heads, IrisEngineStreamType.WATER_HEAD.get(engine));
        assertEquals(71D, IrisEngineStreamType.WATER_HEAD.get(engine).getDouble(4D, 9D), 0D);
        assertEquals(4D, IrisEngineStreamType.WATER_ROLE.get(engine).getDouble(4D, 9D), 0D);
        assertEquals(0D, IrisEngineStreamType.WATER_ROLE.get(engine).getDouble(5D, 9D), 0D);
    }

    @Test
    public void everyWaterRoleKeepsItsPinnedCodeAndTheSchemaListsThem() throws Exception {
        assertEquals(WaterRole.values().length, ROLE_CODES.size());
        Set<Integer> codes = new HashSet<>();
        StringJoiner listed = new StringJoiner(", ");
        for (WaterRole role : WaterRole.values()) {
            assertEquals(role.name(), ROLE_CODES.get(role).intValue(), role.code());
            assertTrue(role.name(), codes.add(role.code()));
        }
        for (int code = 0; code < ROLE_CODES.size(); code++) {
            for (Map.Entry<WaterRole, Integer> entry : ROLE_CODES.entrySet()) {
                if (entry.getValue() == code) {
                    listed.add(code + " " + ROLE_LABELS.get(entry.getKey()));
                }
            }
        }
        String description = IrisEngineStreamType.class.getField(IrisEngineStreamType.WATER_ROLE.name())
                .getAnnotation(Description.class).value();

        assertTrue(description, description.contains(listed + "."));
    }

    @Test
    public void unknownStreamNamesFailThePackLoadInsteadOfFallingBackToPlainNoise() throws Exception {
        File pack = temporaryFolder.newFolder("streams");
        write(pack, "expressions/inline.json", expression("{\"name\":\"stream\",\"engineStreamValue\":\"NOT_A_STREAM\"}"));
        write(pack, "snippet/expression-load/missing.json", "{\"name\":\"stream\",\"engineStreamValue\":\"NOT_A_STREAM\"}");
        write(pack, "expressions/snippet.json", expression("\"snippet/expression-load/missing\""));
        write(pack, "expressions/lowercase.json", expression("{\"name\":\"stream\",\"engineStreamValue\":\"water_head\"}"));
        write(pack, "expressions/known.json", expression("{\"name\":\"stream\",\"engineStreamValue\":\"WATER_HEAD\"}"));
        write(pack, "expressions/unknown-value.json", expression("{\"name\":\"stream\",\"engineValue\":\"NOT_A_VALUE\"}"));
        IrisData data = IrisData.openDatapackCompiler(pack);
        try {
            assertUnknownStream("NOT_A_STREAM", () -> data.getExpressionLoader().load("inline"));
            assertUnknownStream("NOT_A_STREAM", () -> data.getExpressionLoader().load("snippet"));
            assertUnknownStream("water_head", () -> data.getExpressionLoader().load("lowercase"));
            assertUnknownStream("NOT_A_STREAM", () -> new IrisGeneratorStyle().setExpression("inline").create(new RNG(7L), data));

            IrisExpression known = data.getExpressionLoader().load("known");
            assertSame(IrisEngineStreamType.WATER_HEAD, known.getVariables().get(0).getEngineStreamValue());
            IrisExpression unknownValue = data.getExpressionLoader().load("unknown-value");
            assertNotNull(unknownValue);
            assertNull(unknownValue.getVariables().get(0).getEngineValue());
        } finally {
            data.close();
        }
    }

    private static void assertUnknownStream(String name, ThrowingRunnable load) {
        IrisEngineStreamType.UnknownStreamException failure = assertThrows(IrisEngineStreamType.UnknownStreamException.class, load);
        assertTrue(failure.getMessage(), failure.getMessage().contains("Unknown engine stream '" + name + "'"));
        assertTrue(failure.getMessage(), failure.getMessage().contains("WATER_HEAD"));
    }

    private static String expression(String variable) {
        return "{\"expression\":\"stream\",\"variables\":[" + variable + "]}";
    }

    private static void write(File pack, String path, String json) throws Exception {
        Path file = pack.toPath().resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, json, StandardCharsets.UTF_8);
    }

    private static Map<WaterRole, Integer> roleCodes() {
        Map<WaterRole, Integer> codes = new EnumMap<>(WaterRole.class);
        codes.put(WaterRole.NONE, 0);
        codes.put(WaterRole.CHANNEL, 1);
        codes.put(WaterRole.BANK, 2);
        codes.put(WaterRole.SHORE, 3);
        codes.put(WaterRole.POND, 4);
        codes.put(WaterRole.POND_SHORE, 5);
        codes.put(WaterRole.DAM, 6);
        codes.put(WaterRole.MOUTH, 7);
        codes.put(WaterRole.CASCADE, 8);
        codes.put(WaterRole.WATERFALL, 9);
        codes.put(WaterRole.INLET, 10);
        return codes;
    }

    private static Map<WaterRole, String> roleLabels() {
        Map<WaterRole, String> labels = new EnumMap<>(WaterRole.class);
        labels.put(WaterRole.NONE, "dry");
        labels.put(WaterRole.CHANNEL, "channel");
        labels.put(WaterRole.BANK, "bank");
        labels.put(WaterRole.SHORE, "shore");
        labels.put(WaterRole.POND, "pond");
        labels.put(WaterRole.POND_SHORE, "pond shore");
        labels.put(WaterRole.DAM, "dam");
        labels.put(WaterRole.MOUTH, "river mouth");
        labels.put(WaterRole.CASCADE, "cascade");
        labels.put(WaterRole.WATERFALL, "waterfall");
        labels.put(WaterRole.INLET, "sea inlet");
        return labels;
    }
}

package art.arcane.iris.world.history;

import art.arcane.iris.platform.bukkit.nms.datapack.IDataFixer;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisDimensionMode;
import art.arcane.iris.generation.terrain.IrisDimensionModeType;
import art.arcane.iris.generation.terrain.IrisDimensionType;
import art.arcane.iris.generation.terrain.IrisDimensionTypeOptions;
import art.arcane.iris.world.IrisEnvironment;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.volmlib.util.json.JSONObject;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GenerationEpochContractFactoryTest {
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void derivesTheAuthoredDimensionTypeSemantics() {
        IrisDimension dimension = dimension(IrisEnvironment.NORMAL);
        IrisDimensionType dimensionType = dimension.getDimensionType();

        GenerationEpoch.DimensionContract contract = GenerationEpochContractFactory.create(
                dimension,
                "worlds/overworld",
                "iris:overworld"
        );

        assertEquals("worlds/overworld", contract.dimensionKey());
        assertEquals("iris:overworld", contract.dimensionTypeKey());
        assertEquals("NORMAL", contract.environment());
        assertEquals("OVERWORLD", contract.generationMode());
        assertEquals(127, contract.internalFluidHeight());
        assertEquals(-64, contract.minHeight());
        assertEquals(384, contract.height());
        assertEquals(256, contract.logicalHeight());
        assertEquals(1D, contract.coordinateScale(), 0D);
        assertEquals(false, contract.upperTerrainEnabled());
        assertEquals("none", contract.upperDimensionKey());
        assertEquals(0, contract.upperDimensionGap());
        assertEquals("0".repeat(64), contract.upperTerrainPackFingerprint());
        assertEquals(
                GenerationEpochContractFactory.CURRENT_DIMENSION_TYPE_FINGERPRINT_SCHEMA,
                contract.dimensionTypeFingerprintSchema()
        );
        assertEquals(
                GenerationEpochContractFactory.fingerprintDimensionType(dimensionType),
                contract.dimensionTypeFingerprint()
        );
    }

    @Test
    public void capturesGenerationModeAndInternalFluidHeight() {
        IrisDimension dimension = dimension(IrisEnvironment.NORMAL)
                .setMode(new IrisDimensionMode().setType(IrisDimensionModeType.ISLANDS))
                .setFluidHeight(100);

        GenerationEpoch.DimensionContract contract = GenerationEpochContractFactory.create(
                dimension,
                "overworld",
                "iris:overworld"
        );

        assertEquals("ISLANDS", contract.generationMode());
        assertEquals(164, contract.internalFluidHeight());
    }

    @Test
    public void recordedEpochKeepsItsUpperTerrainFingerprintVersion() throws Exception {
        File pack = temporary.newFolder("recorded-upper-pack");
        Files.createDirectories(pack.toPath().resolve("objects"));
        Files.writeString(pack.toPath().resolve("objects/.DS_Store"), "recorded metadata");
        IrisData data = mock(IrisData.class);
        IrisDimension dimension = dimension(IrisEnvironment.NORMAL).setUpperDimension("overworld");
        dimension.setLoader(data);
        when(data.getDataFolder()).thenReturn(pack);
        GenerationEpoch.DimensionContract current = GenerationEpochContractFactory.create(
                dimension, "overworld", "iris:overworld");
        GenerationEpoch epoch = mock(GenerationEpoch.class);
        when(epoch.dimensionContract()).thenReturn(current);
        when(epoch.packFingerprintVersion()).thenReturn(1);

        GenerationEpoch.DimensionContract recorded = GenerationEpochContractFactory.createForEpoch(
                dimension, "iris:overworld", epoch);

        assertEquals(GenerationPackFingerprint.compute(pack.toPath(), 1), recorded.upperTerrainPackFingerprint());
        assertNotEquals(current.upperTerrainPackFingerprint(), recorded.upperTerrainPackFingerprint());
        when(epoch.dimensionContract()).thenReturn(recorded);
        assertEquals(recorded, GenerationEpochContractFactory.createForEpoch(dimension, "iris:overworld", epoch));
        Files.writeString(pack.toPath().resolve("objects/.DS_Store"), "changed metadata");
        assertNotEquals(recorded, GenerationEpochContractFactory.createForEpoch(dimension, "iris:overworld", epoch));
        assertEquals(current, GenerationEpochContractFactory.create(dimension, "overworld", "iris:overworld"));
    }

    @Test
    public void upperTerrainMustBeLocalAndPackIdentityDoesNotChangeItsLayout() throws Exception {
        File pack = temporary.newFolder("upper-pack");
        IrisData data = mock(IrisData.class);
        @SuppressWarnings("unchecked")
        ResourceLoader<IrisDimension> loader = mock(ResourceLoader.class);
        IrisDimension dimension = dimension(IrisEnvironment.NORMAL).setUpperDimension("ceiling");
        IrisDimension ceiling = dimension(IrisEnvironment.NORMAL);
        dimension.setLoader(data);
        ceiling.setLoader(data);
        ceiling.setLoadKey("ceiling");
        when(data.getDataFolder()).thenReturn(pack);
        when(data.getDimensionLoader()).thenReturn(loader);
        when(loader.load("ceiling", false)).thenReturn(ceiling);

        GenerationEpoch.DimensionContract first = GenerationEpochContractFactory.create(
                dimension,
                "overworld",
                "iris:overworld"
        );
        Files.writeString(
                pack.toPath().resolve("upper-content.json"),
                "{}",
                StandardCharsets.UTF_8
        );
        GenerationEpoch.DimensionContract changedPack = GenerationEpochContractFactory.create(
                dimension,
                "overworld",
                "iris:overworld"
        );

        assertEquals(true, first.upperTerrainEnabled());
        assertEquals("ceiling", first.upperDimensionKey());
        assertEquals(32, first.upperDimensionGap());
        assertNotEquals(first.upperTerrainPackFingerprint(), changedPack.upperTerrainPackFingerprint());
        assertNotEquals(first, changedPack);
        assertEquals(true, first.hasSameLayout(changedPack));
        dimension.setUpperDimensionGap(33);
        assertEquals(true, first.hasSameLayout(GenerationEpochContractFactory.create(
                dimension, "overworld", "iris:overworld")));

        when(loader.load("ceiling", false)).thenReturn(null);
        assertThrows(
                IllegalStateException.class,
                () -> GenerationEpochContractFactory.create(
                        dimension,
                        "overworld",
                        "iris:overworld"
        )
        );
    }

    @Test
    public void resolvesBaseEnvironmentCoordinateScale() {
        IrisDimension dimension = dimension(IrisEnvironment.NETHER);

        GenerationEpoch.DimensionContract contract = GenerationEpochContractFactory.create(
                dimension,
                "underworld",
                "iris:underworld"
        );

        assertEquals("NETHER", contract.environment());
        assertEquals(8D, contract.coordinateScale(), 0D);
    }

    @Test
    public void explicitCoordinateScaleOverridesTheBaseEnvironment() {
        IrisDimension dimension = dimension(IrisEnvironment.NETHER);
        dimension.setDimensionOptions(new IrisDimensionTypeOptions().coordinateScale(3.5D));

        GenerationEpoch.DimensionContract contract = GenerationEpochContractFactory.create(
                dimension,
                "underworld",
                "iris:underworld"
        );

        assertEquals(3.5D, contract.coordinateScale(), 0D);
    }

    @Test
    public void serializerChangesDoNotAlterTheDimensionContract() {
        IrisDimension dimension = dimension(IrisEnvironment.NORMAL);
        IrisDimensionType type = dimension.getDimensionType();
        String firstJson = type.toJson(new TaggedFixer("first"));
        String secondJson = type.toJson(new TaggedFixer("second"));

        GenerationEpoch.DimensionContract first = GenerationEpochContractFactory.create(
                dimension,
                "overworld",
                "iris:overworld"
        );
        GenerationEpoch.DimensionContract second = GenerationEpochContractFactory.create(
                dimension,
                "overworld",
                "iris:overworld"
        );

        assertNotEquals(firstJson, secondJson);
        assertEquals(first, second);
    }

    @Test
    public void authoredDimensionTypeChangesAlterTheFingerprint() {
        IrisDimension normal = dimension(IrisEnvironment.NORMAL);
        IrisDimension fullbright = dimension(IrisEnvironment.NORMAL);
        fullbright.setFullbright(true);
        IrisDimension nether = dimension(IrisEnvironment.NETHER);

        assertNotEquals(
                GenerationEpochContractFactory.create(
                        normal,
                        "overworld",
                        "iris:overworld"
                ).dimensionTypeFingerprint(),
                GenerationEpochContractFactory.create(
                        fullbright,
                        "overworld",
                        "iris:overworld"
                ).dimensionTypeFingerprint()
        );
        assertNotEquals(
                GenerationEpochContractFactory.fingerprintDimensionType(normal.getDimensionType()),
                GenerationEpochContractFactory.fingerprintDimensionType(nether.getDimensionType())
        );
    }

    @Test
    public void recordsAndValidatesTheFingerprintSchema() {
        IrisDimension dimension = dimension(IrisEnvironment.NORMAL);

        GenerationEpoch.DimensionContract contract = GenerationEpochContractFactory.create(
                dimension,
                "overworld",
                "iris:overworld",
                GenerationEpochContractFactory.DIMENSION_TYPE_FINGERPRINT_SCHEMA_VERSION_ONE
        );

        assertEquals(
                GenerationEpochContractFactory.DIMENSION_TYPE_FINGERPRINT_SCHEMA_VERSION_ONE,
                contract.dimensionTypeFingerprintSchema()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> GenerationEpochContractFactory.create(
                        dimension,
                        "overworld",
                        "iris:overworld",
                        2
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> GenerationEpochContractFactory.fingerprintDimensionType(
                        dimension.getDimensionType(),
                        2
                )
        );
    }

    @Test
    public void refusesInvalidKeys() {
        IrisDimension dimension = dimension(IrisEnvironment.NORMAL);

        assertThrows(
                IllegalArgumentException.class,
                () -> GenerationEpochContractFactory.create(
                        dimension,
                        "Overworld",
                        "iris:overworld"
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> GenerationEpochContractFactory.create(
                        dimension,
                        "overworld",
                        "overworld"
                )
        );
    }

    private static IrisDimension dimension(IrisEnvironment environment) {
        IrisDimension dimension = new IrisDimension();
        dimension.setLoadKey("overworld");
        dimension.setEnvironment(environment);
        dimension.setDimensionHeight(new IrisRange(-64, 320));
        dimension.setLogicalHeight(256);
        return dimension;
    }

    private record TaggedFixer(String tag) implements IDataFixer {
        @Override
        public JSONObject resolve(Dimension dimension, IrisDimensionTypeOptions options) {
            double coordinateScale = options.coordinateScale() == -1D
                    ? dimension == Dimension.NETHER ? 8D : 1D
                    : options.coordinateScale();
            return new JSONObject()
                    .put("coordinate_scale", coordinateScale)
                    .put("serializer", tag);
        }

        @Override
        public void fixDimension(Dimension dimension, JSONObject json) {
        }
    }
}

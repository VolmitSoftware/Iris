package com.volmit.host;

import com.volmit.FeatureAttachment.Target;
import com.volmit.config.Reference;
import com.volmit.example.chunk.GrassModule;
import com.volmit.example.chunk.TerrainModule;
import com.volmit.example.placement.PlacementModules;
import com.volmit.host.schema.FeatureRole;
import com.volmit.host.schema.FeatureSchema;
import com.volmit.host.schema.ObjectType;
import com.volmit.host.schema.PackSchema;
import com.volmit.host.schema.SchemaOption;
import com.volmit.host.schema.TextFormat;
import com.volmit.host.schema.ValueKind.BooleanKind;
import com.volmit.host.schema.ValueKind.DecimalKind;
import com.volmit.host.schema.ValueKind.EnumConstant;
import com.volmit.host.schema.ValueKind.EnumKind;
import com.volmit.host.schema.ValueKind.IntegerKind;
import com.volmit.host.schema.ValueKind.ListKind;
import com.volmit.host.schema.ValueKind.MapKind;
import com.volmit.host.schema.ValueKind.ObjectKind;
import com.volmit.host.schema.ValueKind.ReferenceKind;
import com.volmit.host.schema.ValueKind.TextKind;
import com.volmit.support.TestModuleJar;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Function;
import static com.volmit.support.TestModuleJar.displayName;
import static com.volmit.support.TestModuleJar.module;
import static org.junit.jupiter.api.Assertions.*;

/** Loads real module jars and checks what the pack tells studio about their settings. */
class PackSchemaTest {
    private static final ModuleHost NO_BUILT_INS = ModuleHost.of(List.of());
    private static final String FLORA = "example:flora";
    private static final String FLORA_SOURCE = "settings example:flora (module example:flora from modules/flora.jar)";
    private static final String NO_STAGES = "";

    @TempDir
    Path packs;
    private int brokenPacks;

    private static final String FLORA_MODULE_MEMBERS = """
            public void register(com.volmit.FeatureRegistry registry) {
                registry.registerSettings(WindSettings.class);
                registry.registerSettings(FloraSettings.class);
                registry.registerObject(BushSettings.class, bush -> context -> null);
            }
            """;

    private static final String FLORA_INSTALL =
            "graph.define(Stage.chunk(\"example:flora/plant\")).when(FloraSettings.class).run((context, flora) -> {});";

    private static final String FLORA_SETTINGS = """
            package pack.flora;

            import com.volmit.FeatureAttachment.Target;
            import com.volmit.IrisFeature;
            import com.volmit.block.BlockState;
            import com.volmit.config.*;
            import com.volmit.resource.ResourceId;
            import java.util.*;

            @Desc("Plants flowers and bushes")
            @Feature(id = "example:flora", name = "Flora", field = "flora", targets = {Target.REGION, Target.BIOME})
            public final class FloraSettings implements IrisFeature {
                @Desc("Plants per chunk") @Required @MinNumber(0) @MaxNumber(64)
                private int density = 4;
                private double chance = 0.25;
                private float spread = 0.1f;
                private Long seedOffset;
                private boolean enabled = true;
                private String label = "flora";
                private ResourceId palette = ResourceId.parse("example:flora/default");
                @Ref(Reference.BLOCK)
                private String ground = "minecraft:grass_block";
                @Ref(Reference.PACK_BIOME)
                private List<String> biomes = List.of("plains", "forest");
                @Ref(Reference.ITEM)
                private ResourceId[] drops = {};
                private BlockState flower = new BlockState("minecraft:poppy");
                private Season season = Season.SPRING;
                private Set<Season> seasons = new HashSet<>(Set.of(Season.SUMMER, Season.AUTUMN));
                private Map<String, Integer> weights = Map.of("rose", 2, "daisy", 1);
                private Patch patch = new Patch();
                private List<Patch> extra = List.of();
                private transient int cache;
                private static int counter;
            }
            """;

    private static final String SEASON = """
            package pack.flora;

            import com.volmit.config.Desc;

            public enum Season {
                @Desc("Early in the year") SPRING,
                SUMMER,
                AUTUMN
            }
            """;

    private static final String PATCH = """
            package pack.flora;

            import com.volmit.config.*;

            @Desc("A patch of plants")
            @Snippet("flora-patch")
            final class Patch {
                @MinNumber(1)
                private int radius = 3;
                private Season bloom = Season.SUMMER;
                @Desc("A smaller patch inside this one")
                private Patch child;
            }
            """;

    private static final String WIND_SETTINGS = """
            package pack.flora;

            import com.volmit.FeatureAttachment.Target;
            import com.volmit.IrisFeature;
            import com.volmit.config.*;

            @Feature(id = "example:flora/wind", name = "Wind", field = "wind", targets = Target.DIMENSION)
            public final class WindSettings implements IrisFeature {
                @Desc("How far plants sway")
                private double strength = 1.5;
            }
            """;

    private static final String BUSH_SETTINGS = """
            package pack.flora;

            import com.volmit.FeatureAttachment.Target;
            import com.volmit.IrisFeature;
            import com.volmit.config.*;

            @Snippet("bush")
            @Feature(id = "example:flora/bush", name = "Bush", field = "bush", targets = Target.OBJECT_PLACEMENT)
            public final class BushSettings implements IrisFeature {
                private int height = 2;
            }
            """;

    private static TestModuleJar flora() {
        return module("pack.flora.FloraModule", FLORA, FLORA_MODULE_MEMBERS, FLORA_INSTALL)
                .source("pack.flora.FloraSettings", FLORA_SETTINGS)
                .source("pack.flora.Season", SEASON)
                .source("pack.flora.Patch", PATCH)
                .source("pack.flora.WindSettings", WIND_SETTINGS)
                .source("pack.flora.BushSettings", BUSH_SETTINGS);
    }

    private Path pack(String name) {
        try {
            return Files.createDirectories(packs.resolve(name));
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    private static PackSchema schemaOf(Path pack, ModuleHost host) {
        try (var loaded = LoadedPack.load(pack, host)) {
            return loaded.schema();
        }
    }

    private PackSchema floraSchema() {
        var pack = pack("flora");
        flora().writeInto(pack, "flora.jar");
        return schemaOf(pack, NO_BUILT_INS);
    }

    private static SchemaOption option(ObjectType type, String name) {
        return type.options().stream().filter(option -> option.name().equals(name)).findFirst().orElseThrow();
    }

    private static List<String> names(List<FeatureSchema> features, Function<FeatureSchema, Object> name) {
        return features.stream().map(name).map(Object::toString).toList();
    }

    @Test
    void attachesEveryModuleFieldToItsTargets() {
        var schema = floraSchema();

        assertEquals(List.of("flora"), names(schema.fields(Target.BIOME), FeatureSchema::field));
        assertEquals(List.of("flora"), names(schema.fields(Target.REGION), FeatureSchema::field));
        assertEquals(List.of("wind"), names(schema.fields(Target.DIMENSION), FeatureSchema::field));
        assertEquals(List.of("bush"), names(schema.fields(Target.OBJECT_PLACEMENT), FeatureSchema::field));
        assertEquals(List.of("example:flora", "example:flora/bush", "example:flora/wind"), names(schema.features(), FeatureSchema::id));

        var flora = schema.features().getFirst();
        assertEquals("Flora", flora.name());
        assertEquals(FeatureRole.SETTINGS, flora.role());
        assertEquals(FLORA, flora.module().id().toString());
        assertEquals(displayName(FLORA), flora.module().name());
        assertEquals(List.of(Target.REGION, Target.BIOME), List.copyOf(flora.targets()));
        assertEquals("pack.flora.FloraSettings", flora.settings().name());
        assertEquals("Plants flowers and bushes", flora.settings().description());
        assertEquals(Optional.empty(), flora.settings().snippet());
    }

    @Test
    void describesOptionsInNameOrderWithChecksAndDefaults() {
        var settings = floraSchema().type("pack.flora.FloraSettings");

        assertEquals(List.of("biomes", "chance", "density", "drops", "enabled", "extra", "flower", "ground", "label",
                        "palette", "patch", "season", "seasons", "seedOffset", "spread", "weights"),
                settings.options().stream().map(SchemaOption::name).toList());
        assertEquals(new SchemaOption("density", new IntegerKind(), "Plants per chunk", true,
                OptionalDouble.of(0), OptionalDouble.of(64), Optional.of(4L)), option(settings, "density"));
        assertEquals(new SchemaOption("chance", new DecimalKind(), "", false,
                OptionalDouble.empty(), OptionalDouble.empty(), Optional.of(0.25)), option(settings, "chance"));
        assertEquals(Optional.of(0.1), option(settings, "spread").defaultValue(), "floats keep their own decimal form");
        assertEquals(new IntegerKind(), option(settings, "seedOffset").kind());
        assertEquals(Optional.empty(), option(settings, "seedOffset").defaultValue(), "a null field has no default");
        assertEquals(new BooleanKind(), option(settings, "enabled").kind());
        assertEquals(Optional.of(true), option(settings, "enabled").defaultValue());
        assertEquals(new TextKind(TextFormat.PLAIN), option(settings, "label").kind());
        assertEquals(new TextKind(TextFormat.RESOURCE_ID), option(settings, "palette").kind());
        assertEquals(Optional.of("example:flora/default"), option(settings, "palette").defaultValue());
    }

    @Test
    void describesEnumsCollectionsAndMaps() {
        var settings = floraSchema().type("pack.flora.FloraSettings");

        var season = new EnumKind("pack.flora.Season", List.of(new EnumConstant("SPRING", "Early in the year"),
                new EnumConstant("SUMMER", ""), new EnumConstant("AUTUMN", "")));
        assertEquals(season, option(settings, "season").kind());
        assertEquals(Optional.of("SPRING"), option(settings, "season").defaultValue());
        assertEquals(new ListKind(season, true), option(settings, "seasons").kind());
        assertEquals(Optional.of(List.of("AUTUMN", "SUMMER")), option(settings, "seasons").defaultValue(), "sets are sorted");
        assertEquals(new MapKind(new IntegerKind()), option(settings, "weights").kind());
        assertEquals(Optional.of(Map.of("daisy", 1L, "rose", 2L)), option(settings, "weights").defaultValue());
        assertEquals(List.of("daisy", "rose"), List.copyOf(((Map<?, ?>) option(settings, "weights").defaultValue().orElseThrow()).keySet()));
        assertEquals(new ListKind(new ObjectKind("pack.flora.Patch"), false), option(settings, "extra").kind());
        assertEquals(Optional.of(List.of()), option(settings, "extra").defaultValue());
    }

    @Test
    void describesReferencesOnTextCollectionsArraysAndBlockStates() {
        var settings = floraSchema().type("pack.flora.FloraSettings");

        assertEquals(new ReferenceKind(Reference.BLOCK, TextFormat.PLAIN), option(settings, "ground").kind());
        assertEquals(new ListKind(new ReferenceKind(Reference.PACK_BIOME, TextFormat.PLAIN), false), option(settings, "biomes").kind());
        assertEquals(Optional.of(List.of("plains", "forest")), option(settings, "biomes").defaultValue());
        assertEquals(new ListKind(new ReferenceKind(Reference.ITEM, TextFormat.RESOURCE_ID), false), option(settings, "drops").kind());
        assertEquals(new ReferenceKind(Reference.BLOCK, TextFormat.BLOCK_STATE), option(settings, "flower").kind(),
                "BlockState names a block without @Ref");
        assertEquals(Optional.of("minecraft:poppy"), option(settings, "flower").defaultValue());
    }

    @Test
    void describesNestedTypesOnceAndEndsCyclesAtTheTypesName() {
        var schema = floraSchema();
        var settings = schema.type("pack.flora.FloraSettings");
        var patch = schema.type("pack.flora.Patch");

        assertEquals(List.of("pack.flora.BushSettings", "pack.flora.FloraSettings", "pack.flora.Patch", "pack.flora.WindSettings"),
                schema.types().stream().map(ObjectType::name).toList());
        assertEquals(new ObjectKind("pack.flora.Patch"), option(settings, "patch").kind());
        assertEquals(Optional.of(Map.of("bloom", "SUMMER", "radius", 3L)), option(settings, "patch").defaultValue(),
                "an object default holds the options that have one");
        assertEquals("A patch of plants", patch.description());
        assertEquals(List.of("bloom", "child", "radius"), patch.options().stream().map(SchemaOption::name).toList());
        assertEquals(new ObjectKind("pack.flora.Patch"), option(patch, "child").kind());
        assertEquals("A smaller patch inside this one", option(patch, "child").description());
        assertEquals(OptionalDouble.of(1), option(patch, "radius").minimum());
    }

    @Test
    void listsSnippetsObjectGeneratorsAndExplicitlyRegisteredSettings() {
        var schema = floraSchema();

        assertEquals(List.of("bush", "flora-patch"), schema.snippets().stream().map(type -> type.snippet().orElseThrow()).toList());
        assertEquals(List.of("pack.flora.BushSettings", "pack.flora.Patch"), schema.snippets().stream().map(ObjectType::name).toList());

        var bush = schema.objectGenerators().getFirst();
        assertEquals(1, schema.objectGenerators().size());
        assertEquals(FeatureRole.OBJECT_GENERATOR, bush.role());
        assertEquals(Optional.of(2L), option(bush.settings(), "height").defaultValue());

        var wind = schema.fields(Target.DIMENSION).getFirst();
        assertEquals(FeatureRole.SETTINGS, wind.role());
        assertEquals("example:flora/wind", wind.id().toString());
        assertEquals(new SchemaOption("strength", new DecimalKind(), "How far plants sway", false,
                OptionalDouble.empty(), OptionalDouble.empty(), Optional.of(1.5)), option(wind.settings(), "strength"));
    }

    @Test
    void sameModulesGiveTheSameDescriptionWhateverTheJarOrder() {
        var other = module("pack.other.OtherModule", "example:other", NO_STAGES, NO_STAGES);
        var first = pack("first");
        flora().writeInto(first, "a.jar");
        other.writeInto(first, "b.jar");
        var second = pack("second");
        other.writeInto(second, "a.jar");
        flora().writeInto(second, "b.jar");

        var schema = schemaOf(first, NO_BUILT_INS);

        assertEquals(schema, schemaOf(first, NO_BUILT_INS));
        assertEquals(schema, schemaOf(second, NO_BUILT_INS));
    }

    @Test
    void describesBuiltInModulesWithThePack() {
        var builtIns = new ArrayList<>(PlacementModules.all());
        builtIns.add(new TerrainModule());
        builtIns.add(new GrassModule());
        var schema = schemaOf(pack("empty"), ModuleHost.of(builtIns));

        assertEquals(List.of("example:grass", "example:moss", "iris:snow", "iris:stilt"), names(schema.features(), FeatureSchema::id));
        assertEquals(List.of("moss", "snow", "stilt"), names(schema.fields(Target.OBJECT_PLACEMENT), FeatureSchema::field));

        var stilt = schema.fields(Target.OBJECT_PLACEMENT).get(2);
        assertEquals("Stilts", stilt.module().name());
        assertEquals(new ObjectType("com.volmit.example.placement.StiltSettings", "Fills the space below an object down to the ground",
                Optional.empty(), List.of(new SchemaOption("maxDepth", new IntegerKind(), "Maximum blocks filled below each lowest object block",
                false, OptionalDouble.of(1), OptionalDouble.empty(), Optional.of(16L)))), stilt.settings());

        var grass = schema.fields(Target.BIOME).getFirst();
        assertEquals(new SchemaOption("block", new ReferenceKind(Reference.BLOCK, TextFormat.BLOCK_STATE), "Block placed on top of each column",
                false, OptionalDouble.empty(), OptionalDouble.empty(), Optional.of("minecraft:grass_block")), grass.settings().options().getFirst());
    }

    /** A pack whose module example:flora registers pack.flora.FloraSettings with {@code body} as its class body. */
    private IllegalArgumentException loadFailure(String body, String... otherSources) {
        var jar = module("pack.flora.FloraModule", FLORA,
                "public void register(com.volmit.FeatureRegistry registry) { registry.registerSettings(FloraSettings.class); }", NO_STAGES)
                .source("pack.flora.FloraSettings", """
                        package pack.flora;

                        import com.volmit.FeatureAttachment.Target;
                        import com.volmit.IrisFeature;
                        import com.volmit.block.BlockState;
                        import com.volmit.config.*;
                        import com.volmit.resource.*;
                        import java.util.*;

                        @Feature(id = "example:flora", name = "Flora", field = "flora", targets = Target.BIOME)
                        public final class FloraSettings implements IrisFeature {
                        %s
                        }
                        """.formatted(body));
        for (int index = 0; index < otherSources.length; index += 2) {
            jar.source(otherSources[index], otherSources[index + 1]);
        }
        var pack = pack("broken-" + ++brokenPacks);
        jar.writeInto(pack, "flora.jar");
        return assertThrows(IllegalArgumentException.class, () -> LoadedPack.load(pack, NO_BUILT_INS));
    }

    private void assertLoadFails(String expected, String body, String... otherSources) {
        assertEquals(expected, loadFailure(body, otherSources).getMessage());
    }

    @Test
    void defaultsLeaveOutWhatJsonCannotHold() {
        var pack = pack("defaults");
        module("pack.flora.FloraModule", FLORA,
                "public void register(com.volmit.FeatureRegistry registry) { registry.registerSettings(FloraSettings.class); }", NO_STAGES)
                .source("pack.flora.FloraSettings", """
                        package pack.flora;

                        import com.volmit.FeatureAttachment.Target;
                        import com.volmit.IrisFeature;
                        import com.volmit.config.Feature;
                        import java.util.*;

                        @Feature(id = "example:flora", name = "Flora", field = "flora", targets = Target.BIOME)
                        public final class FloraSettings implements IrisFeature {
                            private Node node = new Node();
                            private double ratio = Double.NaN;
                            private List<String> names = Arrays.asList("a", null);
                            private Set<Node> nodes = Set.of(new Node());
                        }
                        """)
                .source("pack.flora.Node", "package pack.flora; final class Node { private Node self = this; private int size = 1; }")
                .writeInto(pack, "flora.jar");
        var settings = schemaOf(pack, NO_BUILT_INS).type("pack.flora.FloraSettings");

        assertEquals(Optional.of(Map.of("size", 1L)), option(settings, "node").defaultValue(), "a reference back to itself is left out");
        assertEquals(Optional.empty(), option(settings, "ratio").defaultValue());
        assertEquals(Optional.empty(), option(settings, "names").defaultValue());
        assertEquals(Optional.empty(), option(settings, "nodes").defaultValue(), "a set of objects has no order to write");
    }

    @Test
    void unsupportedFieldTypesFailTheLoadNamingClassFieldAndModule() {
        assertLoadFails("Field pack.flora.FloraSettings.id of " + FLORA_SOURCE + " uses java.util.UUID, a JDK class settings cannot use; "
                + ValueKinds.SUPPORTED_TYPES, "private UUID id;");
        assertLoadFails("Field pack.flora.FloraSettings.name of " + FLORA_SOURCE + " uses java.util.Optional<java.lang.String>;"
                + " only List, Set and Map may have type arguments", "private Optional<String> name;");
        assertLoadFails("Field pack.flora.FloraSettings.names of " + FLORA_SOURCE + " uses java.util.List without type arguments;"
                + " declare it as List<...>, Set<...> or Map<String, ...>", "@SuppressWarnings(\"rawtypes\") private List names;");
        assertLoadFails("Field pack.flora.FloraSettings.names of " + FLORA_SOURCE + " uses java.util.ArrayList<java.lang.String>;"
                + " declare it as List<...>, Set<...> or Map<String, ...>", "private ArrayList<String> names;");
        assertLoadFails("Field pack.flora.FloraSettings.weights of " + FLORA_SOURCE + " uses java.util.Map<java.lang.Integer, java.lang.String>;"
                + " map keys must be String", "private Map<Integer, String> weights;");
        assertLoadFails("Field pack.flora.FloraSettings.letter of " + FLORA_SOURCE + " uses char, which settings cannot use; "
                + ValueKinds.SUPPORTED_TYPES, "private char letter;");
        assertLoadFails("Field pack.flora.FloraSettings.palette of " + FLORA_SOURCE + " uses com.volmit.resource.ResourceKey<java.lang.String>, which names"
                + " a Java type, not something studio can list; use ResourceId, with @Ref when it names a block, biome or other listed entry",
                "private ResourceKey<String> palette;");
        assertLoadFails("Field pack.flora.FloraSettings.names of " + FLORA_SOURCE + " uses ? extends java.lang.String, which is not a"
                + " concrete type; name the type, such as List<String>", "private List<? extends String> names;");
    }

    @Test
    void nestedClassesMustBeConcreteStaticAndConstructible() {
        assertLoadFails("Field pack.flora.FloraSettings.shape of " + FLORA_SOURCE + " uses pack.flora.Shape, which is abstract;"
                + " settings fields need a concrete class", "private Shape shape;", "pack.flora.Shape", "package pack.flora; interface Shape {}");
        assertLoadFails("Field pack.flora.FloraSettings.inner of " + FLORA_SOURCE + " uses pack.flora.FloraSettings$Inner, which is an"
                + " inner class; declare it static", "private Inner inner; class Inner {}");
        assertLoadFails("Class pack.flora.Shape of " + FLORA_SOURCE + " has no no-argument constructor; the host creates settings with it"
                + " and reads their defaults from it, so declare one, private if nothing else should use it",
                "private Shape shape;", "pack.flora.Shape", "package pack.flora; record Shape(int size) {}");

        var throwing = loadFailure("private Shape shape;", "pack.flora.Shape",
                "package pack.flora; final class Shape { Shape() { throw new IllegalStateException(\"no shapes today\"); } }");
        assertEquals("The no-argument constructor of pack.flora.Shape of " + FLORA_SOURCE + " threw java.lang.IllegalStateException:"
                + " no shapes today; the host creates settings with it, so it must not fail", throwing.getMessage());
        assertEquals("no shapes today", throwing.getCause().getMessage());
    }

    @Test
    void referencesOnIncompatibleFieldsFailTheLoad() {
        assertLoadFails("Field pack.flora.FloraSettings.count of " + FLORA_SOURCE + " has @Ref(BLOCK) on int, but @Ref fits only String"
                + " and ResourceId and lists, sets and arrays of them", "@Ref(Reference.BLOCK) private int count;");
        assertLoadFails("Field pack.flora.FloraSettings.flower of " + FLORA_SOURCE + " has @Ref(BIOME) on BlockState, which always names"
                + " a block; remove the @Ref or use String or ResourceId", "@Ref(Reference.BIOME) private BlockState flower;");
        assertLoadFails("Field pack.flora.FloraSettings.blocks of " + FLORA_SOURCE + " has @Ref(BLOCK) on java.util.Map<java.lang.String,"
                + " java.lang.String>, but @Ref fits only String and ResourceId and lists, sets and arrays of them",
                "@Ref(Reference.BLOCK) private Map<String, String> blocks;");
        assertTrue(loadFailure("@Ref(Reference.ENTITY) private List<Integer> counts;").getMessage()
                .contains("has @Ref(ENTITY) on java.lang.Integer"));
    }

    @Test
    void snippetNamesAreFolderNamesThatBelongToOneClass() {
        assertLoadFails("Snippet name 'Big Patch' of pack.flora.Patch of " + FLORA_SOURCE + " is not a folder name; packs write snippets under"
                + " snippet/<name>/, so use lowercase letters, digits, '-' and '_'",
                "private Patch patch;", "pack.flora.Patch", "package pack.flora; @com.volmit.config.Snippet(\"Big Patch\") final class Patch {}");
        assertLoadFails("Snippet name 'patch' is used by pack.flora.Patch of " + FLORA_SOURCE + " and by pack.flora.Plot of " + FLORA_SOURCE
                + "; studio describes snippet/patch/ by one class, so give each its own name",
                "private Patch patch; private Plot plot;",
                "pack.flora.Patch", "package pack.flora; @com.volmit.config.Snippet(\"patch\") final class Patch {}",
                "pack.flora.Plot", "package pack.flora; @com.volmit.config.Snippet(\"patch\") final class Plot {}");
    }
}

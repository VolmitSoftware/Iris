package com.volmit.host;

import com.volmit.FeatureAttachment.Target;
import com.volmit.config.Reference;
import com.volmit.example.chunk.GrassModule;
import com.volmit.example.chunk.TerrainModule;
import com.volmit.example.hosttypes.Palette;
import com.volmit.example.placement.PlacementModules;
import com.volmit.example.terrain.LayersModule;
import com.volmit.host.schema.FeatureRole;
import com.volmit.host.schema.FeatureSchema;
import com.volmit.host.schema.ObjectType;
import com.volmit.host.schema.PackSchema;
import com.volmit.host.schema.ResourceKindSchema;
import com.volmit.host.schema.SchemaOption;
import com.volmit.host.schema.TextFormat;
import com.volmit.host.schema.ValueKind.BooleanKind;
import com.volmit.host.schema.ValueKind.DecimalKind;
import com.volmit.host.schema.ValueKind.EnumConstant;
import com.volmit.host.schema.ValueKind.EnumKind;
import com.volmit.host.schema.ValueKind.HostKind;
import com.volmit.host.schema.ValueKind.IntegerKind;
import com.volmit.host.schema.ValueKind.ListKind;
import com.volmit.host.schema.ValueKind.MapKind;
import com.volmit.host.schema.ValueKind.ObjectKind;
import com.volmit.host.schema.ValueKind.ReferenceKind;
import com.volmit.host.schema.ValueKind.ResourceReferenceKind;
import com.volmit.host.schema.ValueKind.TextKind;
import com.volmit.resource.ResourceId;
import com.volmit.resource.ResourceKind;
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
    private static final String HOST_TYPES = "com.volmit.example.hosttypes";
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
            @Feature(id = "example:flora", name = "Flora", targets = {Target.REGION, Target.BIOME})
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
            @Snippet("example/flora/patch")
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

            @Feature(id = "example:flora/wind", name = "Wind", targets = Target.DIMENSION)
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

            @Snippet("example/flora/bush")
            @Feature(id = "example:flora/bush", name = "Bush", targets = Target.OBJECT_PLACEMENT)
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
    void attachesEveryModuleFeatureToItsTargetsUnderItsId() {
        var schema = floraSchema();

        assertEquals(List.of("example:flora"), names(schema.fields(Target.BIOME), FeatureSchema::key));
        assertEquals(List.of("example:flora"), names(schema.fields(Target.REGION), FeatureSchema::key));
        assertEquals(List.of("example:flora/wind"), names(schema.fields(Target.DIMENSION), FeatureSchema::key));
        assertEquals(List.of("example:flora/bush"), names(schema.fields(Target.OBJECT_PLACEMENT), FeatureSchema::key));
        assertTrue(schema.features().stream().allMatch(feature -> feature.alias().isEmpty()), "pack modules have no alias");
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
    void describesOptionsInDeclarationOrderWithChecksAndDefaults() {
        var settings = floraSchema().type("pack.flora.FloraSettings");

        assertEquals(List.of("density", "chance", "spread", "seedOffset", "enabled", "label", "palette", "ground", "biomes",
                        "drops", "flower", "season", "seasons", "weights", "patch", "extra"),
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
        assertEquals(List.of("radius", "bloom", "child"), patch.options().stream().map(SchemaOption::name).toList());
        assertEquals(new ObjectKind("pack.flora.Patch"), option(patch, "child").kind());
        assertEquals("A smaller patch inside this one", option(patch, "child").description());
        assertEquals(OptionalDouble.of(1), option(patch, "radius").minimum());
    }

    @Test
    void listsSnippetsObjectGeneratorsAndExplicitlyRegisteredSettings() {
        var schema = floraSchema();

        assertEquals(List.of("example/flora/bush", "example/flora/patch"), schema.snippets().stream().map(type -> type.snippet().orElseThrow()).toList());
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
        assertEquals(List.of("example:moss", "iris:snow", "iris:stilt"), names(schema.fields(Target.OBJECT_PLACEMENT), FeatureSchema::key));
        assertEquals(List.of("", "snow", "stilt"), names(schema.fields(Target.OBJECT_PLACEMENT), feature -> feature.alias().orElse("")),
                "built-ins keep their short aliases");

        var stilt = schema.fields(Target.OBJECT_PLACEMENT).get(2);
        assertEquals("Stilts", stilt.module().name());
        assertEquals(new ObjectType("com.volmit.example.placement.StiltSettings", "Fills the space below an object down to the ground",
                Optional.empty(), List.of(new SchemaOption("maxDepth", new IntegerKind(), "Maximum blocks filled below each lowest object block",
                false, OptionalDouble.of(1), OptionalDouble.empty(), Optional.of(16L)))), stilt.settings());

        var grass = schema.fields(Target.BIOME).getFirst();
        assertEquals(new SchemaOption("block", new ReferenceKind(Reference.BLOCK, TextFormat.BLOCK_STATE), "Block placed on top of each column",
                false, OptionalDouble.empty(), OptionalDouble.empty(), Optional.of("minecraft:grass_block")), grass.settings().options().getFirst());
    }

    @Test
    void builtInSettingsMayHoldHostTypesWhichTheHostDescribesItself() {
        var builtIns = List.<com.volmit.IrisModule>of(new LayersModule());
        var schema = schemaOf(pack("layers"), ModuleHost.of(builtIns).describingHostTypesIn(HOST_TYPES));

        var layers = schema.fields(Target.BIOME).getFirst();
        var option = layers.settings().options().getFirst();
        assertEquals(new ListKind(new HostKind(Palette.class.getName()), false), option.kind());
        assertEquals(Optional.of(List.of()), option.defaultValue());
        assertTrue(schema.types().stream().noneMatch(type -> type.name().equals(Palette.class.getName())),
                "the host describes its own types, so their fields are never read");
        assertEquals(Optional.of(option), layers.shorthand(), "one option: packs may write the bare list");

        var undeclared = assertThrows(IllegalArgumentException.class, () -> schemaOf(pack("layers"), ModuleHost.of(builtIns)));
        assertTrue(undeclared.getMessage().startsWith("Field " + Palette.class.getName() + ".anything of settings iris:layers"),
                undeclared.getMessage());
        assertThrows(IllegalArgumentException.class, () -> NO_BUILT_INS.describingHostTypesIn("not a package"));
    }

    @Test
    void packModulesCannotUseHostTypes() {
        var host = NO_BUILT_INS.exposing(HOST_TYPES).describingHostTypesIn(HOST_TYPES);
        assertEquals("Field pack.flora.FloraSettings.palette of " + FLORA_SOURCE + " uses " + Palette.class.getName()
                        + ", a class the host describes itself, which only settings of modules that ship with the host may use; "
                        + ValueKinds.SUPPORTED_TYPES,
                loadFailure(host, "private " + Palette.class.getName() + " palette;").getMessage());
    }

    @Test
    void onlySettingsWithOneOptionHaveAShorthand() {
        var flora = floraSchema().fields(Target.BIOME).getFirst();
        assertEquals(Optional.empty(), flora.shorthand());
    }

    /** A pack whose module example:flora registers pack.flora.FloraSettings with {@code body} as its class body. */
    private IllegalArgumentException loadFailure(String body, String... otherSources) {
        return loadFailure(NO_BUILT_INS, body, otherSources);
    }

    private IllegalArgumentException loadFailure(ModuleHost host, String body, String... otherSources) {
        var jar = module("pack.flora.FloraModule", FLORA,
                "public void register(com.volmit.FeatureRegistry registry) { registry.registerSettings(FloraSettings.class); }", NO_STAGES)
                .compileAgainstHostOf(Palette.class)
                .source("pack.flora.FloraSettings", """
                        package pack.flora;

                        import com.volmit.FeatureAttachment.Target;
                        import com.volmit.IrisFeature;
                        import com.volmit.block.BlockState;
                        import com.volmit.config.*;
                        import com.volmit.resource.*;
                        import java.util.*;

                        @Feature(id = "example:flora", name = "Flora", targets = Target.BIOME)
                        public final class FloraSettings implements IrisFeature {
                        %s
                        }
                        """.formatted(body));
        for (int index = 0; index < otherSources.length; index += 2) {
            jar.source(otherSources[index], otherSources[index + 1]);
        }
        var pack = pack("broken-" + ++brokenPacks);
        jar.writeInto(pack, "flora.jar");
        return assertThrows(IllegalArgumentException.class, () -> LoadedPack.load(pack, host));
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

                        @Feature(id = "example:flora", name = "Flora", targets = Target.BIOME)
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
    void snippetNamesAreFolderPathsThatBelongToOneClass() {
        var notAPath = "is not a folder path; packs write snippets under snippet/<name>/, so use lowercase letters, digits, '-', '_'"
                + " and '.', with '/' between folders";
        assertLoadFails("Snippet name 'example/flora/Big Patch' of pack.flora.Patch of " + FLORA_SOURCE + " " + notAPath,
                "private Patch patch;", "pack.flora.Patch", "package pack.flora; @com.volmit.config.Snippet(\"example/flora/Big Patch\") final class Patch {}");
        assertLoadFails("Snippet name 'example/flora/../moss' of pack.flora.Patch of " + FLORA_SOURCE + " " + notAPath,
                "private Patch patch;", "pack.flora.Patch", "package pack.flora; @com.volmit.config.Snippet(\"example/flora/../moss\") final class Patch {}");
        assertLoadFails("Snippet name 'example/flora/patch' is used by pack.flora.Patch of " + FLORA_SOURCE + " and by pack.flora.Plot of " + FLORA_SOURCE
                + "; studio describes snippet/example/flora/patch/ by one class, so give each its own name",
                "private Patch patch; private Plot plot;",
                "pack.flora.Patch", "package pack.flora; @com.volmit.config.Snippet(\"example/flora/patch\") final class Patch {}",
                "pack.flora.Plot", "package pack.flora; @com.volmit.config.Snippet(\"example/flora/patch\") final class Plot {}");
    }

    @Test
    void packSnippetsLiveUnderTheirModulesFolder() {
        assertLoadFails("Snippet name 'patch' of pack.flora.Patch of " + FLORA_SOURCE + " is not under a module of the pack; snippets of pack"
                + " modules start with their module's id as a folder path, such as 'example/flora/patch' (folder snippet/example/flora/patch/)",
                "private Patch patch;", "pack.flora.Patch", "package pack.flora; @com.volmit.config.Snippet(\"patch\") final class Patch {}");
        assertLoadFails("Snippet name 'example/florae/patch' of pack.flora.Patch of " + FLORA_SOURCE + " is not under a module of the pack;"
                + " snippets of pack modules start with their module's id as a folder path, such as 'example/flora/patch'"
                + " (folder snippet/example/flora/patch/)",
                "private Patch patch;", "pack.flora.Patch", "package pack.flora; @com.volmit.config.Snippet(\"example/florae/patch\") final class Patch {}");

        var pack = pack("module-folder");
        module("pack.flora.FloraModule", FLORA,
                "public void register(com.volmit.FeatureRegistry registry) { registry.registerSettings(FloraSettings.class); }", NO_STAGES)
                .source("pack.flora.FloraSettings", """
                        package pack.flora;

                        @com.volmit.config.Snippet("example/flora")
                        @com.volmit.config.Feature(id = "example:flora", name = "Flora", targets = com.volmit.FeatureAttachment.Target.BIOME)
                        public final class FloraSettings implements com.volmit.IrisFeature {}
                        """)
                .writeInto(pack, "flora.jar");
        assertEquals(List.of("example/flora"), schemaOf(pack, NO_BUILT_INS).snippets().stream()
                .map(type -> type.snippet().orElseThrow()).toList(), "the module's own folder is under its path");
    }

    @Test
    void packModulesCannotSetAnAlias() {
        var pack = pack("alias");
        module("pack.flora.FloraModule", FLORA,
                "public void register(com.volmit.FeatureRegistry registry) { registry.registerSettings(FloraSettings.class); }", NO_STAGES)
                .source("pack.flora.FloraSettings", """
                        package pack.flora;

                        @com.volmit.config.Feature(id = "example:flora", name = "Flora", alias = "flora", targets = com.volmit.FeatureAttachment.Target.BIOME)
                        public final class FloraSettings implements com.volmit.IrisFeature {}
                        """)
                .writeInto(pack, "flora.jar");

        var failure = assertThrows(IllegalArgumentException.class, () -> LoadedPack.load(pack, NO_BUILT_INS));

        assertEquals("Settings pack.flora.FloraSettings of module example:flora from modules/flora.jar set alias 'flora'; only modules"
                + " that ship with the host may set one, so remove it: packs write these settings under their id \"example:flora\"",
                failure.getMessage());
    }

    @Test
    void packReferencesFitOnlyPlainText() {
        assertLoadFails("Field pack.flora.FloraSettings.home of " + FLORA_SOURCE + " has @Ref(PACK_BIOME) on ResourceId, but packs name"
                + " their biomes, regions and objects by path without a namespace, such as desert/dunes; use String",
                "@Ref(Reference.PACK_BIOME) private ResourceId home;");
        assertTrue(loadFailure("@Ref(Reference.PACK_OBJECT) private List<ResourceId> objects;").getMessage()
                .contains("has @Ref(PACK_OBJECT) on ResourceId"));
    }

    @Test
    void inheritedOptionsComeBeforeTheirSubclasssOwn() {
        var pack = pack("inherited");
        module("pack.flora.FloraModule", FLORA,
                "public void register(com.volmit.FeatureRegistry registry) { registry.registerSettings(FloraSettings.class); }", NO_STAGES)
                .source("pack.flora.Base", "package pack.flora; abstract class Base { private int zeta; private int alpha; }")
                .source("pack.flora.FloraSettings", """
                        package pack.flora;

                        @com.volmit.config.Feature(id = "example:flora", name = "Flora", targets = com.volmit.FeatureAttachment.Target.BIOME)
                        public final class FloraSettings extends Base implements com.volmit.IrisFeature { private int omega; private int beta; }
                        """)
                .writeInto(pack, "flora.jar");

        var settings = schemaOf(pack, NO_BUILT_INS).type("pack.flora.FloraSettings");

        assertEquals(List.of("zeta", "alpha", "omega", "beta"), settings.options().stream().map(SchemaOption::name).toList());
    }

    private static final String CASTLE = "example:castle";
    private static final String CASTLE_MEMBERS = """
            public void register(com.volmit.FeatureRegistry registry) {
                registry.registerResourceKind(com.volmit.resource.ResourceKind.of("example:castle/keep", "keeps", Keep.class));
                registry.registerSettings(CastleSettings.class);
            }
            """;

    private static TestModuleJar castle() {
        return module("pack.castle.CastleModule", CASTLE, CASTLE_MEMBERS, NO_STAGES)
                .source("pack.castle.Keep", """
                        package pack.castle;

                        import com.volmit.config.*;

                        @Desc("A keep of a castle")
                        public final class Keep {
                            @Desc("Towers on the walls") @MinNumber(1)
                            private int towers = 4;
                            @ResourceRef(Keep.class)
                            private String next = "";
                        }
                        """)
                .source("pack.castle.CastleSettings", """
                        package pack.castle;

                        import com.volmit.FeatureAttachment.Target;
                        import com.volmit.IrisFeature;
                        import com.volmit.config.*;
                        import java.util.List;

                        @Feature(id = "example:castle", name = "Castle", targets = Target.BIOME)
                        public final class CastleSettings implements IrisFeature {
                            @ResourceRef(Keep.class)
                            private List<String> keeps = List.of("main");
                        }
                        """);
    }

    private PackSchema castleSchema(ModuleHost host) {
        var pack = pack("castle-" + ++brokenPacks);
        castle().writeInto(pack, "castle.jar");
        return schemaOf(pack, host);
    }

    @Test
    void describesResourceKindsLikeSettingsAndReferencesToThem() {
        var schema = castleSchema(NO_BUILT_INS);
        var keepKind = ResourceId.parse("example:castle/keep");

        var kind = schema.resources().getFirst();
        assertEquals(new ResourceKindSchema(keepKind, kind.module(), "keeps", new ObjectKind("pack.castle.Keep")), kind);
        assertEquals(CASTLE, kind.module().id().toString());
        assertSame(kind, schema.resource(keepKind));
        var keep = schema.type("pack.castle.Keep");
        assertEquals("A keep of a castle", keep.description());
        assertEquals(new SchemaOption("towers", new IntegerKind(), "Towers on the walls", false, OptionalDouble.of(1),
                OptionalDouble.empty(), Optional.of(4L)), keep.options().getFirst());
        assertEquals(new ResourceReferenceKind(keepKind), keep.options().get(1).kind(), "a resource may name others of its kind");
        var keeps = schema.fields(Target.BIOME).getFirst().settings().options().getFirst();
        assertEquals(new ListKind(new ResourceReferenceKind(keepKind), false), keeps.kind());
        assertEquals(Optional.of(List.of("main")), keeps.defaultValue());
    }

    @Test
    void builtInKindsMayReadIntoHostTypesWhichTheHostDescribesItself() {
        var builtIn = registering("iris:palettes", ResourceKind.of("iris:palettes/palette", "palettes", Palette.class));
        var schema = schemaOf(pack("palettes"), ModuleHost.of(List.of(builtIn)).describingHostTypesIn(HOST_TYPES));

        assertEquals(new HostKind(Palette.class.getName()), schema.resources().getFirst().type());
        assertTrue(schema.types().isEmpty(), "the host describes its own types");
    }

    @Test
    void kindFoldersBelongToOneKindAndNeverToTheHost() {
        var builtIn = registering("iris:keeps", ResourceKind.of("iris:keeps/keep", "keeps", Palette.class));
        var shared = assertThrows(IllegalArgumentException.class, () -> castleSchema(ModuleHost.of(List.of(builtIn))));
        assertEquals("resource kind example:castle/keep of module example:castle from modules/castle.jar reads folder 'keeps',"
                + " which resource kind iris:keeps/keep of module iris:keeps from host classpath reads already; a folder holds"
                + " one kind, so give one of them a folder of its own", shared.getMessage());

        var reserved = assertThrows(IllegalArgumentException.class, () -> castleSchema(NO_BUILT_INS.reservingFolders("keeps")));
        assertEquals("Resource kind example:castle/keep of module example:castle from modules/castle.jar reads folder 'keeps',"
                + " which the host reads itself; give the kind a folder of its own", reserved.getMessage());
        assertThrows(IllegalArgumentException.class, () -> NO_BUILT_INS.reservingFolders("not/a/folder"));
    }

    @Test
    void packKindsCannotReadIntoHostTypes() {
        var jar = module("pack.castle.CastleModule", CASTLE, """
                public void register(com.volmit.FeatureRegistry registry) {
                    registry.registerResourceKind(com.volmit.resource.ResourceKind.of("example:castle/palette", "palettes",
                            %s.class));
                }
                """.formatted(Palette.class.getName()), NO_STAGES).compileAgainstHostOf(Palette.class);
        var pack = pack("castle-host-type");
        jar.writeInto(pack, "castle.jar");
        var host = NO_BUILT_INS.exposing(HOST_TYPES).describingHostTypesIn(HOST_TYPES);

        var failure = assertThrows(IllegalArgumentException.class, () -> LoadedPack.load(pack, host));
        assertEquals("Resource kind example:castle/palette of module example:castle from modules/castle.jar reads into "
                + Palette.class.getName() + ", a class the host describes itself, which only modules that ship with the host"
                + " may use; read the files into a class of the module", failure.getMessage());
    }

    @Test
    void resourceReferencesNameARegisteredKindOnPlainText() {
        assertLoadFails("Field pack.flora.FloraSettings.keep of " + FLORA_SOURCE + " has @ResourceRef(java.lang.Integer), but no"
                + " module of the pack registers a resource kind read into that class; name the class of a kind registered with"
                + " FeatureRegistry.registerResourceKind", "@ResourceRef(Integer.class) private String keep;");
        assertLoadFails("Field pack.flora.FloraSettings.count of " + FLORA_SOURCE + " has @ResourceRef(java.lang.Integer) on int,"
                + " but @ResourceRef fits only String and lists, sets and arrays of it", "@ResourceRef(Integer.class) private int count;");
        assertLoadFails("Field pack.flora.FloraSettings.keep of " + FLORA_SOURCE + " has @ResourceRef(java.lang.Integer) on"
                + " ResourceId, but packs name a kind's resources by key without a namespace, such as castle/start; use String",
                "@ResourceRef(Integer.class) private ResourceId keep;");
        assertLoadFails("Field pack.flora.FloraSettings.keep of " + FLORA_SOURCE + " has both @Ref(BLOCK) and"
                + " @ResourceRef(java.lang.Integer); an option names one kind of thing, so keep one",
                "@Ref(Reference.BLOCK) @ResourceRef(Integer.class) private String keep;");
    }

    /** A built-in module {@code id} that registers {@code kind}. */
    private static com.volmit.IrisModule registering(String id, ResourceKind<?> kind) {
        return new com.volmit.IrisModule() {
            public com.volmit.ModuleInfo info() { return com.volmit.ModuleInfo.of(id, "Kinds", "test"); }
            public void install(com.volmit.stage.StageGraph graph) {}
            public void register(com.volmit.FeatureRegistry registry) { registry.registerResourceKind(kind); }
        };
    }
}

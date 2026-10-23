package art.arcane.iris.core.modules;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.pack.PackValidationResult;
import art.arcane.iris.core.pack.PackValidator;
import art.arcane.iris.core.project.ModuleResourceSchemas;
import art.arcane.iris.core.project.SchemaTestPlatform;
import art.arcane.volmlib.util.json.JSONObject;
import com.volmit.host.ResourceKindRegistration;
import com.volmit.resource.ResourceId;
import com.volmit.resource.ResourceKind;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * A pack module that declares a resource kind of its own class and a validator, loaded from a real jar in the
 * pack's {@code modules} folder: Iris reads the kind's folder generically, serves it through PackResources, describes
 * it for studio and runs the validator wherever it validates the pack.
 */
public class PackModuleResourceKindTest {
    private static final String MODULE = """
            package pack.castle;

            import com.volmit.*;
            import com.volmit.resource.ResourceKind;

            public final class CastleModule implements IrisModule {
                public static final ResourceKind<Keep> KEEPS = ResourceKind.of("example:castle/keep", "keeps", Keep.class);

                public ModuleInfo info() { return ModuleInfo.of("example:castle", "Castles", "1"); }
                public void setup(ModuleSetup setup) {
                    setup.registry().registerResourceKind(KEEPS);
                    setup.registry().registerValidator(new KeepValidator());
                }
            }
            """;
    private static final String KEEP = """
            package pack.castle;

            import com.volmit.config.*;

            @Desc("A keep of a castle")
            public final class Keep {
                @Desc("Towers on the walls") @MinNumber(1)
                private int towers = 4;
                @ResourceRef(Keep.class)
                private String next = "";

                public int towers() { return towers; }
                public String next() { return next; }
            }
            """;
    private static final String VALIDATOR = """
            package pack.castle;

            import com.volmit.resource.ResourceId;
            import com.volmit.validation.*;

            public final class KeepValidator implements Validator {
                public void validate(ValidationContext pack) {
                    for (ResourceId id : pack.resources().ids(Keep.class)) {
                        Keep keep = pack.resources().require(CastleModule.KEEPS.key(id));
                        if (keep.towers() > 8) {
                            pack.report(Problem.error(Subject.file("keeps/" + id.path() + ".json"), "Keep has " + keep.towers() + " towers; at most 8 fit."));
                        }
                        ResourceId next = new ResourceId(id.namespace(), keep.next().isEmpty() ? id.path() : keep.next());
                        if (pack.resources().find(CastleModule.KEEPS.key(next)).isEmpty()) {
                            pack.report(Problem.warning(Subject.resource(id), "Keep leads to missing keep " + keep.next() + "."));
                        }
                    }
                }
            }
            """;
    private static final String HOST_FOLDER_MODULE = """
            package pack.greedy;

            import com.volmit.*;
            import com.volmit.resource.ResourceKind;

            public final class GreedyModule implements IrisModule {
                public ModuleInfo info() { return ModuleInfo.of("example:greedy", "Greedy", "1"); }
                public void setup(ModuleSetup setup) {
                    setup.registry().registerResourceKind(ResourceKind.of("example:greedy/biome", "biomes", Grab.class));
                }
            }
            """;
    private static final String GRAB = """
            package pack.greedy;

            public final class Grab {
            }
            """;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void aPackModulesKindIsReadFromItsFolderAndServedByType() throws Exception {
        Path pack = castlePack("castles");
        write(pack, "keeps/main.json", "{\"towers\":6,\"next\":\"towers/north\"}");
        write(pack, "keeps/towers/north.json", "{}");
        IrisData data = IrisData.openValidation(pack.toFile());
        try {
            ResourceKind<?> keeps = keepKind(data);
            ResourceId main = new ResourceId("castles", "main");

            assertEquals(List.of(main, new ResourceId("castles", "towers/north")), data.getPackResources().ids(keeps.type()));
            Object keep = data.getPackResources().find(keeps.key(main)).orElseThrow();
            assertEquals(6, keep.getClass().getMethod("towers").invoke(keep));
            assertEquals("towers/north", keep.getClass().getMethod("next").invoke(keep));
            Object north = data.getPackResources().find(keeps.key(new ResourceId("castles", "towers/north"))).orElseThrow();
            assertEquals("an omitted field keeps its default", 4, north.getClass().getMethod("towers").invoke(north));
            assertSame("read once per pack load", keep, data.getPackResources().find(keeps.key(main)).orElseThrow());
            assertTrue(data.getPackResources().find(keeps.key(new ResourceId("castles", "missing"))).isEmpty());
            assertTrue(data.getPackResources().moduleFolderNames().containsAll(List.of("keeps", "structures")));
            assertTrue(data.resourceFolderNames().contains("keeps"));
        } finally {
            data.close();
        }
    }

    @Test
    public void aModuleResourceThatFailsToReadIsAnErrorNotAbsent() throws Exception {
        Path pack = castlePack("broken-castles");
        write(pack, "keeps/main.json", "{\"towers\":\"many\"}");
        IrisData data = IrisData.openValidation(pack.toFile());
        try {
            ResourceKind<?> keeps = keepKind(data);

            assertThrows(IllegalStateException.class, () -> data.getPackResources().find(keeps.key(new ResourceId("broken-castles", "main"))));
        } finally {
            data.close();
        }
    }

    @Test
    public void packValidationRunsThePackModulesValidatorWithItsSeverities() throws Exception {
        Path pack = castlePack("validated-castles");
        write(pack, "keeps/main.json", "{\"towers\":12,\"next\":\"gone\"}");

        PackValidationResult result = PackValidator.validate(pack.toFile());

        assertTrue(result.getBlockingErrors().toString(),
                result.getBlockingErrors().contains("keeps/main.json: Keep has 12 towers; at most 8 fit."));
        assertTrue(result.getWarnings().toString(),
                result.getWarnings().contains("validated-castles:main: Keep leads to missing keep gone."));
    }

    @Test
    public void studioDescribesTheKindFromItsClassAndCompletesReferencesWithItsKeys() throws Exception {
        Path pack = castlePack("studio-castles");
        write(pack, "keeps/main.json", "{}");
        write(pack, "keeps/towers/north.json", "{}");
        IrisData data = IrisData.openValidation(pack.toFile());
        try (SchemaTestPlatform ignored = SchemaTestPlatform.bind(temporaryFolder.getRoot())) {
            JSONObject entry = ModuleResourceSchemas.entries(data, true).stream()
                    .filter(candidate -> candidate.getString("url").equals("./.iris/schema/keeps-schema.json"))
                    .findFirst()
                    .orElseThrow();

            assertEquals("/keeps/*.json", entry.getJSONArray("fileMatch").getString(0));
            JSONObject schema = new JSONObject(Files.readString(pack.resolve(".iris/schema/keeps-schema.json")));
            JSONObject properties = schema.getJSONObject("properties");
            assertEquals("A keep of a castle", schema.getString("description"));
            assertEquals(1, properties.getJSONObject("towers").getInt("minimum"));
            assertEquals("#/definitions/erzkeeps", properties.getJSONObject("next").getString("$ref"));
            assertEquals("[\"main\",\"towers/north\"]",
                    schema.getJSONObject("definitions").getJSONObject("erzkeeps").getJSONArray("enum").toString());
        } finally {
            data.close();
        }
    }

    @Test
    public void aKindMayNotReadAFolderIrisReadsItself() throws Exception {
        Path pack = temporaryFolder.newFolder("greedy").toPath();
        PackModuleJar.write(pack, "greedy.jar", "pack.greedy.GreedyModule",
                Map.of("pack.greedy.GreedyModule", HOST_FOLDER_MODULE, "pack.greedy.Grab", GRAB));
        IrisData data = IrisData.openValidation(pack.toFile());
        try {
            PackModulesException failure = assertThrows(PackModulesException.class, () -> data.getModules().pack());

            assertEquals("Pack greedy cannot load its modules: Resource kind example:greedy/biome of module example:greedy"
                    + " from modules/greedy.jar reads folder 'biomes', which the host reads itself; give the kind a folder of"
                    + " its own", failure.getMessage());
        } finally {
            data.close();
        }
    }

    private Path castlePack(String name) throws Exception {
        File pack = temporaryFolder.newFolder(name);
        write(pack.toPath(), "dimensions/main.json", "{\"regions\":[\"region\"]}");
        write(pack.toPath(), "regions/region.json", "{\"landBiomes\":[\"biome\"]}");
        write(pack.toPath(), "biomes/biome.json", "{\"name\":\"Biome\"}");
        PackModuleJar.write(pack.toPath(), "castle.jar", "pack.castle.CastleModule",
                Map.of("pack.castle.CastleModule", MODULE, "pack.castle.Keep", KEEP, "pack.castle.KeepValidator", VALIDATOR));
        return pack.toPath();
    }

    private static ResourceKind<?> keepKind(IrisData data) {
        return data.getModules().pack().catalog().resourceKinds().stream()
                .map(ResourceKindRegistration::kind)
                .filter(kind -> kind.id().toString().equals("example:castle/keep"))
                .findFirst()
                .orElseThrow();
    }

    private static void write(Path root, String relative, String content) throws Exception {
        Path path = root.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}

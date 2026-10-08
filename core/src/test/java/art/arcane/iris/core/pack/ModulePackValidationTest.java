package art.arcane.iris.core.pack;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.IrisPackResources;
import art.arcane.iris.core.modules.IrisValidationContext;
import com.volmit.FeatureRegistry;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.host.DiscoveredModule;
import com.volmit.host.ValidatorRegistration;
import com.volmit.resource.ResourceId;
import com.volmit.stage.StageGraph;
import com.volmit.validation.Problem;
import com.volmit.validation.Subject;
import com.volmit.validation.Validator;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Module validators run where Iris validates packs, and what they report keeps its severity. */
public class ModulePackValidationTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void errorsBlockAndWarningsOnlyShowEachWithItsSubject() {
        IrisData data = packData();
        Validator validator = pack -> {
            pack.report(Problem.error(Subject.file("keeps/main.json"), "Keep main has no walls."));
            pack.report(Problem.warning(Subject.resource(ResourceId.parse("castles:main")), "Keep main is very tall."));
        };

        ModulePackValidation.Findings findings = ModulePackValidation.validate(data, List.of(registration("example:keeps", validator)));

        assertEquals(List.of("keeps/main.json: Keep main has no walls."), findings.errors());
        assertEquals(List.of("castles:main: Keep main is very tall."), findings.warnings());
    }

    @Test
    public void aValidatorThatThrowsFailsValidationNamingItsModuleAndTheOthersStillRun() {
        IrisData data = packData();
        Validator broken = pack -> {
            pack.report(Problem.warning(Subject.file("keeps/main.json"), "Reported before failing."));
            throw new IllegalStateException("no walls to count");
        };
        Validator healthy = pack -> pack.report(Problem.error(Subject.file("keeps/other.json"), "Still checked."));

        ModulePackValidation.Findings findings = ModulePackValidation.validate(data,
                List.of(registration("example:broken", broken), registration("example:healthy", healthy)));

        assertEquals(List.of("Module example:broken from host classpath failed while validating the pack:"
                + " java.lang.IllegalStateException: no walls to count", "keeps/other.json: Still checked."), findings.errors());
        assertEquals(List.of("keeps/main.json: Reported before failing."), findings.warnings());
    }

    @Test
    public void validatorsSeeThePacksResourcesAndIrisSeesItsOwnData() {
        IrisData data = packData();
        IrisPackResources resources = mock(IrisPackResources.class);
        when(data.getPackResources()).thenReturn(resources);
        List<Object> seen = new ArrayList<>();
        Validator validator = pack -> {
            seen.add(pack.resources());
            seen.add(IrisValidationContext.of(pack).data());
        };

        ModulePackValidation.validate(data, List.of(registration("example:keeps", validator)));

        assertSame(resources, seen.get(0));
        assertSame(data, seen.get(1));
        assertThrows(IllegalArgumentException.class, () -> IrisValidationContext.of(null));
    }

    @Test
    public void packValidationRunsTheValidatorsOfThePacksModulesAndReportsModulesThatFailToLoad() throws Exception {
        File pack = minimalPack("broken-modules");
        Files.createDirectories(pack.toPath().resolve("modules"));
        Files.writeString(pack.toPath().resolve("modules/broken.jar"), "not a jar", StandardCharsets.UTF_8);

        PackValidationResult result = PackValidator.validate(pack);

        assertTrue(result.getBlockingErrors().toString(), result.getBlockingErrors().stream()
                .anyMatch(error -> error.startsWith("Pack broken-modules cannot load its modules: ")));
    }

    private IrisData packData() {
        IrisData data = mock(IrisData.class);
        when(data.getDataFolder()).thenReturn(temporaryFolder.getRoot());
        return data;
    }

    private File minimalPack(String name) throws Exception {
        File pack = temporaryFolder.newFolder(name);
        write(pack, "dimensions/main.json", "{\"regions\":[\"region\"]}");
        write(pack, "regions/region.json", "{\"landBiomes\":[\"biome\"]}");
        write(pack, "biomes/biome.json", "{\"name\":\"Biome\"}");
        return pack;
    }

    private static ValidatorRegistration registration(String moduleId, Validator validator) {
        IrisModule module = new IrisModule() {
            @Override
            public ModuleInfo info() {
                return ModuleInfo.of(moduleId, "Test", "1");
            }

            @Override
            public void install(StageGraph graph) {
            }

            @Override
            public void register(FeatureRegistry registry) {
            }
        };
        return new ValidatorRegistration(validator, DiscoveredModule.builtIn(module));
    }

    private static void write(File root, String relative, String content) throws Exception {
        Path path = root.toPath().resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}

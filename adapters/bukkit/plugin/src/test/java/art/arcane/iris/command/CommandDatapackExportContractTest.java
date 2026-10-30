package art.arcane.iris.command;

import art.arcane.iris.command.handlers.DimensionHandler;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.WorldgenExportRequest;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class CommandDatapackExportContractTest {
    private static final List<String> NAMES = List.of(
            "dimension", "version", "mode", "namespace", "output", "format", "objects", "replace");
    private static final List<String> DEFAULTS = List.of(
            Param.NO_DEFAULT, "", "overworld", "iris_export", "", "zip", "true", "false");

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void exportParametersAreNamedInOrder() throws Exception {
        List<String> names = new ArrayList<>();
        for (Parameter parameter : exportMethod().getParameters()) {
            names.add(parameter.getAnnotation(Param.class).name());
        }

        assertEquals(NAMES, names);
    }

    @Test
    public void dimensionIsPositionalAndEveryOtherParameterIsKeyedWithItsDefault() throws Exception {
        Parameter[] parameters = exportMethod().getParameters();
        List<String> defaults = new ArrayList<>();
        for (Parameter parameter : parameters) {
            defaults.add(parameter.getAnnotation(Param.class).defaultValue());
        }

        assertEquals(DEFAULTS, defaults);
        assertEquals(DimensionHandler.class, parameters[0].getAnnotation(Param.class).customHandler());
    }

    @Test
    public void exportCommandAndParametersUseRegisteredDescriptions() throws Exception {
        Method export = exportMethod();
        Director director = export.getAnnotation(Director.class);

        assertNotNull(director);
        assertEquals(0L, Arrays.stream(director.aliases()).filter((String alias) -> !alias.isBlank()).count());
        assertNotNull(director.descriptionKey(), IrisLanguage.catalog().key(director.descriptionKey()));
        for (Parameter parameter : export.getParameters()) {
            String key = parameter.getAnnotation(Param.class).descriptionKey();
            assertNotNull(key, IrisLanguage.catalog().key(key));
        }
    }

    @Test
    public void validRequestResolvesItsTarget() {
        assertSame(DataVersion.V26_3, CommandDatapack.validateExport(request().build()));
        assertSame(DataVersion.V26_3, CommandDatapack.validateExport(request().version("26.3").build()));
    }

    @Test
    public void unsupportedArgumentsAreRejectedWithSharedMessages() {
        assertEquals("Unsupported datapack export version '1.21.1'. Supported: 26.3",
                reason(request().version("1.21.1").build()));
        assertEquals("Unsupported datapack export version '26.2'. Supported: 26.3",
                reason(request().version("26.2").build()));
        assertEquals("Unknown export mode 'nether'. Expected: preset, dimension, overworld",
                reason(request().mode("nether").build()));
        assertEquals("Export format must be directory or zip.", reason(request().format("rar").build()));
    }

    private WorldgenExportRequest.Builder request() {
        Path root = temporaryFolder.getRoot().toPath();
        return WorldgenExportRequest.builder()
                .pack(root.resolve("packs").resolve("overworld"))
                .dimension("overworld")
                .exportRoot(root.resolve("exports"));
    }

    private static String reason(WorldgenExportRequest request) {
        return assertThrows(IllegalArgumentException.class, () -> CommandDatapack.validateExport(request)).getMessage();
    }

    private static Method exportMethod() throws NoSuchMethodException {
        return CommandDatapack.class.getDeclaredMethod("export", IrisDimension.class, String.class, String.class,
                String.class, String.class, String.class, boolean.class, boolean.class);
    }
}

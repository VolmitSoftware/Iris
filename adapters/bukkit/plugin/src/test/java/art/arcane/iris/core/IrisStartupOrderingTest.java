package art.arcane.iris.core;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class IrisStartupOrderingTest {
    @Test
    public void admissionGateIsRegisteredBeforeStartupValidationBegins() throws Exception {
        String source = Files.readString(Path.of(System.getProperty("iris.startupSource")));
        String onEnable = section(source, "public void onEnable()", "public void onDisable()");

        assertOrdered(onEnable,
                "IrisStartupValidation.begin();",
                "registerEvents(new IrisStartupAdmissionListener(), this);",
                "enable();");
    }

    @Test
    public void externalDatapacksValidateBeforeDimensionPacks() throws Exception {
        String source = Files.readString(Path.of(System.getProperty("iris.startupSource")));
        String enable = section(source, "private boolean enable()", "public void addShutdownHook()");

        assertOrdered(enable,
                "DatapackIngestService.validateOnStartup();",
                "generatorResolver.validateAllPacks();");
    }

    @Test
    public void externalProvidersEnableBeforePackValidation() throws Exception {
        String source = Files.readString(Path.of(System.getProperty("iris.startupSource")));
        String enable = section(source, "private boolean enable()", "public void addShutdownHook()");

        assertOrdered(enable,
                "service.onEnable();",
                "setContentChangeListener(generatorResolver::requestExternalContentRefresh)",
                "generatorResolver.validateAllPacks();");
    }

    @Test
    public void restartRequiredStartupTerminatesAfterPluginInitialization() throws Exception {
        String source = Files.readString(Path.of(System.getProperty("iris.startupSource")));
        String onEnable = section(source, "public void onEnable()", "public void onDisable()");

        assertOrdered(onEnable,
                "BukkitGuiHost.install();",
                "super.onEnable();",
                "IrisStartupValidation.isRestartRequired()",
                "ServerConfigurator.restartAtStartupBoundary(restartReason);");
    }

    private static String section(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue("Missing source section starting with " + startMarker, start >= 0);
        assertTrue("Missing source section ending with " + endMarker, end > start);
        return source.substring(start, end);
    }

    private static void assertOrdered(String source, String... markers) {
        int previous = -1;
        for (String marker : markers) {
            int current = source.indexOf(marker);
            assertTrue("Missing source marker " + marker, current >= 0);
            assertTrue("Source marker is out of order: " + marker, current > previous);
            previous = current;
        }
    }
}

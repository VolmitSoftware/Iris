package art.arcane.iris.core.link;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisPapiLifecycleTest {
    private static final Path PLUGIN_SOURCE = Path.of("src/main/java/art/arcane/iris/Iris.java");
    private static final String SETUP = "private void setupPapi() {";
    private static final String TEARDOWN = "private void teardownPapi() {";

    private static String source() throws Exception {
        // The assertions below match against literals written with \n, so a CRLF checkout has to
        // be folded first or every multi-line expectation misses.
        return Files.readString(PLUGIN_SOURCE).replace("\r\n", "\n");
    }

    private static String body(String declaration) throws Exception {
        String source = source();
        int start = source.indexOf(declaration);

        assertTrue("Iris.java must declare " + declaration, start >= 0);

        int open = source.indexOf('{', start);
        int depth = 0;

        for (int index = open; index < source.length(); index++) {
            char character = source.charAt(index);

            if (character == '{') {
                depth++;
                continue;
            }

            if (character != '}') {
                continue;
            }

            depth--;

            if (depth == 0) {
                return source.substring(open + 1, index);
            }
        }

        throw new AssertionError(declaration + " is not brace balanced");
    }

    @Test
    public void registrationIsGatedOnPlaceholderApiBeingEnabled() throws Exception {
        assertTrue("setupPapi must bail out when PlaceholderAPI is absent",
                body(SETUP).contains("if (!PlaceholderRegistration.isPlaceholderApiEnabled()) {"));
    }

    @Test
    public void theRegistrationTheListenerAndTheStateAreAllRetained() throws Exception {
        String source = source();

        assertTrue(source.contains("private volatile PlaceholderRegistration papiRegistration;"));
        assertTrue(source.contains("private volatile IrisPapiListener papiListener;"));
        assertTrue(source.contains("private volatile IrisPapiState papiState;"));
    }

    @Test
    public void teardownUnregistersTheExpansionTheListenerAndClearsTheState() throws Exception {
        String teardown = body(TEARDOWN);

        assertTrue("the retained registration must be unregistered inside teardownPapi",
                teardown.contains("registration.unregister();"));
        assertTrue("the retained listener must be detached inside teardownPapi",
                teardown.contains("HandlerList.unregisterAll(listener);"));
        assertTrue("the retained state must drop every held world reference inside teardownPapi",
                teardown.contains("state.clear();"));
        assertTrue("teardownPapi must drop the retained listener", teardown.contains("papiListener = null;"));
        assertTrue("teardownPapi must drop the retained registration", teardown.contains("papiRegistration = null;"));
        assertTrue("teardownPapi must drop the retained state", teardown.contains("papiState = null;"));
    }

    @Test
    public void bothDisablePathsTearThePlaceholderSurfaceDown() throws Exception {
        String source = source();

        assertTrue("onDisable must tear the expansion down",
                source.contains("public void onDisable() {\n        pendingWorldReplacements.awaitStagingForShutdown();\n        teardownPapi();"));
        assertTrue("the BileTools pre-unload hook must tear the expansion down",
                source.contains("public void onPreUnload(ReloadAware.PreUnloadReason reason) {\n        teardownPapi();"));
    }

    @Test
    public void aFailedListenerAttachDoesNotLeaveTheExpansionRegistered() throws Exception {
        String setup = body(SETUP);
        int attach = setup.indexOf("registerEvents(listener, this)");

        assertTrue("the listener must be attached inside setupPapi", attach >= 0);

        int rescue = setup.indexOf("} catch (Throwable failure) {", attach);

        assertTrue("the attach must be guarded inside setupPapi", rescue > attach);

        int rollback = setup.indexOf("registration.unregister();", rescue);

        assertTrue("a failed attach must roll the registration back inside setupPapi", rollback > rescue);

        int bail = setup.indexOf("return;", rollback);

        assertTrue("a failed attach must leave setupPapi", bail > rollback);

        int retained = setup.indexOf("papiRegistration = registration;");

        assertTrue("setupPapi must retain the registration", retained > 0);
        assertTrue("the registration may only be retained after a successful attach", retained > bail);
    }

    @Test
    public void theExpansionIsNeverConstructedFromAPluginStatic() throws Exception {
        String setup = body(SETUP);

        assertFalse("the plugin class must not construct the expansion: that forces PlaceholderExpansion to load during enable and crashes a server without PlaceholderAPI",
                setup.contains("new IrisPapiExpansion("));
        assertTrue("the expansion takes its state as a constructor argument, built inside the installer",
                installerSource().contains("new IrisPapiExpansion(state, logger)"));
        assertFalse("the hand rolled PlaceholderAPI presence check must be gone",
                source().contains("isPluginEnabled(\"PlaceholderAPI\")"));
    }

    private static String installerSource() throws Exception {
        return java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/art/arcane/iris/core/link/IrisPapiInstaller.java")).replace("\r\n", "\n");
    }
}

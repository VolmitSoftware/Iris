package art.arcane.iris.modded;

import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Dist gate. The loader source sets fold modded-common, minecraft-common and client-common into one output, so
 * nothing at compile time stops a server-side class from touching a client-only type. On a dedicated server
 * that is a NoClassDefFoundError at the first call, usually deep inside worldgen.
 *
 * <p>The check is a constant-pool byte scan for the internal (slash) form of the forbidden packages. Slash form
 * is the point: a real type reference - supertype, field or local descriptor, method owner, class literal - is
 * always stored slash-separated, while a reflective lookup keeps the class name as a dotted string constant.
 * {@link ModdedMixinAudit} depends on that distinction: it names client mixin targets as dotted strings on
 * purpose so it can audit them from a dedicated server, and this gate must not flag it.
 *
 * <p>Same style as the core purity gate: read the bytes, do not load the class. Loading is what the gate is
 * trying to prove is safe.
 */
public class ModdedClientPackageIsolationTest {
    private static final String CLIENT_MINECRAFT = "net/minecraft/client/";
    private static final String CLIENT_NATIVE = "art/arcane/volmlib/nativelib/minecraft26_2/client/";
    private static final String CLIENT_IRIS = "art/arcane/iris/client/";
    private static final List<String> GUARDED_PACKAGES = List.of(
            "art/arcane/iris/modded",
            "art/arcane/iris/nativegen");
    private static final int MINIMUM_SCANNED = 50;

    @Test
    public void serverSidePackagesNeverReferenceClientOnlyTypes() throws IOException, URISyntaxException {
        Path root = classesRoot();
        List<String> violations = new ArrayList<>();
        int scanned = 0;
        for (String guarded : GUARDED_PACKAGES) {
            Path directory = root.resolve(guarded);
            if (!Files.isDirectory(directory)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(directory)) {
                for (Path classFile : walk.filter(ModdedClientPackageIsolationTest::isClassFile).toList()) {
                    scanned++;
                    String bytecode = readAsLatin1(classFile);
                    String name = root.relativize(classFile).toString();
                    if (bytecode.contains(CLIENT_MINECRAFT)) {
                        violations.add(name + " -> " + CLIENT_MINECRAFT);
                    }
                    if (bytecode.contains(CLIENT_NATIVE)) {
                        violations.add(name + " -> " + CLIENT_NATIVE);
                    }
                    if (bytecode.contains(CLIENT_IRIS)) {
                        violations.add(name + " -> " + CLIENT_IRIS);
                    }
                }
            }
        }
        assertTrue("guarded packages produced only " + scanned + " classes; the scan root is wrong",
                scanned >= MINIMUM_SCANNED);
        assertEquals("server-side classes referencing client-only types: " + violations,
                List.of(), violations);
    }

    /**
     * Positive control. If the needle ever stops matching - a package rename, a scan that reads the wrong
     * bytes - the gate above would pass silently forever. A known client-tainted class must still trip it.
     */
    @Test
    public void scanDetectsClientReferencesInAKnownClientClass() throws IOException, URISyntaxException {
        Path visionScreen = classesRoot().resolve("art/arcane/volmlib/nativelib/minecraft26_2/client/NativeClientScreen.class");
        assertTrue("NativeClientScreen.class missing from " + visionScreen, Files.isRegularFile(visionScreen));
        assertTrue("scan needle no longer matches a known client class",
                readAsLatin1(visionScreen).contains(CLIENT_MINECRAFT));
    }

    private static boolean isClassFile(Path path) {
        return Files.isRegularFile(path) && path.getFileName().toString().endsWith(".class");
    }

    private static String readAsLatin1(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1);
    }

    private static Path classesRoot() throws URISyntaxException {
        String anchor = "/art/arcane/iris/modded/ModdedMixinAudit.class";
        URL located = ModdedClientPackageIsolationTest.class.getResource(anchor);
        assertNotNull("compiled main classes are not on the test classpath as files", located);
        assertEquals("expected a directory classpath entry, got " + located, "file", located.getProtocol());
        Path root = Path.of(located.toURI());
        for (int depth = 0; depth < 5; depth++) {
            root = root.getParent();
            assertNotNull("walked past the classpath root resolving " + anchor, root);
        }
        return root;
    }
}

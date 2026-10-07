package art.arcane.iris.pack;

import art.arcane.iris.spi.IrisPlatform;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class PackFingerprintPlatformIsolationTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void validationContentFingerprintRunsWithoutBukkitClasses() throws Exception {
        File packs = temporary.newFolder("packs");
        File resource = new File(packs, "content.json");
        Files.writeString(resource.toPath(), "first");
        URL[] sources = {
                PackValidationCache.class.getProtectionDomain().getCodeSource().getLocation(),
                IrisPlatform.class.getProtectionDomain().getCodeSource().getLocation()
        };
        try (URLClassLoader loader = new WithoutBukkitLoader(sources)) {
            try {
                loader.loadClass("org.bukkit.command.CommandSender");
                fail("Bukkit must be unavailable to this runtime");
            } catch (ClassNotFoundException expected) {
                assertEquals("org.bukkit.command.CommandSender", expected.getMessage());
            }
            Class<?> cache = Class.forName("art.arcane.iris.pack.PackValidationCache", true, loader);
            assertSame(loader, cache.getClassLoader());
            String first = (String) cache.getMethod("contentFingerprint", File.class).invoke(null, packs);
            assertEquals("1915829f796381467340a94bd3581569cb1a8694d59f077bf14c849c39b20bb9", first);
            Files.writeString(resource.toPath(), "second");
            String second = (String) cache.getMethod("contentFingerprint", File.class).invoke(null, packs);
            assertNotEquals(first, second);
            assertEquals("9b21d3b13634fb96c41b442e2b886b15b62d10bbdea5258d76782eb28289d52d", second);
        }
    }

    @Test
    public void treeAndWorkspaceSnapshotRunWithoutBukkitAndKeepCanonicalHashes() throws Exception {
        File packs = temporary.newFolder("snapshot-packs");
        File pack = new File(packs, "overworld");
        Files.createDirectories(pack.toPath());
        Files.writeString(new File(pack, "content.json").toPath(), "first");
        URL[] sources = {PackFingerprints.class.getProtectionDomain().getCodeSource().getLocation()};
        try (URLClassLoader loader = new WithoutBukkitLoader(sources)) {
            Class<?> fingerprints = Class.forName("art.arcane.iris.pack.PackFingerprints", true, loader);
            assertSame(loader, fingerprints.getClassLoader());
            String tree = (String) fingerprints.getMethod("computePackTreeFingerprint", File.class).invoke(null, pack);
            assertEquals("1915829f796381467340a94bd3581569cb1a8694d59f077bf14c849c39b20bb9", tree);
            Object snapshot = fingerprints.getMethod("computePackContentSnapshot", File.class).invoke(null, packs);
            String content = (String) snapshot.getClass().getMethod("content").invoke(snapshot);
            assertEquals(PackFingerprints.computePackFingerprint(packs), content);
            assertEquals(PackFingerprints.computePackContentSnapshot(packs).packContents(),
                    snapshot.getClass().getMethod("packContents").invoke(snapshot));
            String metadata = (String) fingerprints.getMethod("computePackMetadataDigest", File.class).invoke(null, packs);
            assertEquals(PackFingerprints.computePackMetadataDigest(packs), metadata);
        }
    }

    private static final class WithoutBukkitLoader extends URLClassLoader {
        private WithoutBukkitLoader(URL[] sources) {
            super(sources, PackFingerprintPlatformIsolationTest.class.getClassLoader());
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("org.bukkit.")) {
                throw new ClassNotFoundException(name);
            }
            if (!name.startsWith("art.arcane.iris.")) {
                return super.loadClass(name, resolve);
            }
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                try {
                    loaded = findClass(name);
                } catch (ClassNotFoundException missing) {
                    return super.loadClass(name, resolve);
                }
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }
}

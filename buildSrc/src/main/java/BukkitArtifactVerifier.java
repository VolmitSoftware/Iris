import org.gradle.api.GradleException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Packaging gate for the CraftBukkit artifact. Every shadowJar exclude is silent at build time, so
 * this re-derives the class reference graph over the shipped jar and fails on anything the excludes
 * left dangling, plus asserts the entries that are only ever reached by name.
 */
public final class BukkitArtifactVerifier {
    // Packages the artifact is expected to ship in full. A CONSTANT_Class entry naming a missing
    // class under one of these is a NoClassDefFoundError waiting for the right code path.
    private static final List<String> SHIPPED_PREFIXES = List.of(
            "art/arcane/iris/",
            "art/arcane/volmlib/"
    );
    private static final String MATTER_SLICE_PACKAGE = "art/arcane/volmlib/util/matter/slices/";
    private static final String LANGUAGE_DIRECTORY = "languages/";

    private BukkitArtifactVerifier() {
    }

    /**
     * @param runtimeDownloadedPrefixes relocation targets for the libraries slimjar downloads and
     *                                  relocates at runtime, derived from the single relocation
     *                                  declaration in gradle/runtime-relocations.gradle. Compiled
     *                                  references to them are correct and the classes are correctly
     *                                  absent from the jar.
     */
    public static void verify(File artifact, List<String> requiredEntries,
                              int minimumMatterSlices, Collection<String> runtimeDownloadedPrefixes) {
        if (!artifact.isFile()) {
            throw new GradleException("Missing Bukkit Iris artifact: " + artifact.getAbsolutePath());
        }

        try (JarFile jar = new JarFile(artifact)) {
            for (String requiredEntry : requiredEntries) {
                if (jar.getJarEntry(requiredEntry) == null) {
                    throw new GradleException(artifact.getName() + " is missing " + requiredEntry
                            + ". If this run also requested a task whose name contains \"test\", :core:processResources"
                            + " was disabled for it and the artifact is incomplete by construction - build the jar in"
                            + " its own invocation.");
                }
            }

            Set<String> shippedClasses = new LinkedHashSet<>();
            int locales = 0;
            int matterSlices = 0;
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }

                String name = entry.getName();
                if (name.startsWith(LANGUAGE_DIRECTORY) && name.endsWith(".json")) {
                    locales++;
                }
                if (!name.endsWith(".class")) {
                    continue;
                }

                String internalName = name.substring(0, name.length() - ".class".length());
                shippedClasses.add(internalName);
                if (internalName.startsWith(MATTER_SLICE_PACKAGE)) {
                    matterSlices++;
                }
            }

            if (locales != 0) {
                throw new GradleException(artifact.getName() + " ships " + locales + " locale files; translations must download on demand");
            }
            // Matter.read() resolves slice types from the canonical name stored in the payload.
            if (matterSlices < minimumMatterSlices) {
                throw new GradleException(artifact.getName() + " ships " + matterSlices
                        + " Matter slice types; expected at least " + minimumMatterSlices
                        + ". Matter payloads name these types directly");
            }

            TreeMap<String, String> dangling = new TreeMap<>();
            entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }

                for (String reference : ClassReferences.read(readEntryBytes(jar, entry))) {
                    if (shippedClasses.contains(reference)
                            || !startsWithAny(reference, SHIPPED_PREFIXES)
                            || startsWithAny(reference, runtimeDownloadedPrefixes)) {
                        continue;
                    }
                    dangling.putIfAbsent(reference, entry.getName());
                }
            }

            if (!dangling.isEmpty()) {
                StringBuilder message = new StringBuilder(artifact.getName())
                        .append(" references classes it does not ship. A shadowJar exclude removed a class that is")
                        .append(" still in use:");
                dangling.entrySet().stream().limit(12).forEach(missing -> message.append("\n  ")
                        .append(missing.getKey())
                        .append(" (referenced by ")
                        .append(missing.getValue())
                        .append(')'));
                if (dangling.size() > 12) {
                    message.append("\n  ... and ").append(dangling.size() - 12).append(" more");
                }
                throw new GradleException(message.toString());
            }
        } catch (IOException e) {
            throw new GradleException("Unable to verify Bukkit Iris artifact " + artifact.getAbsolutePath(), e);
        }
    }

    private static byte[] readEntryBytes(JarFile jar, JarEntry entry) throws IOException {
        try (InputStream input = jar.getInputStream(entry)) {
            return input.readAllBytes();
        }
    }

    private static boolean startsWithAny(String value, Collection<String> prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}

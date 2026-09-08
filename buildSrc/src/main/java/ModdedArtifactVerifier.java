import org.gradle.api.GradleException;
import org.objectweb.asm.ClassReader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ModdedArtifactVerifier {
    private static final String CODEC_CLASS = "art/arcane/volmlib/util/mantle/io/Lz4IOWorkerCodecSupport.class";
    private static final List<String> INTERNAL_LZ4_REFERENCES = List.of(
            "net/jpountz/lz4/LZ4BlockInputStream",
            "net/jpountz/lz4/LZ4BlockOutputStream"
    );
    private static final String INTERNAL_OSHI_REFERENCE = "oshi/SystemInfo";
    private static final List<String> BUNDLED_RUNTIME_PREFIXES = List.of(
            "net/jpountz/",
            "oshi/",
            "com/sun/jna/",
            "art/arcane/iris/shadow/jpountz/",
            "art/arcane/iris/shadow/oshi/",
            "art/arcane/iris/shadow/jna/"
    );
    private static final List<String> PRIVATE_REFERENCE_PREFIXES = List.of(
            "art/arcane/iris/shadow/jpountz",
            "art/arcane/iris/shadow/oshi",
            "art/arcane/iris/shadow/jna"
    );
    // ASM must always be relocated: the loaders put their own copy on the module layer and a second
    // org.objectweb.asm package on the same layer is a boot failure.
    private static final String ASM_PREFIX = "org/objectweb/asm/";
    // Only the Bukkit platform binding is allowed to sit on org.bukkit types, and the modded jars
    // exclude it outright. Kept as an exemption so the same check can run over the Bukkit artifact.
    private static final String BUKKIT_PLATFORM_PREFIX = "art/arcane/iris/platform/bukkit/";
    // VolmLib is a separate library with its own build; Iris' core purity ratchet does not govern it.
    private static final String SHARED_LIBRARY_PREFIX = "art/arcane/volmlib/";
    private static final String BUKKIT_TYPE_PREFIX = "org/bukkit/";
    private static final String FABRIC_METADATA = "fabric.mod.json";
    private static final String NEOFORGE_METADATA = "META-INF/neoforge.mods.toml";
    private static final String NESTED_JAR_DIRECTORY = "META-INF/jars/";
    private static final String MIXIN_CONFIGS_ATTRIBUTE = "MixinConfigs";
    // Forge 26.2 ships upstream Mixin 0.8.7, whose MixinEnvironment.CompatibilityLevel enum ends at
    // JAVA_21. Anything higher is a hard MixinInitialisationError at Forge boot even though Fabric's
    // fork accepts it, so the shared configs are capped here.
    private static final int MAX_MIXIN_COMPATIBILITY_LEVEL = 21;
    private static final Pattern TOML_CONFIG_ASSIGNMENT = Pattern.compile("(?m)^\\s*config\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern COMPATIBILITY_LEVEL = Pattern.compile("\"compatibilityLevel\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern COMPATIBILITY_LEVEL_VALUE = Pattern.compile("JAVA_(\\d+)");
    private static final Pattern QUOTED_JSON_FILE = Pattern.compile("\"([^\"]+\\.(?:json|jar))\"");

    private ModdedArtifactVerifier() {
    }

    public static void verify(File artifact, List<String> requiredEntries) {
        verify(artifact, requiredEntries, Set.of());
    }

    /**
     * @param bukkitCoupledCoreClasses top-level internal class names (no {@code .class} suffix, no
     *                                 nested-class part) whose source file is listed in
     *                                 core/purity-allowlist.txt. A class with an {@code org.bukkit}
     *                                 supertype is accepted only when it is one of those, sits under
     *                                 the Bukkit platform package, or comes from VolmLib. Anything
     *                                 else is new Bukkit coupling in core and fails the build.
     */
    public static void verify(File artifact, List<String> requiredEntries, Set<String> bukkitCoupledCoreClasses) {
        if (!artifact.isFile()) {
            throw new GradleException("Missing modded Iris artifact: " + artifact.getAbsolutePath());
        }

        try (JarFile jar = new JarFile(artifact)) {
            for (String requiredEntry : requiredEntries) {
                if (jar.getJarEntry(requiredEntry) == null) {
                    throw new GradleException(artifact.getName() + " is missing " + requiredEntry);
                }
            }

            Set<String> bundledRuntimeEntries = new LinkedHashSet<>();
            Set<String> privateReferenceClasses = new LinkedHashSet<>();
            Set<String> codecReferences = new LinkedHashSet<>();
            Set<String> asmEntries = new LinkedHashSet<>();
            Set<String> shippedNestedJars = new LinkedHashSet<>();
            Set<String> bukkitSupertypeClasses = new LinkedHashSet<>();
            boolean oshiReference = false;
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }

                String name = entry.getName();
                if (startsWithAny(name, BUNDLED_RUNTIME_PREFIXES)) {
                    bundledRuntimeEntries.add(name);
                }
                if (name.startsWith(ASM_PREFIX)) {
                    asmEntries.add(name);
                }
                if (name.startsWith(NESTED_JAR_DIRECTORY) && name.endsWith(".jar")) {
                    shippedNestedJars.add(name);
                }
                if (isNestedJar(name)) {
                    inspectNestedJar(jar, entry, bundledRuntimeEntries, privateReferenceClasses);
                }
                if (!name.endsWith(".class")) {
                    continue;
                }

                byte[] classFile = readEntryBytes(jar, entry);
                String bytecode = new String(classFile, StandardCharsets.ISO_8859_1);
                if (containsAny(bytecode, PRIVATE_REFERENCE_PREFIXES)) {
                    privateReferenceClasses.add(name);
                }
                if (bytecode.contains(INTERNAL_OSHI_REFERENCE)) {
                    oshiReference = true;
                }
                if (name.equals(CODEC_CLASS)) {
                    for (String reference : INTERNAL_LZ4_REFERENCES) {
                        if (bytecode.contains(reference)) {
                            codecReferences.add(reference);
                        }
                    }
                }
                if (!name.startsWith(BUKKIT_PLATFORM_PREFIX)
                        && !name.startsWith(SHARED_LIBRARY_PREFIX)
                        && !bukkitCoupledCoreClasses.contains(topLevelClass(name))
                        && hasBukkitSupertype(classFile)) {
                    bukkitSupertypeClasses.add(name);
                }
            }

            if (!bundledRuntimeEntries.isEmpty()) {
                throw new GradleException(artifact.getName() + " duplicates Minecraft runtime libraries: "
                        + preview(bundledRuntimeEntries));
            }
            if (!privateReferenceClasses.isEmpty()) {
                throw new GradleException(artifact.getName() + " contains unresolved Iris-private runtime references in: "
                        + preview(privateReferenceClasses));
            }
            if (!codecReferences.containsAll(INTERNAL_LZ4_REFERENCES)) {
                throw new GradleException(artifact.getName() + " does not link region storage to Minecraft's LZ4 runtime");
            }
            if (!oshiReference) {
                throw new GradleException(artifact.getName() + " does not link hardware diagnostics to Minecraft's OSHI runtime");
            }
            if (!asmEntries.isEmpty()) {
                throw new GradleException(artifact.getName() + " ships unrelocated ASM: " + preview(asmEntries));
            }
            if (!bukkitSupertypeClasses.isEmpty()) {
                throw new GradleException(artifact.getName() + " ships classes with an org.bukkit supertype outside "
                        + BUKKIT_PLATFORM_PREFIX + " whose source is not declared Bukkit-coupled in"
                        + " core/purity-allowlist.txt: " + preview(bukkitSupertypeClasses));
            }

            verifyNestedJarDeclarations(artifact, jar, shippedNestedJars);
            verifyMixinCompatibilityLevels(artifact, jar);
        } catch (IOException e) {
            throw new GradleException("Unable to verify modded Iris artifact " + artifact.getAbsolutePath(), e);
        }
    }

    private static void verifyNestedJarDeclarations(File artifact, JarFile jar, Set<String> shippedNestedJars)
            throws IOException {
        String metadata = readEntryText(jar, FABRIC_METADATA);
        if (metadata == null) {
            if (!shippedNestedJars.isEmpty()) {
                throw new GradleException(artifact.getName() + " ships nested jars but has no " + FABRIC_METADATA
                        + " to declare them: " + preview(shippedNestedJars));
            }
            return;
        }

        Set<String> declared = collectQuotedFiles(extractJsonArray(metadata, "jars"), ".jar");
        Set<String> undeclared = new LinkedHashSet<>(shippedNestedJars);
        undeclared.removeAll(declared);
        if (!undeclared.isEmpty()) {
            throw new GradleException(artifact.getName() + " ships nested jars that " + FABRIC_METADATA
                    + " does not declare: " + preview(undeclared));
        }

        Set<String> missing = new LinkedHashSet<>(declared);
        missing.removeAll(shippedNestedJars);
        if (!missing.isEmpty()) {
            throw new GradleException(artifact.getName() + " declares nested jars in " + FABRIC_METADATA
                    + " that it does not ship: " + preview(missing));
        }
    }

    private static void verifyMixinCompatibilityLevels(File artifact, JarFile jar) throws IOException {
        Set<String> configs = new LinkedHashSet<>();
        Manifest manifest = jar.getManifest();
        if (manifest != null) {
            String declared = manifest.getMainAttributes().getValue(MIXIN_CONFIGS_ATTRIBUTE);
            if (declared != null) {
                for (String config : declared.split(",")) {
                    String trimmed = config.trim();
                    if (!trimmed.isEmpty()) {
                        configs.add(trimmed);
                    }
                }
            }
        }

        String neoforgeMetadata = readEntryText(jar, NEOFORGE_METADATA);
        if (neoforgeMetadata != null) {
            Matcher matcher = TOML_CONFIG_ASSIGNMENT.matcher(neoforgeMetadata);
            while (matcher.find()) {
                configs.add(matcher.group(1));
            }
        }

        String fabricMetadata = readEntryText(jar, FABRIC_METADATA);
        if (fabricMetadata != null) {
            configs.addAll(collectQuotedFiles(extractJsonArray(fabricMetadata, "mixins"), ".json"));
        }

        if (configs.isEmpty()) {
            throw new GradleException(artifact.getName() + " registers no mixin configs; expected the "
                    + MIXIN_CONFIGS_ATTRIBUTE + " manifest attribute (Forge), [[mixins]] (NeoForge), or a mixins "
                    + "array (Fabric)");
        }

        for (String config : configs) {
            String body = readEntryText(jar, config);
            if (body == null) {
                throw new GradleException(artifact.getName() + " registers mixin config " + config
                        + " which is not in the jar");
            }

            Matcher matcher = COMPATIBILITY_LEVEL.matcher(body);
            if (!matcher.find()) {
                continue;
            }

            String level = matcher.group(1);
            Matcher value = COMPATIBILITY_LEVEL_VALUE.matcher(level);
            if (!value.matches()) {
                throw new GradleException(artifact.getName() + " mixin config " + config
                        + " has unparseable compatibilityLevel " + level);
            }
            if (Integer.parseInt(value.group(1)) > MAX_MIXIN_COMPATIBILITY_LEVEL) {
                throw new GradleException(artifact.getName() + " mixin config " + config
                        + " requests compatibilityLevel " + level + "; upstream Mixin 0.8.7 (Forge) tops out at JAVA_"
                        + MAX_MIXIN_COMPATIBILITY_LEVEL);
            }
        }
    }

    private static String topLevelClass(String entryName) {
        String internalName = entryName.substring(0, entryName.length() - ".class".length());
        int nested = internalName.indexOf('$');
        return nested < 0 ? internalName : internalName.substring(0, nested);
    }

    private static boolean hasBukkitSupertype(byte[] classFile) {
        ClassReader reader;
        try {
            reader = new ClassReader(classFile);
        } catch (RuntimeException e) {
            // Class file version newer than the bundled ASM. Nothing to assert about it.
            return false;
        }

        String superName = reader.getSuperName();
        if (superName != null && superName.startsWith(BUKKIT_TYPE_PREFIX)) {
            return true;
        }
        for (String candidate : reader.getInterfaces()) {
            if (candidate.startsWith(BUKKIT_TYPE_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the balanced {@code [...]} block that follows {@code "key"}, or an empty string.
     */
    private static String extractJsonArray(String json, String key) {
        int keyIndex = json.indexOf('"' + key + '"');
        if (keyIndex < 0) {
            return "";
        }

        int open = json.indexOf('[', keyIndex);
        if (open < 0) {
            return "";
        }

        int depth = 0;
        for (int i = open; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    return json.substring(open, i + 1);
                }
            }
        }
        return "";
    }

    private static Set<String> collectQuotedFiles(String region, String suffix) {
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = QUOTED_JSON_FILE.matcher(region);
        while (matcher.find()) {
            String value = matcher.group(1);
            if (value.endsWith(suffix)) {
                found.add(value);
            }
        }
        return found;
    }

    private static String readEntryText(JarFile jar, String name) throws IOException {
        JarEntry entry = jar.getJarEntry(name);
        if (entry == null) {
            return null;
        }
        return new String(readEntryBytes(jar, entry), StandardCharsets.UTF_8);
    }

    private static byte[] readEntryBytes(JarFile jar, JarEntry entry) throws IOException {
        try (InputStream input = jar.getInputStream(entry)) {
            return input.readAllBytes();
        }
    }

    private static boolean startsWithAny(String value, List<String> prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String value, List<String> fragments) {
        for (String fragment : fragments) {
            if (value.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static void inspectNestedJar(JarFile jar, JarEntry nestedEntry,
                                         Set<String> bundledRuntimeEntries,
                                         Set<String> privateReferenceClasses) throws IOException {
        String nestedName = nestedEntry.getName();
        if (isNamedRuntimeLibrary(nestedName)) {
            bundledRuntimeEntries.add(nestedName);
        }

        try (InputStream input = jar.getInputStream(nestedEntry);
             JarInputStream nestedJar = new JarInputStream(input)) {
            JarEntry entry;
            while ((entry = nestedJar.getNextJarEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String name = entry.getName();
                String path = nestedName + "!/" + name;
                if (startsWithAny(name, BUNDLED_RUNTIME_PREFIXES)) {
                    bundledRuntimeEntries.add(path);
                }
                if (name.endsWith(".class")) {
                    String bytecode = new String(nestedJar.readAllBytes(), StandardCharsets.ISO_8859_1);
                    if (containsAny(bytecode, PRIVATE_REFERENCE_PREFIXES)) {
                        privateReferenceClasses.add(path);
                    }
                }
            }
        }
    }

    private static boolean isNestedJar(String name) {
        return name.endsWith(".jar")
                && (name.startsWith(NESTED_JAR_DIRECTORY) || name.startsWith("META-INF/jarjar/"));
    }

    private static boolean isNamedRuntimeLibrary(String name) {
        if (!isNestedJar(name)) {
            return false;
        }

        String fileName = name.substring(name.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        return fileName.startsWith("lz4") || fileName.startsWith("oshi") || fileName.startsWith("jna");
    }

    private static String preview(Set<String> values) {
        return String.join(", ", values.stream().limit(8).toList());
    }
}

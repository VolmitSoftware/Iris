package com.volmit.host;

import com.volmit.FeatureAttachment;
import com.volmit.IrisModule;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** The host's side of every pack load, the same for all packs: its built-in modules, the host
 * packages pack modules may use besides the Iris API, the JSON fields the host itself uses (which no
 * built-in alias may take), and the namespaces only built-in modules may use. Build it once and pass it to every
 * {@link LoadedPack#load(java.nio.file.Path, ModuleHost)}.
 *
 * <pre>{@code
 * var host = ModuleHost.of(builtInModules)
 *         .exposing("art.arcane.iris.engine.modules")
 *         .reservingNamespaces("iris")
 *         .reservingFields(reservedHostFields);
 * }</pre>
 *
 * Every method adds to what earlier calls configured, so a host can build the configuration in steps.
 * Built-in modules' {@code info()} is read here, once. Exposed package names, reserved namespaces and
 * reserved field names are checked here, so a mistake fails when the host starts rather than at the
 * first pack load.
 */
public record ModuleHost(
        List<DiscoveredModule> builtIns,
        Set<String> exposedHostPackages,
        Map<FeatureAttachment.Target, Set<String>> reservedHostFields,
        Set<String> reservedNamespaces,
        Path jarCopyRoot,
        System.Logger logger) {
    private static final String TEMPORARY_DIRECTORY_PROPERTY = "java.io.tmpdir";

    public ModuleHost {
        builtIns = List.copyOf(builtIns);
        exposedHostPackages = Set.copyOf(exposedHostPackages);
        reservedHostFields = copyOf(reservedHostFields);
        reservedNamespaces = Set.copyOf(reservedNamespaces);
        Objects.requireNonNull(jarCopyRoot, "jarCopyRoot");
        Objects.requireNonNull(logger, "logger");
        ExposedPackages.apiAnd(exposedHostPackages);
        ReservedNamespaces.of(reservedNamespaces);
        requireFieldNames(reservedHostFields);
    }

    /** The given built-in modules, exposing only the Iris API and reserving nothing. Jar copies go to
     * {@code java.io.tmpdir} and the load log to the {@link System.Logger} named after {@link PackModules}.
     */
    public static ModuleHost of(List<? extends IrisModule> builtInModules) {
        var builtIns = builtInModules.stream().map(DiscoveredModule::builtIn).toList();
        return new ModuleHost(builtIns, Set.of(), Map.of(), Set.of(), systemTemporaryDirectory(),
                System.getLogger(PackModules.class.getName()));
    }

    /** This configuration with {@code packages}, each with its subpackages, also exposed to pack
     * modules, such as the packages that hold built-in modules' stage constants.
     */
    public ModuleHost exposing(String... packages) {
        return new ModuleHost(builtIns, union(exposedHostPackages, packages), reservedHostFields, reservedNamespaces,
                jarCopyRoot, logger);
    }

    /** This configuration with {@code namespaces} reserved for built-in modules. */
    public ModuleHost reservingNamespaces(String... namespaces) {
        return new ModuleHost(builtIns, exposedHostPackages, reservedHostFields, union(reservedNamespaces, namespaces),
                jarCopyRoot, logger);
    }

    /** This configuration with {@code fields} also reserved as JSON fields the host itself uses,
     * merged per attachment target with the fields reserved before.
     */
    public ModuleHost reservingFields(Map<FeatureAttachment.Target, Set<String>> fields) {
        return new ModuleHost(builtIns, exposedHostPackages, merge(reservedHostFields, fields), reservedNamespaces,
                jarCopyRoot, logger);
    }

    /** This configuration copying module jars into new directories below {@code directory}, such as a
     * cache directory of the host, which is created when missing. The last call wins.
     */
    public ModuleHost copyingJarsTo(Path directory) {
        return new ModuleHost(builtIns, exposedHostPackages, reservedHostFields, reservedNamespaces, directory, logger);
    }

    /** This configuration logging each pack load's module summary and missing-classes warning to
     * {@code logger}, such as an adapter to the host's own log. The last call wins.
     */
    public ModuleHost loggingTo(System.Logger logger) {
        return new ModuleHost(builtIns, exposedHostPackages, reservedHostFields, reservedNamespaces, jarCopyRoot, logger);
    }

    /** The built-in modules followed by a pack's modules, in the order {@link LoadedModules} installs them. */
    List<DiscoveredModule> withPackModules(List<DiscoveredModule> packModules) {
        var modules = new ArrayList<>(builtIns);
        modules.addAll(Objects.requireNonNull(packModules, "packModules"));
        return modules;
    }

    private static Path systemTemporaryDirectory() {
        return Path.of(System.getProperty(TEMPORARY_DIRECTORY_PROPERTY));
    }

    private static Map<FeatureAttachment.Target, Set<String>> copyOf(Map<FeatureAttachment.Target, Set<String>> fields) {
        var copy = new EnumMap<FeatureAttachment.Target, Set<String>>(FeatureAttachment.Target.class);
        fields.forEach((target, names) -> copy.put(target, Set.copyOf(names)));
        return Map.copyOf(copy);
    }

    /** A blank field name, or one with ':' like a feature id, fails here, as it would as an alias. */
    private static void requireFieldNames(Map<FeatureAttachment.Target, Set<String>> fields) {
        fields.values().forEach(names -> names.forEach(FeatureAttachment::requireFieldName));
    }

    private static Map<FeatureAttachment.Target, Set<String>> merge(
            Map<FeatureAttachment.Target, Set<String>> existing,
            Map<FeatureAttachment.Target, Set<String>> added) {
        var merged = new EnumMap<FeatureAttachment.Target, Set<String>>(FeatureAttachment.Target.class);
        existing.forEach((target, names) -> merged.put(target, new TreeSet<>(names)));
        added.forEach((target, names) -> merged.computeIfAbsent(target, ignored -> new TreeSet<>()).addAll(names));
        return merged;
    }

    private static Set<String> union(Set<String> existing, String... added) {
        var names = new TreeSet<>(existing);
        names.addAll(Arrays.asList(added));
        return names;
    }
}

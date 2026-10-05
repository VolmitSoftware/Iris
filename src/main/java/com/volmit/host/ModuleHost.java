package com.volmit.host;

import com.volmit.FeatureAttachment;
import com.volmit.IrisModule;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** The host's side of every pack load, the same for all packs: its built-in modules, the host
 * packages pack modules may use besides the Iris API, the JSON fields the host itself uses, and the
 * namespaces only built-in modules may use. Build it once and pass it to every
 * {@link LoadedPack#load(java.nio.file.Path, ModuleHost)}.
 *
 * <pre>{@code
 * var host = ModuleHost.of(builtInModules)
 *         .exposing("art.arcane.iris.engine.modules")
 *         .reservingNamespaces("iris")
 *         .reservingFields(reservedHostFields);
 * }</pre>
 *
 * Built-in modules' {@code info()} is read here, once. Exposed package names and reserved namespaces
 * are checked here, so a mistake fails when the host starts rather than at the first pack load.
 */
public record ModuleHost(
        List<DiscoveredModule> builtIns,
        Set<String> exposedHostPackages,
        Map<FeatureAttachment.Target, Set<String>> reservedHostFields,
        Set<String> reservedNamespaces) {
    public ModuleHost {
        builtIns = List.copyOf(builtIns);
        exposedHostPackages = Set.copyOf(exposedHostPackages);
        reservedHostFields = copyOf(reservedHostFields);
        reservedNamespaces = Set.copyOf(reservedNamespaces);
        ExposedPackages.apiAnd(exposedHostPackages);
        ReservedNamespaces.of(reservedNamespaces);
    }

    /** The given built-in modules, exposing only the Iris API and reserving nothing. */
    public static ModuleHost of(List<? extends IrisModule> builtInModules) {
        var builtIns = builtInModules.stream().map(DiscoveredModule::builtIn).toList();
        return new ModuleHost(builtIns, Set.of(), Map.of(), Set.of());
    }

    /** This configuration with {@code packages}, each with its subpackages, also exposed to pack
     * modules, such as the packages that hold built-in modules' stage constants.
     */
    public ModuleHost exposing(String... packages) {
        return new ModuleHost(builtIns, union(exposedHostPackages, packages), reservedHostFields, reservedNamespaces);
    }

    /** This configuration with {@code namespaces} reserved for built-in modules. */
    public ModuleHost reservingNamespaces(String... namespaces) {
        return new ModuleHost(builtIns, exposedHostPackages, reservedHostFields, union(reservedNamespaces, namespaces));
    }

    /** This configuration with {@code fields} as the JSON fields the host itself uses, per target. */
    public ModuleHost reservingFields(Map<FeatureAttachment.Target, Set<String>> fields) {
        return new ModuleHost(builtIns, exposedHostPackages, fields, reservedNamespaces);
    }

    /** The built-in modules followed by a pack's modules, in the order {@link LoadedModules} installs them. */
    List<DiscoveredModule> withPackModules(List<DiscoveredModule> packModules) {
        var modules = new ArrayList<>(builtIns);
        modules.addAll(Objects.requireNonNull(packModules, "packModules"));
        return modules;
    }

    private static Map<FeatureAttachment.Target, Set<String>> copyOf(Map<FeatureAttachment.Target, Set<String>> fields) {
        var copy = new EnumMap<FeatureAttachment.Target, Set<String>>(FeatureAttachment.Target.class);
        fields.forEach((target, names) -> copy.put(target, Set.copyOf(names)));
        return Map.copyOf(copy);
    }

    private static Set<String> union(Set<String> existing, String... added) {
        var names = new TreeSet<>(existing);
        names.addAll(Arrays.asList(added));
        return names;
    }
}

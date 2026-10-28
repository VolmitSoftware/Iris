package com.volmit.host;

import com.volmit.host.schema.FeatureRole;
import com.volmit.host.schema.FeatureSchema;
import com.volmit.host.schema.ObjectType;
import com.volmit.host.schema.PackSchema;
import com.volmit.host.schema.ResourceKindSchema;
import com.volmit.host.schema.SchemaOption;
import com.volmit.host.schema.ValueKind;
import com.volmit.resource.ResourceId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Describes a loaded pack's catalogued settings and resource kinds for studio. Fails the load, naming the class,
 * field and module, on what the host could not render: an unsupported field type, a misplaced {@code @Ref} or one
 * naming a kind nobody has, a class without a usable no-argument constructor, a snippet name used twice, a pack
 * class's snippet name outside its module's path, or a pack module's resource kind read into a host type.
 */
@SuppressWarnings("removal") // host kinds are transitional, until the cut-over
final class PackSchemaBuilder {
    private static final Pattern SNIPPET_NAME = Pattern.compile("[a-z0-9_.-]+(/[a-z0-9_.-]+)*");
    private static final Pattern RELATIVE_SEGMENT = Pattern.compile("(^|/)\\.\\.?(/|$)");
    private static final char FOLDER_SEPARATOR = '/';

    private final List<DiscoveredModule> modules;
    private final TypeDrafts drafts;

    private final HostTypes hostTypes;

    private PackSchemaBuilder(FeatureCatalog catalog, List<DiscoveredModule> modules, HostTypes hostTypes) {
        this.modules = modules;
        this.hostTypes = hostTypes;
        this.drafts = new TypeDrafts(hostTypes, type -> resourceKindOf(catalog, type));
    }

    static PackSchema describe(FeatureCatalog catalog, List<DiscoveredModule> modules, HostTypes hostTypes) {
        return new PackSchemaBuilder(catalog, modules, hostTypes).describe(catalog);
    }

    private PackSchema describe(FeatureCatalog catalog) {
        var features = catalogued(catalog).map(this::draft).toList();
        var resources = catalog.resourceKinds().stream().map(this::describe).toList();
        requireUniqueSnippets();
        var types = completeTypes();
        return new PackSchema(features.stream().map(feature -> feature.complete(types)).toList(), resources,
                List.copyOf(types.values()), catalog.placementType().map(placement -> placement.type().getName()));
    }

    private static Optional<ResourceId> resourceKindOf(FeatureCatalog catalog, Class<?> type) {
        return catalog.resourceKinds().stream()
                .map(ResourceKindRegistration::kind)
                .filter(kind -> kind.type() == type)
                .map(com.volmit.resource.ResourceKind::id)
                .findFirst();
    }

    /** A built-in kind may read into a class the host describes itself, as built-in settings may hold one. */
    private ResourceKindSchema describe(ResourceKindRegistration registration) {
        var kind = registration.kind();
        var module = registration.module();
        return new ResourceKindSchema(kind.id(), module.info(), kind.folder(), fileType(registration));
    }

    private ValueKind fileType(ResourceKindRegistration registration) {
        var type = registration.kind().type();
        if (!hostTypes.contains(type)) return drafts.resourceKind(SchemaSource.resourceKind(registration.kind(), registration.module()));
        if (registration.module().source() == DiscoveredModule.Source.HOST) return new ValueKind.HostKind(type.getName());
        throw new IllegalArgumentException("Resource kind " + registration.kind().id() + " of module " + registration.module()
                + " reads into " + type.getName() + ", a class the host describes itself, which only modules that ship with"
                + " the host may use; read the files into a class of the module");
    }

    /** One catalogued settings type and why it is in the catalog. */
    private record Catalogued(FeatureDescriptor<?> descriptor, FeatureRole role) {}

    /** A feature whose settings class is drafted but whose defaults are not converted yet. */
    private record FeatureDraft(Catalogued catalogued, DiscoveredModule owner) {
        FeatureSchema complete(Map<String, ObjectType> types) {
            var descriptor = catalogued.descriptor();
            return new FeatureSchema(descriptor.id(), descriptor.name(), catalogued.role(), owner.info(), descriptor.key(), descriptor.root(),
                    descriptor.targets(),types.get(descriptor.type().getName()));
        }
    }

    /** Ordered by feature id, so which settings an error names first never depends on discovery. */
    private static Stream<Catalogued> catalogued(FeatureCatalog catalog) {
        var settings = catalog.settings().stream().map(descriptor -> new Catalogued(descriptor, FeatureRole.SETTINGS));
        var objects = catalog.objects().stream()
                .map(registration -> new Catalogued(registration.descriptor(), FeatureRole.OBJECT_GENERATOR));
        return Stream.concat(settings, objects).sorted(Comparator.comparing(catalogued -> catalogued.descriptor().id()));
    }

    private FeatureDraft draft(Catalogued catalogued) {
        var owner = ownerOf(catalogued.descriptor());
        drafts.settings(SchemaSource.settings(catalogued.descriptor(), owner));
        return new FeatureDraft(catalogued, owner);
    }

    /** Registration checked that a module owns every catalogued id; module ids are unique and have no '/', so only one can. */
    private DiscoveredModule ownerOf(FeatureDescriptor<?> descriptor) {
        return modules.stream()
                .filter(module -> module.info().ownsId(descriptor.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No loaded module owns feature id " + descriptor.id()));
    }

    private void requireUniqueSnippets() {
        var owners = new HashMap<String, TypeDrafts.TypeDraft>();
        for (var draft : sortedDrafts()) {
            var snippet = draft.metadata().snippet();
            if (snippet.isEmpty()) continue;
            requireSnippetName(snippet, draft);
            requirePackSnippetPath(snippet, draft);
            var previous = owners.putIfAbsent(snippet, draft);
            if (previous != null) throw snippetCollision(snippet, previous, draft);
        }
    }

    private static void requireSnippetName(String snippet, TypeDrafts.TypeDraft draft) {
        if (SNIPPET_NAME.matcher(snippet).matches() && !RELATIVE_SEGMENT.matcher(snippet).find()) return;
        throw new IllegalArgumentException("Snippet name '" + snippet + "' of " + draft.type().getName() + " of "
                + draft.source() + " is not a folder path; packs write snippets under snippet/<name>/, so use lowercase"
                + " letters, digits, '-', '_' and '.', with '/' between folders");
    }

    /** A pack class keeps its snippets under the folder of a module of its pack,
     * {@code snippet/<namespace>/<path>/}, so they can never collide with Iris's own or another module's.
     */
    private void requirePackSnippetPath(String snippet, TypeDrafts.TypeDraft draft) {
        if (!isPackClass(draft.type())) return;
        if (packModules().anyMatch(module -> isUnderModuleFolder(snippet, module))) return;
        var example = moduleFolder(draft.source().module()) + FOLDER_SEPARATOR + lastSegment(snippet);
        throw new IllegalArgumentException("Snippet name '" + snippet + "' of " + draft.type().getName() + " of "
                + draft.source() + " is not under a module of the pack; snippets of pack modules start with their"
                + " module's id as a folder path, such as '" + example + "' (folder snippet/" + example + "/)");
    }

    private boolean isPackClass(Class<?> type) {
        return packModules().anyMatch(module -> module.module().getClass().getClassLoader() == type.getClassLoader());
    }

    private Stream<DiscoveredModule> packModules() {
        return modules.stream().filter(module -> module.source() == DiscoveredModule.Source.PACK);
    }

    private static boolean isUnderModuleFolder(String snippet, DiscoveredModule module) {
        var folder = moduleFolder(module);
        return snippet.equals(folder) || snippet.startsWith(folder + FOLDER_SEPARATOR);
    }

    private static String moduleFolder(DiscoveredModule module) {
        var id = module.info().id();
        return id.namespace() + FOLDER_SEPARATOR + id.path();
    }

    private static String lastSegment(String snippet) {
        return snippet.substring(snippet.lastIndexOf(FOLDER_SEPARATOR) + 1);
    }

    private static IllegalArgumentException snippetCollision(String snippet, TypeDrafts.TypeDraft first, TypeDrafts.TypeDraft second) {
        return new IllegalArgumentException("Snippet name '" + snippet + "' is used by " + first.type().getName() + " of "
                + first.source() + " and by " + second.type().getName() + " of " + second.source()
                + "; studio describes snippet/" + snippet + "/ by one class, so give each its own name");
    }

    private List<TypeDrafts.TypeDraft> sortedDrafts() {
        return drafts.drafts().stream().sorted(Comparator.comparing(draft -> draft.type().getName())).toList();
    }

    private Map<String, ObjectType> completeTypes() {
        var byName = new HashMap<String, TypeDrafts.TypeDraft>();
        drafts.drafts().forEach(draft -> byName.put(draft.type().getName(), draft));
        var defaults = new DefaultValues(byName);
        var types = new TreeMap<String, ObjectType>();
        byName.forEach((name, draft) -> types.put(name, objectType(draft, defaults)));
        return types;
    }

    private static ObjectType objectType(TypeDrafts.TypeDraft draft, DefaultValues defaults) {
        var metadata = draft.metadata();
        var options = draft.options().stream().map(option -> schemaOption(option, defaults)).toList();
        var snippet = metadata.snippet().isEmpty() ? Optional.<String>empty() : Optional.of(metadata.snippet());
        return new ObjectType(draft.type().getName(), metadata.description(), snippet, options);
    }

    private static SchemaOption schemaOption(TypeDrafts.OptionDraft draft, DefaultValues defaults) {
        var option = draft.option();
        return new SchemaOption(option.name(), draft.kind(), option.description(), option.required(),
                bound(option.minimum()), bound(option.maximum()), minItems(option.minItems()), defaults.of(draft.defaultValue(), draft.kind()));
    }

    private static OptionalInt minItems(Integer value) {
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }

    private static OptionalDouble bound(Double value) {
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
    }
}

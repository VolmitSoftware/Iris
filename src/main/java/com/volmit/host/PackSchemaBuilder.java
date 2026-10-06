package com.volmit.host;

import com.volmit.host.schema.FeatureRole;
import com.volmit.host.schema.FeatureSchema;
import com.volmit.host.schema.ObjectType;
import com.volmit.host.schema.PackSchema;
import com.volmit.host.schema.SchemaOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Describes a loaded pack's catalogued settings for studio. Fails the load, naming the class, field
 * and module, on settings the host could not render: an unsupported field type, a misplaced
 * {@code @Ref}, a class without a usable no-argument constructor or a snippet name used twice.
 */
final class PackSchemaBuilder {
    private static final Pattern SNIPPET_NAME = Pattern.compile("[a-z0-9_-]+");

    private final List<DiscoveredModule> modules;
    private final TypeDrafts drafts = new TypeDrafts();

    private PackSchemaBuilder(List<DiscoveredModule> modules) {
        this.modules = modules;
    }

    static PackSchema describe(FeatureCatalog catalog, List<DiscoveredModule> modules) {
        return new PackSchemaBuilder(modules).describe(catalog);
    }

    private PackSchema describe(FeatureCatalog catalog) {
        var features = catalogued(catalog).map(this::draft).toList();
        requireUniqueSnippets();
        var types = completeTypes();
        return new PackSchema(features.stream().map(feature -> feature.complete(types)).toList(), List.copyOf(types.values()));
    }

    /** One catalogued settings type and why it is in the catalog. */
    private record Catalogued(FeatureDescriptor<?> descriptor, FeatureRole role) {}

    /** A feature whose settings class is drafted but whose defaults are not converted yet. */
    private record FeatureDraft(Catalogued catalogued, DiscoveredModule owner) {
        FeatureSchema complete(Map<String, ObjectType> types) {
            var descriptor = catalogued.descriptor();
            return new FeatureSchema(descriptor.id(), descriptor.name(), catalogued.role(), owner.info(),
                    descriptor.attachment().field(), descriptor.attachment().targets(), types.get(descriptor.type().getName()));
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
        drafts.settings(new SchemaSource(catalogued.descriptor(), owner));
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
            var previous = owners.putIfAbsent(snippet, draft);
            if (previous != null) throw snippetCollision(snippet, previous, draft);
        }
    }

    private static void requireSnippetName(String snippet, TypeDrafts.TypeDraft draft) {
        if (SNIPPET_NAME.matcher(snippet).matches()) return;
        throw new IllegalArgumentException("Snippet name '" + snippet + "' of " + draft.type().getName() + " of "
                + draft.source() + " is not a folder name; packs write snippets under snippet/<name>/, so use lowercase"
                + " letters, digits, '-' and '_'");
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
                bound(option.minimum()), bound(option.maximum()), defaults.of(draft.defaultValue(), draft.kind()));
    }

    private static OptionalDouble bound(Double value) {
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
    }
}

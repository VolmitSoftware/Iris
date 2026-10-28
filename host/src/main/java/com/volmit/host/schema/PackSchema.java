package com.volmit.host.schema;

import com.volmit.config.Level;
import com.volmit.resource.ResourceId;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/** What the modules of a loaded pack add to the pack's JSON, for the host to render as JSON Schema and
 * studio completion. It holds module-provided entries only: the host describes its own fields.
 *
 * <p>Everything is in a fixed order, so the same modules always give the same description: features
 * and resource kinds by id, the features of a target by JSON key, types by name, snippets by snippet name,
 * options and enum constants in declaration order.
 *
 * @param features every catalogued settings type of the pack's modules, built-in ones included
 * @param resources every resource kind of the pack's modules, built-in ones included
 * @param types every class reachable from those settings and resource kinds, each once
 * @param placementType the name of the class packs write their object placements as, the JSON holder of
 *     {@link Level#PLACEMENT}, if a module declared it ({@code FeatureRegistry.registerPlacementType})
 */
public record PackSchema(List<FeatureSchema> features, List<ResourceKindSchema> resources, List<ObjectType> types,
                         Optional<String> placementType) {
    public PackSchema {
        features = features.stream().sorted(Comparator.comparing(FeatureSchema::id)).toList();
        resources = resources.stream().sorted(Comparator.comparing(ResourceKindSchema::id)).toList();
        types = types.stream().sorted(Comparator.comparing(ObjectType::name)).toList();
        Objects.requireNonNull(placementType, "placementType");
    }

    /** The resource kind {@code id}, such as the one a {@link ValueKind.ResourceReferenceKind} names. */
    public ResourceKindSchema resource(ResourceId id) {
        Objects.requireNonNull(id, "id");
        return resources.stream()
                .filter(kind -> kind.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("No resource kind: " + id));
    }

    /** The module features written in the files of one level, by JSON key. */
    public List<FeatureSchema> fields(Level target) {
        Objects.requireNonNull(target, "target");
        return features.stream()
                .filter(feature -> feature.targets().contains(target))
                .sorted(Comparator.comparing(FeatureSchema::key))
                .toList();
    }

    /** The settings of object generators, by feature id. */
    public List<FeatureSchema> objectGenerators() {
        return features.stream().filter(feature -> feature.role() == FeatureRole.OBJECT_GENERATOR).toList();
    }

    /** The type an {@link ValueKind.ObjectKind} names. */
    public ObjectType type(String name) {
        return types.stream()
                .filter(type -> type.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("No settings type: " + name));
    }

    /** The types a pack may write as snippet files, by snippet name; each name belongs to one type. */
    public List<ObjectType> snippets() {
        return types.stream()
                .filter(type -> type.snippet().isPresent())
                .sorted(Comparator.comparing(type -> type.snippet().orElseThrow()))
                .toList();
    }
}

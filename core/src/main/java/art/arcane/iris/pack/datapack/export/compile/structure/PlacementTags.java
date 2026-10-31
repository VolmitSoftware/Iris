package art.arcane.iris.pack.datapack.export.compile.structure;

import art.arcane.iris.pack.datapack.export.compile.ExportedBiomes;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.ir.JsonResource;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

public final class PlacementTags {
    private static final String ROOT = "placement/";
    private static final String BIOME_TAGS = "/tags/worldgen/biome/";
    private static final String JSON_SUFFIX = ".json";
    private static final String DIMENSION_LABEL = "dimensions/";
    private static final String REGION_LABEL = "regions/";

    private final PackCompileContext context;
    private final PlacementScopes.Scope dimension;
    private final Map<String, PlacementScopes.Scope> regions;

    private PlacementTags(PackCompileContext context, PlacementScopes.Scope dimension, Map<String, PlacementScopes.Scope> regions) {
        this.context = context;
        this.dimension = dimension;
        this.regions = regions;
    }

    public static PlacementTags collect(PackCompileContext context) {
        List<PlacementScopes.Scope> scopes = PlacementScopes.collect(context, ExportedBiomes.load(context));
        PlacementScopes.Scope dimension = null;
        Map<String, PlacementScopes.Scope> regions = new HashMap<>();
        for (PlacementScopes.Scope scope : scopes) {
            if (scope.label().startsWith(DIMENSION_LABEL)) {
                dimension = scope;
            } else if (scope.label().startsWith(REGION_LABEL)) {
                regions.put(scope.label().substring(REGION_LABEL.length()), scope);
            }
        }
        if (dimension == null) {
            throw new IllegalStateException("Placement scopes have no dimension scope");
        }
        return new PlacementTags(context, dimension, regions);
    }

    public Optional<String> dimensionTag() {
        return ensure(dimension);
    }

    public Optional<String> regionTag(String regionKey) {
        PlacementScopes.Scope scope = regions.get(regionKey);
        return scope == null ? Optional.empty() : ensure(scope);
    }

    public SortedSet<String> sharedBiomes(String regionKey) {
        PlacementScopes.Scope scope = regions.get(regionKey);
        return scope == null ? new TreeSet<>() : new TreeSet<>(scope.shared());
    }

    static void write(PackCompileContext context, String tag, SortedSet<String> ids) {
        String path = "data/" + context.namespace() + BIOME_TAGS + tag + JSON_SUFFIX;
        for (JsonResource resource : context.jsonResources()) {
            if (resource.path().equals(path)) {
                return;
            }
        }
        JsonArray values = new JsonArray(ids.size());
        for (String id : ids) {
            values.add(id);
        }
        JsonObject json = new JsonObject();
        json.addProperty("replace", false);
        json.add("values", values);
        context.jsonResources().add(new JsonResource(path, json));
    }

    private Optional<String> ensure(PlacementScopes.Scope scope) {
        if (scope.biomeIds().isEmpty()) {
            return Optional.empty();
        }
        String tag = ROOT + scope.name();
        write(context, tag, scope.biomeIds());
        return Optional.of(context.namespace() + ":" + tag);
    }
}

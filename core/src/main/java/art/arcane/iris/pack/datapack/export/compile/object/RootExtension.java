package art.arcane.iris.pack.datapack.export.compile.object;

import art.arcane.iris.pack.datapack.export.TemplateBlock;
import art.arcane.iris.pack.datapack.export.TemplateVolume;
import art.arcane.iris.pack.datapack.export.VanillaBlockState;
import art.arcane.iris.pack.datapack.export.compile.WeightedState;
import art.arcane.iris.pack.datapack.export.compile.decoration.Placements;
import art.arcane.iris.pack.datapack.export.ir.JsonResource;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class RootExtension {
    private static final int PROBE_LIMIT = 16;
    private static final int QUARTER_DEGREES = 90;

    private final String namespace;
    private final FeatureJson json;

    RootExtension(String namespace, FeatureJson json) {
        this.namespace = namespace;
        this.json = json;
    }

    static JsonObject gap(TreeRoots roots, TemplateVolume volume, int quarter) {
        List<JsonObject> columns = new ArrayList<>(roots.cells().size());
        for (TemplateBlock cell : roots.cells()) {
            TemplateColumn column = TemplateColumn.of(volume, cell.x(), cell.z(), quarter);
            int top = Math.min(PROBE_LIMIT, cell.y() - 1);
            int bottom = Math.max(-PROBE_LIMIT, cell.y() - 1 - roots.reach());
            if (bottom > top || Math.abs(column.x()) > PROBE_LIMIT || Math.abs(column.z()) > PROBE_LIMIT) {
                continue;
            }
            JsonObject open = Placements.typed("minecraft:volume_match");
            open.add("min", FeatureJson.vector(column.x(), bottom, column.z()));
            open.add("max", FeatureJson.vector(column.x(), top, column.z()));
            open.add("match", Placements.not(Placements.solid(0, 0, 0)));
            columns.add(Placements.not(open));
        }
        return columns.isEmpty() ? null : Placements.allOf(columns);
    }

    List<JsonObject> pillars(TreeRoots roots, TemplateVolume volume, int quarter, Map<String, JsonResource> shared) {
        BlockStateRotation rotation = BlockStateRotation.of(0, quarter * QUARTER_DEGREES, 0);
        List<JsonObject> pillars = new ArrayList<>(roots.cells().size());
        for (TemplateBlock cell : roots.cells()) {
            TemplateColumn column = TemplateColumn.of(volume, cell.x(), cell.z(), quarter);
            JsonObject entry = new JsonObject();
            entry.addProperty("feature", pillar(rotation.apply(cell.state()), shared));
            JsonArray placement = new JsonArray();
            for (JsonObject offset : json.offset(column.x(), cell.y() - 1, cell.y() - 1, column.z())) {
                placement.add(offset);
            }
            entry.add("placement", placement);
            pillars.add(entry);
        }
        return pillars;
    }

    private String pillar(VanillaBlockState state, Map<String, JsonResource> shared) {
        JsonObject block = json.syntax().provider(List.of(new WeightedState(state, 1)));
        JsonObject cap = new JsonObject();
        JsonObject capFields = new JsonObject();
        capFields.add("to_place", block);
        cap.add("feature", json.syntax().feature("minecraft:simple_block", capFields));
        JsonArray capPlacement = new JsonArray(1);
        capPlacement.add(Placements.verticalOffset(-1));
        cap.add("placement", capPlacement);
        JsonObject canReplace = PlacementCompiler.canReplace();
        JsonObject columnFields = new JsonObject();
        columnFields.add("block", block);
        columnFields.add("can_replace", canReplace);
        columnFields.addProperty("direction", "down");
        columnFields.add("cap_feature", cap);
        JsonObject gated = new JsonObject();
        gated.add("feature", json.syntax().feature("minecraft:single_block_pillar", columnFields));
        JsonArray gate = new JsonArray(1);
        gate.add(Placements.filter(canReplace));
        gated.add("placement", gate);
        JsonArray features = new JsonArray(1);
        features.add(gated);
        JsonObject overlayFields = new JsonObject();
        overlayFields.add("features", features);
        JsonObject feature = json.syntax().feature("minecraft:overlay", overlayFields);
        String path = "object/root/" + ContentIds.hash(feature);
        shared.putIfAbsent(path, new JsonResource("data/" + namespace + "/" + json.syntax().featureFolder() + "/" + path + ".json", feature));
        return namespace + ":" + path;
    }
}

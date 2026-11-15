package art.arcane.iris.generation.procedural;

import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.iris.generation.procedural.part.ProceduralObject;
import art.arcane.iris.generation.procedural.part.ResolvedProceduralObject;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ObjectResourceLoader;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.studio.scene.EditableSceneView;
import art.arcane.iris.studio.scene.GraphNode;
import art.arcane.iris.studio.scene.SceneBakeTiming;
import art.arcane.iris.studio.scene.SceneBlocks;
import art.arcane.iris.studio.scene.SceneEntry;
import art.arcane.iris.studio.scene.SceneRequest;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * The pack's procedural objects, every variant in 3D with the part that placed each block, edited in the studio as a
 * graph of their parts.
 */
public final class ProceduralPartsSceneView implements EditableSceneView {
    private static final char FOLDER_SEPARATOR = '/';
    private static final String STARTER = """
            {"variants":6,"seed":1337,"materials":[{"role":"rock","block":"minecraft:stone"}],"parts":[{"role":"rock","blob":{"radius":[2,4],"height":[2,3]}}]}""";
    private static final Creation CREATION = new Creation("procedural", STARTER, ProceduralObjectCatalog.KEY_PREFIX);

    @Override
    public String id() {
        return "proceduralParts";
    }

    @Override
    public String label() {
        return "Procedural parts";
    }

    @Override
    public String description() {
        return "Procedural objects built from parts, every variant in 3D, edited as nodes";
    }

    @Override
    public List<SceneEntry> entries(Engine engine) {
        ProceduralObjectCatalog catalog = catalogOf(engine.getData());
        return Arrays.stream(definitions(engine.getData()).getPossibleKeys()).sorted()
                .flatMap(path -> catalog.variants(ProceduralObjectCatalog.KEY_PREFIX + path).stream())
                .map(ProceduralPartsSceneView::entryOf)
                .toList();
    }

    @Override
    public SceneBlocks scene(Engine engine, SceneRequest request) {
        ResolvedProceduralObject object = resolved(engine, request.key());
        IrisObject variant = object.variant(Math.floorMod(request.variant(), object.variantCount()))
                .orElseThrow(() -> new NoSuchElementException("Variant " + request.variant() + " of " + request.key() + " bakes no blocks"));
        Map<IrisBlockVector, Integer> parts = object.parts(variant);
        SceneBlocks scene = new SceneBlocks();
        variant.getBlocks().forEach((position, state) -> scene.add(position.getBlockX(), position.getBlockY(), position.getBlockZ(), state.key(),
                parts.getOrDefault(position, SceneBlocks.NO_PART)));
        return scene;
    }

    @Override
    public boolean tracesParts() {
        return true;
    }

    @Override
    public boolean isBaked() {
        return true;
    }

    @Override
    public SceneBakeTiming timeBake(Engine engine, String key) {
        BakeProfile profile = resolved(engine, key).profile();
        List<SceneBakeTiming.Stage> stages = Arrays.stream(BakeProfile.Stage.values())
                .map(stage -> new SceneBakeTiming.Stage(stage.label(), profile.nanos(stage)))
                .toList();
        return new SceneBakeTiming(profile.variantCount(), stages);
    }

    @Override
    public Optional<File> fileOf(Engine engine, String key) {
        ResourceLoader<ProceduralObject> definitions = definitions(engine.getData());
        if (!Arrays.asList(definitions.getPossibleKeys()).contains(key)) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.findFile(key));
    }

    // Text the pack can't read bakes no parts, so its nodes are drawn without any.
    @Override
    public List<GraphNode> graph(Engine engine, String key, JsonObject file) {
        ProceduralObject read;
        try {
            read = engine.getData().getGson().fromJson(file, ProceduralObject.class);
        } catch (JsonParseException unreadable) {
            read = new ProceduralObject();
        }
        return ProceduralObjectGraph.of(key.substring(key.lastIndexOf(FOLDER_SEPARATOR) + 1), file, read == null ? new ProceduralObject() : read);
    }

    @Override
    public Optional<Creation> creation() {
        return Optional.of(CREATION);
    }

    private static ResolvedProceduralObject resolved(Engine engine, String key) {
        return catalogOf(engine.getData()).variants(ProceduralObjectCatalog.KEY_PREFIX + key)
                .filter(ResolvedProceduralObject.class::isInstance)
                .map(ResolvedProceduralObject.class::cast)
                .orElseThrow(() -> new NoSuchElementException("No procedural object " + key));
    }

    private static ResourceLoader<ProceduralObject> definitions(IrisData data) {
        ResourceLoader<ProceduralObject> loader = data.getProceduralObjectLoader();
        if (loader == null) {
            throw new NoSuchElementException("This pack has no procedural objects");
        }
        return loader;
    }

    // The object loader's catalog is the one world generation bakes from, and forgets its bakes when the pack reloads.
    private static ProceduralObjectCatalog catalogOf(IrisData data) {
        if (data.getObjectLoader() instanceof ObjectResourceLoader objects) {
            return objects.procedural();
        }
        return new ProceduralObjectCatalog(data);
    }

    private static SceneEntry entryOf(ProceduralVariants variants) {
        String path = variants.path();
        int lastSeparator = path.lastIndexOf(FOLDER_SEPARATOR);
        String folder = lastSeparator < 0 ? "" : path.substring(0, lastSeparator);
        return new SceneEntry(path, path.substring(lastSeparator + 1), folder, variants.variantCount());
    }
}

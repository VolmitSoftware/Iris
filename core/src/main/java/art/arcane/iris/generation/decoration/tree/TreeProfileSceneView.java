package art.arcane.iris.generation.decoration.tree;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.studio.scene.SceneBlocks;
import art.arcane.iris.studio.scene.SceneEntry;
import art.arcane.iris.studio.scene.SceneForm;
import art.arcane.iris.studio.scene.SceneRequest;
import art.arcane.iris.studio.scene.SceneView;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/** The canopy shape each tree profile gives a procedural tree. */
public final class TreeProfileSceneView implements SceneView {
    private static final Set<String> HIDDEN = Set.of("name", "chance", "density", "variants", "seed", "plausible", "mode",
            "carvingSupport", "underwater", "profile");

    @Override
    public String id() {
        return "treeProfiles";
    }

    @Override
    public String label() {
        return "Tree profiles";
    }

    @Override
    public String description() {
        return "The canopy shape each tree profile gives a procedural tree";
    }

    @Override
    public List<SceneEntry> entries(Engine engine) {
        int variants = new IrisProceduralTree().getVariants();
        return Arrays.stream(IrisTreeProfile.values()).map(profile -> SceneEntry.preset(profile, variants)).toList();
    }

    @Override
    public boolean hasForm() {
        return true;
    }

    @Override
    public SceneForm form(Engine engine, String key, Map<String, String> overrides) {
        return SceneForm.of("Tree", treeOf(key, overrides), HIDDEN, overrides,
                List.of(SceneForm.describe(profileOf(key)), "Values typed here change the preview only"));
    }

    @Override
    public SceneBlocks scene(Engine engine, SceneRequest request) {
        IrisProceduralTree tree = treeOf(request.key(), request.overrides());
        return SceneBlocks.ofVariant(tree.getVariantObjects(engine.getData()), request.variant(), "tree");
    }

    private static IrisProceduralTree treeOf(String key, Map<String, String> overrides) {
        IrisProceduralTree tree = SceneForm.withOverrides(new IrisProceduralTree(), overrides);
        tree.setProfile(profileOf(key));
        return tree;
    }

    private static IrisTreeProfile profileOf(String key) {
        try {
            return IrisTreeProfile.valueOf(key);
        } catch (IllegalArgumentException unknown) {
            throw new NoSuchElementException("No tree profile " + key);
        }
    }
}

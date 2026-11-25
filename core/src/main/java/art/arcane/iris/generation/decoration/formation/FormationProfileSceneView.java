package art.arcane.iris.generation.decoration.formation;

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

/** How each formation profile shapes a formation's width from base to top. */
public final class FormationProfileSceneView implements SceneView {
    private static final Set<String> HIDDEN = Set.of("name", "chance", "density", "variants", "seed", "mode", "carvingSupport",
            "surfaceSupportBuffer", "underwater", "profile");

    @Override
    public String id() {
        return "formationProfiles";
    }

    @Override
    public String label() {
        return "Formation profiles";
    }

    @Override
    public String description() {
        return "How each formation profile shapes a formation's width from base to top";
    }

    @Override
    public List<SceneEntry> entries(Engine engine) {
        int variants = new IrisFormation().getVariants();
        return Arrays.stream(IrisFormationProfile.values()).map(profile -> SceneEntry.preset(profile, variants)).toList();
    }

    @Override
    public boolean hasForm() {
        return true;
    }

    @Override
    public SceneForm form(Engine engine, String key, Map<String, String> overrides) {
        return SceneForm.of("Formation", formationOf(key, overrides), HIDDEN, overrides,
                List.of(SceneForm.describe(profileOf(key)), "Values typed here change the preview only"));
    }

    @Override
    public SceneBlocks scene(Engine engine, SceneRequest request) {
        IrisFormation formation = formationOf(request.key(), request.overrides());
        return SceneBlocks.ofVariant(formation.getVariantObjects(engine.getData()), request.variant(), "formation");
    }

    private static IrisFormation formationOf(String key, Map<String, String> overrides) {
        IrisFormation formation = SceneForm.withOverrides(new IrisFormation(), overrides);
        formation.setProfile(profileOf(key));
        return formation;
    }

    private static IrisFormationProfile profileOf(String key) {
        try {
            return IrisFormationProfile.valueOf(key);
        } catch (IllegalArgumentException unknown) {
            throw new NoSuchElementException("No formation profile " + key);
        }
    }
}

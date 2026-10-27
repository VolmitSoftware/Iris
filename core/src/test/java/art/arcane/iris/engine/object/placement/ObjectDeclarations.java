package art.arcane.iris.engine.object.placement;

import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectSettings;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.core.modules.BuiltInPack;
import art.arcane.iris.engine.object.TestSettings;

import java.util.List;

/** Tests declare the object placements of a biome or region as its module settings. */
public final class ObjectDeclarations {
    private ObjectDeclarations() {
    }

    /** Makes {@code owner} declare {@code placements}, which Iris places as they are. */
    public static void declare(IrisRegistrant owner, List<ObjectPlacement> placements) {
        owner.setModuleSettings(BuiltInPack.settings(TestSettings.with(new ObjectSettings(), "objects", List.copyOf(placements))));
    }

    public static void declare(IrisRegistrant owner, ObjectPlacement... placements) {
        declare(owner, List.of(placements));
    }
}

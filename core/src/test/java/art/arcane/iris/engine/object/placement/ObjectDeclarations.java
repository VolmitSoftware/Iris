package art.arcane.iris.engine.object.placement;

import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectSettings;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.core.modules.BuiltInPack;
import art.arcane.iris.engine.object.TestSettings;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

/** Tests declare the object placements of a biome or region as its module settings, with the placements Iris would have read. */
public final class ObjectDeclarations {
    private static final Gson GSON = new Gson();

    private ObjectDeclarations() {
    }

    /** Makes {@code owner} declare {@code placements}, which Iris places as they are. */
    public static void declare(IrisRegistrant owner, List<IrisObjectPlacement> placements) {
        List<ObjectPlacement> declared = new ArrayList<>();
        for (IrisObjectPlacement placement : placements) {
            ObjectPlacement settings = TestSettings.of(ObjectPlacement.class, GSON.toJson(placement));
            settings.hostForm(placement);
            declared.add(settings);
        }
        owner.setModuleSettings(BuiltInPack.settings(TestSettings.with(new ObjectSettings(), "objects", declared)));
    }

    public static void declare(IrisRegistrant owner, IrisObjectPlacement... placements) {
        declare(owner, List.of(placements));
    }
}

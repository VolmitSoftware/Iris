package art.arcane.iris.pack.datapack.export.compile.object;

import art.arcane.iris.pack.datapack.export.VanillaBlockState;
import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Objects;

public record PlacementInput(IrisObjectPlacement placement, Anchor anchor, boolean tree, List<ObjectTemplates> objects,
                             List<JsonObject> processors, List<VanillaBlockState> stiltBases, String name) {
    public PlacementInput {
        Objects.requireNonNull(placement, "Object placement");
        Objects.requireNonNull(anchor, "Placement anchor");
        objects = List.copyOf(objects);
        processors = List.copyOf(processors);
        stiltBases = List.copyOf(stiltBases);
        VanillaResourceIdentifier.requirePath(name, "placement name");
    }

    public enum Anchor {
        SURFACE,
        CAVE
    }
}

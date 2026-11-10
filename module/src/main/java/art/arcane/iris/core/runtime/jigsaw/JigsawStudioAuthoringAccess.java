package art.arcane.iris.core.runtime.jigsaw;

import art.arcane.iris.core.structure.authoring.StructureOwnershipManifest;

import java.io.IOException;
import java.util.Objects;

public final class JigsawStudioAuthoringAccess {
    private JigsawStudioAuthoringAccess() {
    }

    public static boolean isEditable(StructureOwnershipManifest manifest) {
        StructureOwnershipManifest ownership = Objects.requireNonNull(
                manifest,
                "Jigsaw Studio ownership manifest");
        return ownership.provenance().origin() != StructureOwnershipManifest.Origin.MANAGED_DATAPACK;
    }

    public static StructureOwnershipManifest requireEditable(
            StructureOwnershipManifest manifest
    ) throws IOException {
        StructureOwnershipManifest ownership = Objects.requireNonNull(
                manifest,
                "Jigsaw Studio ownership manifest");
        if (!isEditable(ownership)) {
            throw new IOException("This graph is read-only because it is managed by datapack ingest; "
                    + "adopt or clone it before making authoring changes.");
        }
        return ownership;
    }
}

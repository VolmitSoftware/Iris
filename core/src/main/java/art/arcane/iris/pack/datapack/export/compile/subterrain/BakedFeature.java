package art.arcane.iris.pack.datapack.export.compile.subterrain;

import java.util.List;
import java.util.Objects;

public record BakedFeature(String id, List<BakedRoom> rooms, int lowestAnchorY, int highestAnchorY) {
    public BakedFeature {
        Objects.requireNonNull(id, "Subterrain feature id");
        rooms = List.copyOf(rooms);
        if (rooms.isEmpty()) {
            throw new IllegalArgumentException("Subterrain feature " + id + " has no baked rooms");
        }
        if (lowestAnchorY > highestAnchorY) {
            throw new IllegalArgumentException("Subterrain feature " + id + " has an empty anchor range " + lowestAnchorY + ".." + highestAnchorY);
        }
    }

    public int reach() {
        int reach = 0;
        for (BakedRoom room : rooms) {
            reach = Math.max(reach, room.reach());
        }
        return reach;
    }
}

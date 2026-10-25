package art.arcane.iris.generation.hydrology.water;

import java.util.List;

public record ShapedNetwork(List<Reach> reaches, List<Pond> ponds) {
    public ShapedNetwork {
        reaches = List.copyOf(reaches);
        ponds = List.copyOf(ponds);
    }
}

package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.subterrain.SubterrainCell;

public final class CarveOwnership {
    private CarveOwnership() {
    }

    public static Owner resolve(boolean hydrology, boolean subterrain, boolean naturalOpening) {
        if (hydrology) {
            return Owner.HYDROLOGY;
        }
        if (subterrain) {
            return Owner.SUBTERRAIN;
        }
        return naturalOpening ? Owner.NATURAL_OPENING : Owner.CAVERN;
    }

    public static boolean carved(HydrologyCaveCell hydrology, SubterrainCell subterrain, boolean naturalOpening, boolean cavern) {
        return switch (resolve(hydrology != null, subterrainClaims(subterrain), naturalOpening)) {
            case HYDROLOGY -> hydrology.carves();
            case SUBTERRAIN -> subterrain.carve();
            case NATURAL_OPENING -> true;
            case CAVERN -> cavern;
        };
    }

    public static boolean subterrainClaims(SubterrainCell cell) {
        return cell != null && cell.owned() && (!cell.solid() || cell.room().reservedSolid());
    }

    public enum Owner {
        HYDROLOGY,
        SUBTERRAIN,
        NATURAL_OPENING,
        CAVERN
    }
}

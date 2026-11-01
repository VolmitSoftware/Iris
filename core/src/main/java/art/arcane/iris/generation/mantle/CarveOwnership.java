package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.subterrain.SubterrainCell;
import art.arcane.iris.generation.subterrain.SubterrainRasterizer;
import art.arcane.iris.generation.terrain.Terrain3DColumn;

import java.util.function.BooleanSupplier;

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

    public static boolean carved(Terrain3DColumn transformed, int y, HydrologyCaveCell hydrology, SubterrainCell subterrain,
                                 BooleanSupplier naturalOpening, BooleanSupplier cavern) {
        if (transformed != null) {
            if (hydrology == null && SubterrainRasterizer.protectsPlacement(subterrain)) {
                return subterrain.carve();
            }
            return y >= 0 && y < transformed.topY() && !transformed.isSolid(y);
        }
        boolean claims = subterrainClaims(subterrain);
        return switch (resolve(hydrology != null, claims, hydrology == null && !claims && naturalOpening.getAsBoolean())) {
            case HYDROLOGY -> hydrology.carves();
            case SUBTERRAIN -> subterrain.carve();
            case NATURAL_OPENING -> true;
            case CAVERN -> cavern.getAsBoolean();
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

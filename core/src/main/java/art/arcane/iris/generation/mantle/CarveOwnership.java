package art.arcane.iris.generation.mantle;

public final class CarveOwnership {
    private CarveOwnership() {
    }

    public static Owner resolve(boolean hydrology, boolean subterrain, boolean transformed) {
        if (hydrology) {
            return Owner.HYDROLOGY;
        }
        if (subterrain) {
            return Owner.SUBTERRAIN;
        }
        return transformed ? Owner.TRANSFORMED : Owner.NATURAL;
    }

    public enum Owner {
        HYDROLOGY,
        SUBTERRAIN,
        TRANSFORMED,
        NATURAL
    }
}

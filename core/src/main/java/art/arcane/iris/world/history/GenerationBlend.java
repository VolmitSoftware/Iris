package art.arcane.iris.world.history;

public final class GenerationBlend {
    private GenerationBlend() {
    }

    public static double newEpochWeight(double distanceToHistoricalBlocks, int widthBlocks) {
        if (!Double.isFinite(distanceToHistoricalBlocks) || distanceToHistoricalBlocks < 0D) {
            throw new IllegalArgumentException("Distance must be finite and non-negative");
        }
        if (widthBlocks <= 0) {
            throw new IllegalArgumentException("Blend width must be positive");
        }
        if (distanceToHistoricalBlocks <= 0D) {
            return 0D;
        }
        if (distanceToHistoricalBlocks >= widthBlocks) {
            return 1D;
        }
        return smootherstep(distanceToHistoricalBlocks / widthBlocks);
    }

    public static double smootherstep(double progress) {
        if (!Double.isFinite(progress)) {
            throw new IllegalArgumentException("Progress must be finite");
        }
        if (progress <= 0D) {
            return 0D;
        }
        if (progress >= 1D) {
            return 1D;
        }
        return progress * progress * progress * (progress * (progress * 6D - 15D) + 10D);
    }

    public static double interpolate(double historicalValue, double newValue, double newEpochWeight) {
        if (!Double.isFinite(historicalValue) || !Double.isFinite(newValue)) {
            throw new IllegalArgumentException("Blend values must be finite");
        }
        validateWeight(newEpochWeight);
        if (newEpochWeight == 0D) {
            return historicalValue;
        }
        if (newEpochWeight == 1D) {
            return newValue;
        }
        return historicalValue + (newValue - historicalValue) * newEpochWeight;
    }

    public static int interpolateHeight(int historicalHeight, int newHeight, double newEpochWeight) {
        validateWeight(newEpochWeight);
        if (newEpochWeight == 0D) {
            return historicalHeight;
        }
        if (newEpochWeight == 1D) {
            return newHeight;
        }
        return (int) Math.round(historicalHeight + ((double) newHeight - historicalHeight) * newEpochWeight);
    }

    public static boolean usesHistoricalMaterial(int blockX, int blockY, int blockZ, double newWeight) {
        validateWeight(newWeight);
        if (newWeight == 0D) {
            return true;
        }
        if (newWeight == 1D) {
            return false;
        }
        long hash = Math.floorDiv(blockX, 8) * 341873128712L
                ^ Math.floorDiv(blockY, 8) * 132897987541L
                ^ Math.floorDiv(blockZ, 8) * 42317861L;
        hash = (hash ^ (hash >>> 30)) * 0xbf58476d1ce4e5b9L;
        hash = (hash ^ (hash >>> 27)) * 0x94d049bb133111ebL;
        hash ^= hash >>> 31;
        return newWeight < (hash >>> 11) * 0x1.0p-53;
    }

    private static void validateWeight(double weight) {
        if (!Double.isFinite(weight) || weight < 0D || weight > 1D) {
            throw new IllegalArgumentException("Blend weight must be finite and between zero and one");
        }
    }
}

package art.arcane.iris.generation.subterrain;

import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.decoration.IrisProceduralBlocks;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.matter.MatterCavern;

import java.util.List;
import java.util.Objects;

public final class SubterrainRasterizer {
    private SubterrainRasterizer() {
    }

    public static void validateMaterials(List<IrisSubterrainFeature> features) {
        for (IrisSubterrainFeature feature : features) {
            if (feature == null || !feature.isEnabled()) {
                continue;
            }
            NativeBlockState state = B.getStateOrNull(feature.getSolid(), false);
            if (!isRetainedSolid(state)) {
                throw new IllegalArgumentException("Subterrain feature " + feature.getId()
                        + " requires a full, opaque, dry vanilla solid block without gravity: " + feature.getSolid());
            }
        }
    }

    public static boolean isRetainedSolid(NativeBlockState state) {
        return state != null && state.isSolid() && state.isOccluding() && !state.isFluid()
                && !state.isWaterLogged() && !state.isCustom() && !IrisProceduralBlocks.isGravityAffected(state);
    }

    public static void rasterize(SubterrainPlanner planner, int chunkX, int chunkZ, CellConsumer consumer) {
        Objects.requireNonNull(planner);
        Objects.requireNonNull(consumer);
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        List<SubterrainPlan> plans = planner.plansForBounds(baseX - 1, baseZ - 1, baseX + 16, baseZ + 16);
        if (plans.isEmpty()) {
            return;
        }
        int lowY = Integer.MAX_VALUE;
        int highY = Integer.MIN_VALUE;
        for (SubterrainPlan plan : plans) {
            lowY = Math.min(lowY, plan.bounds().minY());
            highY = Math.max(highY, plan.bounds().maxY());
        }
        for (int x = baseX; x < baseX + 16; x++) {
            for (int z = baseZ; z < baseZ + 16; z++) {
                for (int y = lowY; y <= highY; y++) {
                    SubterrainCell cell = planner.sample(plans, x, y, z);
                    if (cell.owned()) {
                        consumer.accept(x, y, z, cell);
                    }
                }
            }
        }
    }

    public static MatterCavern cavern(SubterrainCell cell) {
        if (!cell.carve()) {
            return null;
        }
        byte liquid = switch (cell.kind()) {
            case WATER -> 1;
            case LAVA -> 2;
            default -> 3;
        };
        return new MatterCavern(true, cell.room().biome(), liquid);
    }

    public static NativeBlockState state(SubterrainCell cell) {
        return IrisProceduralBlocks.normalizeWaterlogging(B.getState(cell.material()), null, false);
    }

    public static boolean protectsPlacement(SubterrainCell cell) {
        return cell != null && cell.owned() && (cell.room().reservedSolid() || cell.room().reservedPassage());
    }

    @FunctionalInterface
    public interface CellConsumer {
        void accept(int x, int worldY, int z, SubterrainCell cell);
    }
}

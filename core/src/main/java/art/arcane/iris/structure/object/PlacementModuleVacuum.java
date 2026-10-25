package art.arcane.iris.structure.object;

import art.arcane.iris.generation.decoration.IrisVacuumSettings;

import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.generation.block.B;
import art.arcane.volmlib.util.noise.SimplexNoise;

/** Collects written object bounds, then bends terrain to meet them. Installed for vacuum modes. */
final class PlacementModuleVacuum implements PlacementModule {
    static final PlacementLifecycleDependency COLLECT = new PlacementLifecycleDependency("vacuum.collect");
    static final PlacementLifecycleDependency FINISH = new PlacementLifecycleDependency("vacuum.finish");
    private static final long VACUUM_WAVE_SEED = 7392113L;

    @Override
    public void install(PlacementLifecycleBuilder lifecycle) {
        lifecycle.add(COLLECT, PlacementStage.BLOCK_WRITTEN, (context, state, block) -> {
            state.vacuumLowest = Math.min(state.vacuumLowest, block.y());
            state.vacuumHighest = Math.max(state.vacuumHighest, block.y());
        });
        lifecycle.add(FINISH, PlacementStage.AFTER_BLOCKS, (context, state, ignored) -> {
            if (state.vacuumLowest == Integer.MAX_VALUE || state.placer.getEngine() == null) {
                return;
            }
            vacuumTerrain(context.self, state.placer, state.config, context.x, context.z,
                    state.bounds.minX(), state.bounds.maxX(), state.bounds.minZ(), state.bounds.maxZ(),
                    state.vacuumLowest, state.vacuumHighest);
        }, COLLECT, PlacementModuleBlocks.COMPLETE, PlacementModuleTreeGrounding.ROOTS);
    }

    private static void vacuumTerrain(IrisObject self, IObjectPlacer placer, IrisObjectPlacement config, int centerX, int centerZ, int lowX, int highX, int lowZ, int highZ, int baseY, int topY) {
        ObjectPlaceMode mode = config.getMode();
        IrisVacuumSettings settings = config.getVacuumSettings();
        int radius = IrisObjectVacuum.resolveRadius(mode, settings);
        int step = IrisObjectVacuum.resolveStep(mode);
        double falloff = IrisObjectVacuum.resolveFalloff(settings);
        int jitter = settings != null ? Math.max(0, settings.getOrganicJitter()) : 4;
        boolean organicEdge = mode == ObjectPlaceMode.VACUUM_ORGANIC;
        boolean wavyEdge = mode == ObjectPlaceMode.VACUUM_WAVY;
        double waveAmplitude = IrisObjectVacuum.resolveWaveAmplitude(settings);
        double waveScale = IrisObjectVacuum.resolveWaveScale(settings);
        SimplexNoise waveNoise = (wavyEdge && waveAmplitude > 0) ? new SimplexNoise(VACUUM_WAVE_SEED) : null;
        int meetY = baseY - 1;

        IrisComplex complex = placer.getEngine().getComplex();
        int worldMin = placer.getEngine().getMinHeight();
        int worldMax = worldMin + placer.getEngine().getHeight() - 1;

        for (int dx = lowX - radius; dx <= highX + radius; dx += step) {
            for (int dz = lowZ - radius; dz <= highZ + radius; dz += step) {
                int cx = centerX + dx;
                int cz = centerZ + dz;
                double effRadius = radius;
                if (organicEdge && jitter > 0) {
                    long h = ((long) cx * 341873128712L) ^ ((long) cz * 132897987541L);
                    double n = ((Math.abs(h) % 1000) / 1000.0) - 0.5;
                    effRadius = Math.max(1.0, radius + (n * 2.0 * jitter));
                }
                int origY = placer.getHighest(cx, cz, self.getLoader(), true);
                int targetY = IrisObjectVacuum.columnTargetY(dx, dz, lowX, highX, lowZ, highZ, effRadius, falloff, origY, meetY);
                if (waveNoise != null) {
                    int outX = IrisObjectVacuum.outset(dx, lowX, highX);
                    int outZ = IrisObjectVacuum.outset(dz, lowZ, highZ);
                    double waveDistance = Math.sqrt((double) (outX * outX) + (double) (outZ * outZ));
                    double sample = waveNoise.noiseSigned(cx * waveScale, cz * waveScale);
                    targetY += IrisObjectVacuum.waveOffset(waveDistance, effRadius, sample, waveAmplitude);
                }
                if (targetY == origY) {
                    continue;
                }
                targetY = Math.max(worldMin + 1, Math.min(worldMax, targetY));
                if (targetY > origY) {
                    NativeBlockState fill = complex != null ? complex.getRockStream().get(cx, cz) : null;
                    if (B.isAir(fill)) {
                        fill = IrisObject.States.stone();
                    }
                    for (int yy = origY + 1; yy <= targetY; yy++) {
                        placer.set(cx, yy, cz, fill);
                    }
                } else if (targetY < origY) {
                    boolean inside = IrisObjectVacuum.outset(dx, lowX, highX) == 0 && IrisObjectVacuum.outset(dz, lowZ, highZ) == 0;
                    int carveFloor = IrisObjectVacuum.carveFloorY(targetY, topY, inside);
                    for (int yy = origY; yy >= carveFloor; yy--) {
                        placer.set(cx, yy, cz, IrisObject.States.air());
                    }
                }
            }
        }
    }
}

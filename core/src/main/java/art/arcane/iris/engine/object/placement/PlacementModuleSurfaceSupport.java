package art.arcane.iris.engine.object.placement;

import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.terrain.carving.CarvingMode;
import art.arcane.iris.spi.IrisLogging;
import java.util.concurrent.ConcurrentHashMap;

/** Carving and surface support rules, including the bedrock diagnostic. */
final class PlacementModuleSurfaceSupport implements PlacementModule {
    static final PlacementLifecycleDependency CHECK = new PlacementLifecycleDependency("validate.surface");
    private static final long IMPLAUSIBLE_BEDROCK_WARN_THROTTLE_MS = 5000L;
    private static final ConcurrentHashMap<String, Long> IMPLAUSIBLE_BEDROCK_WARNS = new ConcurrentHashMap<>();

    @Override
    public void install(PlacementLifecycleBuilder lifecycle) {
        lifecycle.add(CHECK, PlacementStage.VALIDATE, (context, state, ignored) -> {
            IrisObjectPlacement config = state.config;
            boolean rawStructurePiece = config.getMode() == ObjectPlaceMode.STRUCTURE_PIECE;
            if (context.yv < 0 && !config.isForcePlace() && !config.isFromBottom()
                    && config.getMode() != ObjectPlaceMode.FLOATING && !rawStructurePiece
                    && config.getCarvingSupport().supportsSurface() && state.placer.getEngine() != null
                    && state.placer.getEngine().getDimension().isBedrock() && state.y <= 1) {
                warnImplausibleBedrockPlacement(context.self, state.placer, config, context.x, state.y, context.z);
                state.reject("bedrock-anchor");
                return;
            }
            boolean surfaceAnchored = context.yv < 0 ? config.getCarvingSupport().supportsSurface()
                    : config.getCarvingSupport() == CarvingMode.SURFACE_ONLY;
            if (surfaceAnchored && !config.isForcePlace() && !config.isFromBottom()
                    && config.getMode() != ObjectPlaceMode.FLOATING && !rawStructurePiece
                    && !config.isUnderwater() && !config.isOnwater() && config.isRequireSurfaceSupport()
                    && IrisSurfaceSupport.isUnsupported(context.placer, context.self.getLoader(), context.x, context.z,
                    config.getTranslate(), config.getRotation(), state.spinX, state.spinY, state.spinZ,
                    context.self.getSurfaceSupportOffsets(), config.getSurfaceSupportBuffer(), config.getSurfaceSupportDepth())) {
                state.reject("surface-support");
            }
        }, PlacementModuleAnchor.POSITIONED);
    }

    static boolean rejectsAnchor(PlacementContext context, PlacementState state, int x, int y, int z) {
        return switch (state.config.getCarvingSupport()) {
            case SURFACE_ONLY -> state.placer.isCarved(x, y, z);
            case CARVING_ONLY -> !(state.placer.isCarved(x, y, z) || state.placer.isCarved(x, y - 1, z)
                    || state.placer.isCarved(x, y - 2, z) || state.placer.isCarved(x, y - 3, z));
            case ANYWHERE -> false;
        };
    }

    private static void warnImplausibleBedrockPlacement(IrisObject self, IObjectPlacer placer, IrisObjectPlacement config, int x, int y, int z) {
        String key = self.getLoadKey();
        String fingerprint = (key == null ? "<null>" : key) + "|" + config.getMode();
        long now = System.currentTimeMillis();
        Long last = IMPLAUSIBLE_BEDROCK_WARNS.get(fingerprint);
        if (last != null && now - last < IMPLAUSIBLE_BEDROCK_WARN_THROTTLE_MS) {
            return;
        }
        IMPLAUSIBLE_BEDROCK_WARNS.put(fingerprint, now);
        IrisLogging.warn("Implausible object placement rejected: "
                + (key == null ? "<no loadKey>" : key)
                + " resolved anchorY=" + y + " at (" + x + "," + z + ") mode=" + config.getMode()
                + " carving=" + config.getCarvingSupport()
                + ". Surface-anchored placement should never land on the bedrock row. "
                + "Height sampling returned a bogus value — not configured for floor placement "
                + "(forcePlace=false, fromBottom=false, mode!=FLOATING). Skipping to protect bedrock.");
    }

}

/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.engine.object.placement;

import art.arcane.iris.builtin.validate.ObjectLimit;
import com.volmit.FeatureRegistry;
import art.arcane.iris.builtin.IrisStages;
import art.arcane.iris.core.modules.IrisPlacement;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.NativeStructureVolume;
import art.arcane.iris.engine.framework.PlacedObject;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.terrain.carving.CarvingMode;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.collection.KList;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.block.BlockState;
import com.volmit.object.ObjectBlock;
import com.volmit.stage.PlacementStageContext;
import com.volmit.stage.StageGraph;
import com.volmit.world.BlockPosition;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Module {@code iris:validate}: the checks that reject a placement before anything is written. Forced
 * placements skip all but the native structure check, which only structure pieces skip.
 */
public final class ValidateModule implements IrisModule {
    private static final long IMPLAUSIBLE_BEDROCK_WARN_THROTTLE_MS = 5000L;
    private static final int BEDROCK_ROW = 1;
    private static final Set<String> AIR_MATERIALS = Set.of("minecraft:air", "minecraft:cave_air");
    private static final ConcurrentHashMap<String, Long> IMPLAUSIBLE_BEDROCK_WARNS = new ConcurrentHashMap<>();

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:validate", "Placement checks", PlacementModules.VERSION);
    }

    @Override
    public void register(FeatureRegistry registry) {
        registry.registerHeldType(ObjectLimit.class);
    }

    @Override
    public void install(StageGraph graph) {
        graph.define(IrisStages.Validate.SURFACE)
                .requires(IrisStages.Anchor.HEIGHT)
                .run(ValidateModule::checkSurface);
        graph.define(IrisStages.Validate.LIMITS)
                .requires(IrisStages.Validate.SURFACE, IrisStages.Anchor.HEIGHT)
                .run(ValidateModule::checkLimits);
        graph.define(IrisStages.Validate.STRUCTURES)
                .requires(IrisStages.Validate.LIMITS, IrisStages.Anchor.HEIGHT)
                .run(ValidateModule::checkNativeStructures);
        graph.define(IrisStages.Validate.COLLISIONS)
                .requires(IrisStages.Validate.STRUCTURES, IrisStages.Anchor.HEIGHT)
                .run(ValidateModule::checkCollisions);
    }

    private static void checkSurface(PlacementStageContext context) {
        IrisPlacement placement = IrisPlacement.current();
        PreparedPlacement prepared = context.output(IrisStages.Prepare.TRANSFORMS);
        IrisObjectPlacement config = prepared.config();
        if (config.isForcePlace() || config.isFromBottom() || prepared.mode() == ObjectPlaceMode.FLOATING
                || prepared.mode() == ObjectPlaceMode.STRUCTURE_PIECE) {
            return;
        }
        int y = context.output(IrisStages.Anchor.HEIGHT).y();
        boolean sampled = placement.requestedY() < 0;
        if (sampled && config.getCarvingSupport().supportsSurface() && isOnBedrock(prepared, y)) {
            warnImplausibleBedrockPlacement(placement, config, y);
            context.reject("bedrock-anchor");
            return;
        }
        boolean surfaceAnchored = sampled ? config.getCarvingSupport().supportsSurface()
                : config.getCarvingSupport() == CarvingMode.SURFACE_ONLY;
        if (surfaceAnchored && !config.isUnderwater() && !config.isOnwater() && config.isRequireSurfaceSupport()
                && lacksSurfaceSupport(placement, prepared)) {
            context.reject("surface-support");
        }
    }

    private static boolean isOnBedrock(PreparedPlacement prepared, int y) {
        Engine engine = prepared.terrain().getEngine();
        return engine != null && engine.getDimension().isBedrock() && y <= BEDROCK_ROW;
    }

    private static boolean lacksSurfaceSupport(IrisPlacement placement, PreparedPlacement prepared) {
        IrisObjectPlacement config = prepared.config();
        IrisObject object = placement.object();
        return IrisSurfaceSupport.isUnsupported(placement.placer(), object.getLoader(), placement.x(), placement.z(),
                config.getTranslate(), config.getRotation(), prepared.spinX(), prepared.spinY(), prepared.spinZ(),
                object.getSurfaceSupportOffsets(), config.getSurfaceSupportBuffer(), config.getSurfaceSupportDepth());
    }

    private static void checkLimits(PlacementStageContext context) {
        IrisPlacement placement = IrisPlacement.current();
        PreparedPlacement prepared = context.output(IrisStages.Prepare.TRANSFORMS);
        IrisObjectPlacement config = prepared.config();
        if (config.isForcePlace() || prepared.mode() == ObjectPlaceMode.STRUCTURE_PIECE) {
            return;
        }
        int y = context.output(IrisStages.Anchor.HEIGHT).y();
        int rotated = prepared.rotatedCenterY();
        int translated = prepared.translatedCenterY();
        if (placement.requestedY() < 0 && prepared.mode() != ObjectPlaceMode.FLOATING && !config.isUnderwater()
                && !config.isOnwater() && prepared.terrain().isUnderwater(placement.x(), placement.z())) {
            context.reject("underwater-anchor");
        } else if (placement.cave() != null
                && Math.max(0, placement.object().h + prepared.randomY() + translated) + 1 >= placement.cave().getHeight()) {
            context.reject("cave-clearance");
        } else if (config.isUnderwater() && y + rotated + translated >= prepared.terrain().getFluidHeight()) {
            context.reject("fluid-height");
        } else if (!config.getClamp().canPlace(y + rotated + translated, y - rotated + translated)) {
            context.reject("height-clamp");
        }
    }

    private static void checkNativeStructures(PlacementStageContext context) {
        PreparedPlacement prepared = context.output(IrisStages.Prepare.TRANSFORMS);
        if (prepared.mode() == ObjectPlaceMode.STRUCTURE_PIECE) {
            return;
        }
        int y = context.output(IrisStages.Anchor.HEIGHT).y() + prepared.randomY();
        if (meetsNativeStructure(context, IrisPlacement.current(), prepared, y)) {
            context.reject("native-structure");
        }
    }

    private static boolean meetsNativeStructure(PlacementStageContext context, IrisPlacement placement,
                                                PreparedPlacement prepared, int y) {
        Engine engine = prepared.terrain().getEngine();
        if (engine == null) {
            return false;
        }
        int x = placement.x();
        int z = placement.z();
        int margin = nativeStructureMargin(placement.object(), prepared);
        KList<NativeStructureVolume> volumes = engine.getNativeStructureVolumes(x - margin, z - margin, x + margin, z + margin);
        if (volumes == null || volumes.isEmpty()) {
            return false;
        }
        boolean painted = WorldVolume.isPainted(placement, prepared);
        int worldY = y + engine.getMinHeight();
        int envelopeMinY = painted ? Integer.MIN_VALUE : worldY - margin;
        int envelopeMaxY = painted ? Integer.MAX_VALUE : worldY + margin;
        boolean envelopeMeetsPiece = volumes.stream()
                .anyMatch(volume -> volume.intersects(x - margin, envelopeMinY, z - margin, x + margin, envelopeMaxY, z + margin));
        if (!envelopeMeetsPiece) {
            return false;
        }
        for (ObjectBlock block : context.request().object().blocks()) {
            if (isAir(block.state())) {
                continue;
            }
            BlockPosition offset = prepared.transform(block.position());
            int blockX = x + offset.x();
            int blockZ = z + offset.z();
            int blockY = painted
                    ? offset.y() + Math.floorDiv(placement.object().h, 2)
                    + prepared.terrain().getHighest(blockX, blockZ, placement.object().getLoader(), prepared.config().isUnderwater())
                    : y + offset.y();
            int worldBlockY = blockY + engine.getMinHeight();
            for (NativeStructureVolume volume : volumes) {
                if (volume.containsWithin(blockX, worldBlockY, blockZ, prepared.warpMargin())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** How far around the anchor any block of the transformed object can reach. */
    private static int nativeStructureMargin(IrisObject object, PreparedPlacement prepared) {
        int margin = (Math.max(object.getW(), Math.max(object.getH(), object.getD())) / 2) + 1 + prepared.warpMargin();
        if (prepared.translation() == null) {
            return margin;
        }
        return margin + Math.max(Math.abs(prepared.translation().getBlockX()),
                Math.max(Math.abs(prepared.translation().getBlockY()), Math.abs(prepared.translation().getBlockZ())));
    }

    private static boolean isAir(BlockState state) {
        return AIR_MATERIALS.contains(state.material().toString());
    }

    private static void checkCollisions(PlacementStageContext context) {
        IrisPlacement placement = IrisPlacement.current();
        PreparedPlacement prepared = context.output(IrisStages.Prepare.TRANSFORMS);
        IrisObjectPlacement config = prepared.config();
        if (config.isForcePlace() || prepared.mode() == ObjectPlaceMode.STRUCTURE_PIECE
                || (config.getAllowedCollisions().isEmpty() && config.getForbiddenCollisions().isEmpty())) {
            return;
        }
        Engine engine = placement.data().getEngine();
        if (engine == null) {
            throw new IllegalStateException("Object placement requires active Iris data for collision settings.");
        }
        WorldVolume volume = WorldVolume.of(placement, prepared, context.output(IrisStages.Anchor.HEIGHT).y() + prepared.randomY());
        if (collidesWithForbiddenObject(engine, config, volume)) {
            context.reject("object-collision");
        }
    }

    private static boolean collidesWithForbiddenObject(Engine engine, IrisObjectPlacement config, WorldVolume volume) {
        for (int x = volume.minX(); x <= volume.maxX(); x++) {
            for (int y = volume.minY(); y <= volume.maxY(); y++) {
                for (int z = volume.minZ(); z <= volume.maxZ(); z++) {
                    PlacedObject placed = engine.getObjectPlacement(x, y, z);
                    if (placed == null || placed.getObject() == null) {
                        continue;
                    }
                    String key = placed.getObject().getLoadKey();
                    if (key != null && config.getForbiddenCollisions().contains(key) && !config.getAllowedCollisions().contains(key)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void warnImplausibleBedrockPlacement(IrisPlacement placement, IrisObjectPlacement config, int y) {
        String key = placement.object().getLoadKey();
        String fingerprint = (key == null ? "<null>" : key) + "|" + config.getMode();
        long now = System.currentTimeMillis();
        Long last = IMPLAUSIBLE_BEDROCK_WARNS.get(fingerprint);
        if (last != null && now - last < IMPLAUSIBLE_BEDROCK_WARN_THROTTLE_MS) {
            return;
        }
        IMPLAUSIBLE_BEDROCK_WARNS.put(fingerprint, now);
        IrisLogging.warn("Implausible object placement rejected: "
                + (key == null ? "<no loadKey>" : key)
                + " resolved anchorY=" + y + " at (" + placement.x() + "," + placement.z() + ") mode=" + config.getMode()
                + " carving=" + config.getCarvingSupport()
                + ". Surface-anchored placement should never land on the bedrock row. "
                + "Height sampling returned a bogus value — not configured for floor placement "
                + "(forcePlace=false, fromBottom=false, mode!=FLOATING). Skipping to protect bedrock.");
    }
}

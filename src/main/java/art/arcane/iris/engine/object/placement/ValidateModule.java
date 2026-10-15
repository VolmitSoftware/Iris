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

import art.arcane.iris.builtin.IrisStages;
import art.arcane.iris.builtin.anchor.PlaceTrait;
import art.arcane.iris.builtin.carve.CarvingMode;
import art.arcane.iris.builtin.placement.CaveSpace;
import art.arcane.iris.builtin.placement.ObjectPlacement;
import art.arcane.iris.builtin.placement.ObjectShape;
import art.arcane.iris.builtin.placement.PlacementSettings;
import art.arcane.iris.builtin.placement.PlacementTerrain;
import art.arcane.iris.builtin.placement.StructureVolumes;
import art.arcane.iris.builtin.prepare.PreparedPlacement;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleLog;
import com.volmit.block.BlockState;
import com.volmit.object.ObjectBlock;
import com.volmit.stage.PlacementStageContext;
import com.volmit.stage.StageGraph;
import com.volmit.world.BlockPosition;

import java.util.Optional;
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
        ObjectPlacement placement = ObjectPlacement.current();
        PreparedPlacement prepared = context.output(IrisStages.Prepare.TRANSFORMS);
        PlacementSettings settings = prepared.settings();
        if (settings.forcePlace() || settings.fromBottom() || prepared.mode().has(PlaceTrait.FREE_HEIGHT)) {
            return;
        }
        int y = context.output(IrisStages.Anchor.HEIGHT).y();
        boolean sampled = placement.requestedY() < 0;
        if (sampled && settings.carvingSupport().supportsSurface() && isOnBedrock(prepared, y)) {
            warnImplausibleBedrockPlacement(placement, settings, y, context.log());
            context.reject("bedrock-anchor");
            return;
        }
        boolean surfaceAnchored = sampled ? settings.carvingSupport().supportsSurface()
                : settings.carvingSupport() == CarvingMode.SURFACE_ONLY;
        if (surfaceAnchored && !settings.underwater() && !settings.onwater() && settings.requireSurfaceSupport()
                && lacksSurfaceSupport(placement, prepared)) {
            context.reject("surface-support");
        }
    }

    private static boolean isOnBedrock(PreparedPlacement prepared, int y) {
        PlacementTerrain terrain = prepared.terrain();
        return terrain.hasWorld() && terrain.hasBedrock() && y <= BEDROCK_ROW;
    }

    private static boolean lacksSurfaceSupport(ObjectPlacement placement, PreparedPlacement prepared) {
        PlacementSettings settings = prepared.settings();
        return IrisSurfaceSupport.isUnsupported(prepared.terrain(), placement.x(), placement.z(), settings.translate(),
                prepared.rotation(), prepared.spinX(), prepared.spinY(), prepared.spinZ(),
                placement.object().surfaceSupportOffsets(), settings.surfaceSupportBuffer(), settings.surfaceSupportDepth());
    }

    private static void checkLimits(PlacementStageContext context) {
        ObjectPlacement placement = ObjectPlacement.current();
        PreparedPlacement prepared = context.output(IrisStages.Prepare.TRANSFORMS);
        PlacementSettings settings = prepared.settings();
        if (settings.forcePlace() || prepared.mode().has(PlaceTrait.RAW_STAMP)) {
            return;
        }
        int y = context.output(IrisStages.Anchor.HEIGHT).y();
        int rotated = prepared.rotatedCenterY();
        int translated = prepared.translatedCenterY();
        Optional<CaveSpace> cave = placement.cave();
        if (placement.requestedY() < 0 && !prepared.mode().has(PlaceTrait.FREE_HEIGHT) && !settings.underwater()
                && !settings.onwater() && prepared.terrain().isUnderwater(placement.x(), placement.z())) {
            context.reject("underwater-anchor");
        } else if (cave.isPresent()
                && Math.max(0, placement.object().height() + prepared.randomY() + translated) + 1 >= cave.get().height()) {
            context.reject("cave-clearance");
        } else if (settings.underwater() && y + rotated + translated >= prepared.terrain().fluidHeight()) {
            context.reject("fluid-height");
        } else if (!settings.clamp().canPlace(y + rotated + translated, y - rotated + translated)) {
            context.reject("height-clamp");
        }
    }

    private static void checkNativeStructures(PlacementStageContext context) {
        PreparedPlacement prepared = context.output(IrisStages.Prepare.TRANSFORMS);
        if (prepared.mode().has(PlaceTrait.RAW_STAMP)) {
            return;
        }
        int y = context.output(IrisStages.Anchor.HEIGHT).y() + prepared.randomY();
        if (meetsNativeStructure(context, ObjectPlacement.current(), prepared, y)) {
            context.reject("native-structure");
        }
    }

    private static boolean meetsNativeStructure(PlacementStageContext context, ObjectPlacement placement,
                                                PreparedPlacement prepared, int y) {
        PlacementTerrain terrain = prepared.terrain();
        if (!terrain.hasWorld()) {
            return false;
        }
        int x = placement.x();
        int z = placement.z();
        int margin = nativeStructureMargin(placement.object(), prepared);
        StructureVolumes volumes = terrain.structureVolumes(x - margin, z - margin, x + margin, z + margin);
        if (volumes.isEmpty()) {
            return false;
        }
        boolean painted = WorldVolume.isPainted(placement, prepared);
        int worldY = y + terrain.minHeight();
        int envelopeMinY = painted ? Integer.MIN_VALUE : worldY - margin;
        int envelopeMaxY = painted ? Integer.MAX_VALUE : worldY + margin;
        if (!volumes.intersects(x - margin, envelopeMinY, z - margin, x + margin, envelopeMaxY, z + margin)) {
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
                    ? offset.y() + Math.floorDiv(placement.object().height(), 2)
                    + terrain.highest(blockX, blockZ, prepared.settings().underwater())
                    : y + offset.y();
            int worldBlockY = blockY + terrain.minHeight();
            if (volumes.containsWithin(blockX, worldBlockY, blockZ, prepared.warpMargin())) {
                return true;
            }
        }
        return false;
    }

    /** How far around the anchor any block of the transformed object can reach. */
    private static int nativeStructureMargin(ObjectShape object, PreparedPlacement prepared) {
        int margin = (Math.max(object.width(), Math.max(object.height(), object.depth())) / 2) + 1 + prepared.warpMargin();
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
        ObjectPlacement placement = ObjectPlacement.current();
        PreparedPlacement prepared = context.output(IrisStages.Prepare.TRANSFORMS);
        PlacementSettings settings = prepared.settings();
        if (settings.forcePlace() || prepared.mode().has(PlaceTrait.RAW_STAMP)
                || (settings.allowedCollisions().isEmpty() && settings.forbiddenCollisions().isEmpty())) {
            return;
        }
        WorldVolume volume = WorldVolume.of(placement, prepared, context.output(IrisStages.Anchor.HEIGHT).y() + prepared.randomY());
        if (collidesWithForbiddenObject(prepared.terrain(), settings, volume)) {
            context.reject("object-collision");
        }
    }

    private static boolean collidesWithForbiddenObject(PlacementTerrain terrain, PlacementSettings settings, WorldVolume volume) {
        for (int x = volume.minX(); x <= volume.maxX(); x++) {
            for (int y = volume.minY(); y <= volume.maxY(); y++) {
                for (int z = volume.minZ(); z <= volume.maxZ(); z++) {
                    String key = terrain.objectKeyAt(x, y, z);
                    if (key != null && settings.forbiddenCollisions().contains(key) && !settings.allowedCollisions().contains(key)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void warnImplausibleBedrockPlacement(ObjectPlacement placement, PlacementSettings settings, int y, ModuleLog log) {
        String key = placement.object().key();
        String fingerprint = (key == null ? "<null>" : key) + "|" + settings.mode();
        long now = System.currentTimeMillis();
        Long last = IMPLAUSIBLE_BEDROCK_WARNS.get(fingerprint);
        if (last != null && now - last < IMPLAUSIBLE_BEDROCK_WARN_THROTTLE_MS) {
            return;
        }
        IMPLAUSIBLE_BEDROCK_WARNS.put(fingerprint, now);
        log.warn("Implausible object placement rejected: "
                + (key == null ? "<no loadKey>" : key)
                + " resolved anchorY=" + y + " at (" + placement.x() + "," + placement.z() + ") mode=" + settings.mode()
                + " carving=" + settings.carvingSupport()
                + ". Surface-anchored placement should never land on the bedrock row. "
                + "Height sampling returned a bogus value — not configured for floor placement "
                + "(forcePlace=false, fromBottom=false, mode!=FLOATING). Skipping to protect bedrock.");
    }
}

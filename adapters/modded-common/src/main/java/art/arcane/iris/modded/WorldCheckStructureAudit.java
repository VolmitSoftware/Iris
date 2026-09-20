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

package art.arcane.iris.modded;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.nativelib.terrain.NativeBlockPoint;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeStructureInspection;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeStructureInspection.Reference;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeStructureInspection.Start;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeStructureInspection.Found;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeStructureInspection.FootprintAudit;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeStructureInspection.PoiAudit;
import art.arcane.iris.structure.nativegen.NativeStructureGenerationPolicy;
import art.arcane.iris.structure.nativegen.IrisNativeStructureDecision;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntBinaryOperator;

final class WorldCheckStructureAudit {
    static final List<StructureCheck> STRUCTURE_CHECKS = List.of(
            new StructureCheck("stronghold", List.of("minecraft:stronghold"), 256),
            new StructureCheck("trial_chambers", List.of("minecraft:trial_chambers"), 128),
            new StructureCheck("mansion", List.of("minecraft:mansion"), 256),
            new StructureCheck("village", List.of(
                    "minecraft:village_plains",
                    "minecraft:village_desert",
                    "minecraft:village_savanna",
                    "minecraft:village_snowy",
                    "minecraft:village_taiga"), 128),
            new StructureCheck("monument", List.of("minecraft:monument"), 128)
    );

    private WorldCheckStructureAudit() {
    }

    static NativeStructureGate checkNativeStructures(NativeWorld level,
                                                     IrisModdedChunkGenerator generator,
                                                     NativeBlockPoint origin) {
        boolean pass = true;
        int nonVillagePassed = 0;
        boolean villagePass = false;
        PendingVillagePoi pendingPoi = null;
        for (StructureCheck check : STRUCTURE_CHECKS) {
            StructureCheckResult result = checkNativeStructure(level, generator, origin, check);
            if (!result.pass()) {
                pass = false;
            }
            if (check.label().equals("village")) {
                villagePass = result.pass();
                pendingPoi = result.pendingPoi();
            } else if (result.pass()) {
                nonVillagePassed++;
            }
        }
        return new NativeStructureGate(pass, nonVillagePassed, villagePass, pendingPoi);
    }

    private static StructureCheckResult checkNativeStructure(NativeWorld level,
                                                             IrisModdedChunkGenerator generator,
                                                             NativeBlockPoint origin,
                                                             StructureCheck check) {
        NativeStructureInspection inspection = new NativeStructureInspection(level);
        List<Reference> registered = inspection.resolve(check.registryKeys());
        LinkedHashSet<String> registeredKeys = new LinkedHashSet<>();
        for (Reference reference : registered) {
            registeredKeys.add(reference.key());
        }
        boolean registryOk = registered.size() == check.registryKeys().size();
        ModdedIrisLog.info("[worldcheck] {} registry: {}/{} resolved {}", check.label(), registered.size(),
                check.registryKeys().size(), registeredKeys);
        WorldCheckPredicates.qaEvent("structure_registry", check.label(), registryOk,
                "resolved=" + registered.size() + ",expected=" + check.registryKeys().size()
                        + ",keys=" + String.join("|", registeredKeys));
        if (!registryOk) {
            ModdedIrisLog.error("[worldcheck] {} registry resolution failed; expected {}", check.label(), check.registryKeys());
            WorldCheckPredicates.emitSkipped(check, "registry", "structure_reachability", "structure_locate",
                    "structure_start_reference", "structure_footprint", "structure_material",
                    "structure_block_entity");
            return new StructureCheckResult(false, null);
        }

        List<Reference> reachable = new ArrayList<>(registered.size());
        LinkedHashSet<String> reachableKeys = new LinkedHashSet<>();
        for (Reference holder : registered) {
            if (!inspection.reachable(holder)) {
                continue;
            }
            reachable.add(holder);
            if (holder.key() != null) {
                reachableKeys.add(holder.key());
            }
        }
        boolean reachableOk = !reachable.isEmpty();
        ModdedIrisLog.info("[worldcheck] {} biome-reachable through Iris: {}", check.label(), reachableKeys);
        WorldCheckPredicates.qaEvent("structure_reachability", check.label(), reachableOk,
                "reachable=" + reachable.size() + ",registered=" + registered.size()
                        + ",keys=" + String.join("|", reachableKeys));
        if (!reachableOk) {
            ModdedIrisLog.error("[worldcheck] {} cannot generate in any biome produced by this Iris pack", check.label());
            WorldCheckPredicates.emitSkipped(check, "reachability", "structure_locate", "structure_start_reference",
                    "structure_footprint", "structure_material", "structure_block_entity");
            return new StructureCheckResult(false, null);
        }

        long locateStart = System.nanoTime();
        Found found = inspection.find(reachable, origin, check.locateRadius());
        long locateMillis = (System.nanoTime() - locateStart) / 1_000_000L;
        String foundKey = found == null ? null : found.structure().key();
        boolean locateOk = found != null && foundKey != null && reachableKeys.contains(foundKey);
        WorldCheckPredicates.qaEvent("structure_locate", check.label(), locateOk,
                "method=placement_candidates,millis=" + locateMillis + ",radius=" + check.locateRadius()
                        + ",result=" + (foundKey == null ? "none" : foundKey));
        if (found == null) {
            ModdedIrisLog.error("[worldcheck] {} native placement candidates produced no valid start within {} rings after {}ms",
                    check.label(), check.locateRadius(), locateMillis);
            WorldCheckPredicates.emitSkipped(check, "locate", "structure_start_reference", "structure_footprint",
                    "structure_material", "structure_block_entity");
            return new StructureCheckResult(false, null);
        }

        NativeBlockPoint position = found.position();
        ModdedIrisLog.info("[worldcheck] {} generated candidate: {} {} {} in {}ms (radius={}, result={})",
                check.label(), position.x(), position.y(), position.z(), locateMillis,
                check.locateRadius(), foundKey);
        if (!locateOk) {
            ModdedIrisLog.error("[worldcheck] {} candidate scan returned unexpected structure {}", check.label(), foundKey);
            WorldCheckPredicates.emitSkipped(check, "locate", "structure_start_reference", "structure_footprint",
                    "structure_material", "structure_block_entity");
            return new StructureCheckResult(false, null);
        }

        int chunkX = position.x() >> 4;
        int chunkZ = position.z() >> 4;
        NativeStructureInspection.Evidence evidence = inspection.evidence(found);
        Start start = evidence.start();
        boolean validStart = evidence.valid();
        int references = evidence.references();
        boolean startReferenceOk = WorldCheckPredicates.hasNativeStructureEvidence(validStart, references);
        ModdedIrisLog.info("[worldcheck] {} target chunk {},{}: valid start={}, references={}",
                check.label(), chunkX, chunkZ, validStart, references);
        WorldCheckPredicates.qaEvent("structure_start_reference", check.label(), startReferenceOk,
                "chunk=" + chunkX + "," + chunkZ + ",validStart=" + validStart
                        + ",references=" + references);
        if (!startReferenceOk || !validStart) {
            ModdedIrisLog.error("[worldcheck] {} located at chunk {},{} but no resolvable valid start was generated",
                    check.label(), chunkX, chunkZ);
            WorldCheckPredicates.emitSkipped(check, "start_reference", "structure_footprint", "structure_material",
                    "structure_block_entity");
            return new StructureCheckResult(false, null);
        }

        IrisNativeStructureDecision decision = NativeStructureGenerationPolicy.resolve(
                generator.commandEngine(), foundKey,
                start.underground());
        Integer appliedShift = generator.worldCheckStructureShift(foundKey, start.chunkX(), start.chunkZ());
        boolean verticalShiftOk = WorldCheckPredicates.verticalShiftMatches(
                decision.yShift(), appliedShift, start.minY(), start.maxY(),
                level.minHeight(), level.maxHeight());
        WorldCheckPredicates.qaEvent("structure_vertical_shift", check.label(), verticalShiftOk,
                "configured=" + decision.yShift() + ",applied="
                        + (appliedShift == null ? "unrecorded" : appliedShift));
        if (!verticalShiftOk) {
            ModdedIrisLog.error("[worldcheck] {} expected vertical shift {} but generation recorded {}",
                    check.label(), decision.yShift(), appliedShift);
        }

        Engine engine = generator.commandEngine();
        NativeStructureInspection.FootprintOptions options = new NativeStructureInspection.FootprintOptions(
                key -> WorldCheckMaterials.isCharacteristicMaterial(check.label(), foundKey, key),
                check.label().equals("mansion"), check.label().equals("village"),
                ModdedBlockResolution.BLOCKS.get("minecraft:cobblestone"),
                (x, z) -> engine.getHeight(x, z, true) + engine.getMinHeight());
        FootprintAudit footprint = inspection.footprint(start, options);
        boolean footprintOk = footprint.inspectedChunks() > 0
                && footprint.evidenceChunks() == footprint.inspectedChunks()
                && footprint.coveredPieces() == footprint.totalPieces();
        ModdedIrisLog.info("[worldcheck] {} footprint: chunks={}/{} evidence={} pieces={}/{}",
                check.label(), footprint.inspectedChunks(), footprint.availableChunks(),
                footprint.evidenceChunks(), footprint.coveredPieces(), footprint.totalPieces());
        WorldCheckPredicates.qaEvent("structure_footprint", check.label(), footprintOk,
                "inspected=" + footprint.inspectedChunks() + ",available=" + footprint.availableChunks()
                        + ",evidence=" + footprint.evidenceChunks() + ",coveredPieces="
                        + footprint.coveredPieces() + ",totalPieces=" + footprint.totalPieces());

        boolean materialOk = WorldCheckPredicates.hasCharacteristicMaterialEvidence(footprint.characteristicBlocks(),
                footprint.characteristicChunks(), footprint.materialScannedChunks());
        ModdedIrisLog.info("[worldcheck] {} material: blocks={} chunks={}/{}",
                check.label(), footprint.characteristicBlocks(), footprint.characteristicChunks(),
                footprint.materialScannedChunks());
        WorldCheckPredicates.qaEvent("structure_material", check.label(), materialOk,
                "blocks=" + footprint.characteristicBlocks() + ",chunks=" + footprint.characteristicChunks()
                        + ",scanned=" + footprint.materialScannedChunks());

        boolean vegetationOk = true;
        if (check.label().equals("mansion")) {
            boolean overlap = footprint.vegetationBlocks() > 0;
            vegetationOk = WorldCheckPredicates.mansionVegetationPass(footprint.vegetationBlocks());
            ModdedIrisLog.info("[worldcheck] mansion vegetation metric: remaining log/leaf blocks={} columns={} overlap={}",
                    footprint.vegetationBlocks(), footprint.vegetationColumns(), overlap);
            WorldCheckPredicates.qaEvent("mansion_vegetation_metric", check.label(), vegetationOk,
                    "remainingLogsOrLeaves=" + footprint.vegetationBlocks() + ",columns="
                            + footprint.vegetationColumns() + ",overlap=" + overlap);
        }

        boolean foundationOk = true;
        PendingVillagePoi pendingPoi = null;
        if (check.label().equals("village")) {
            foundationOk = WorldCheckPredicates.villageFoundationPass(footprint.foundationGapColumns());
            ModdedIrisLog.info("[worldcheck] village foundation metric: bases={} cobblestone={} columns={} unsupported={}",
                    footprint.foundationBaseColumns(), footprint.foundationBlocks(),
                    footprint.foundationColumns(), footprint.foundationGapColumns());
            WorldCheckPredicates.qaEvent("village_foundation_metric", check.label(), foundationOk,
                    "bases=" + footprint.foundationBaseColumns() + ",cobblestoneBelowBase="
                            + footprint.foundationBlocks() + ",columns="
                            + footprint.foundationColumns() + ",unsupported=" + footprint.foundationGapColumns());
            pendingPoi = new PendingVillagePoi(level, start);
        }

        boolean blockEntityOk = footprint.blockEntityStates() == footprint.blockEntitiesPresent();
        ModdedIrisLog.info("[worldcheck] {} block entities: state blocks={}, present={}, missing={}",
                check.label(), footprint.blockEntityStates(), footprint.blockEntitiesPresent(),
                footprint.blockEntityStates() - footprint.blockEntitiesPresent());
        WorldCheckPredicates.qaEvent("structure_block_entity", check.label(), blockEntityOk,
                "states=" + footprint.blockEntityStates() + ",present=" + footprint.blockEntitiesPresent()
                        + ",missing=" + (footprint.blockEntityStates() - footprint.blockEntitiesPresent()));
        if (!footprintOk) {
            ModdedIrisLog.error("[worldcheck] {} structure footprint is incomplete", check.label());
        }
        if (!materialOk) {
            ModdedIrisLog.error("[worldcheck] {} has no distributed characteristic structure material", check.label());
        }
        if (!blockEntityOk) {
            ModdedIrisLog.error("[worldcheck] {} generated block-entity states without matching block entities", check.label());
        }
        if (!vegetationOk) {
            ModdedIrisLog.error("[worldcheck] mansion vegetation still intersects the generated structure footprint");
        }
        if (!foundationOk) {
            ModdedIrisLog.error("[worldcheck] village has unsupported foundation columns after stilt placement");
        }
        boolean pass = verticalShiftOk && footprintOk && materialOk && blockEntityOk
                && vegetationOk && foundationOk;
        return new StructureCheckResult(pass, pass ? pendingPoi : null);
    }

    static PoiAudit auditStructurePois(NativeWorld level, Start start) {
        return new NativeStructureInspection(level).pois(start);
    }

    record StructureCheck(String label, List<String> registryKeys, int locateRadius) {
    }

    record NativeStructureGate(boolean passBeforePoi, int nonVillagePassed,
                               boolean villagePassBeforePoi, PendingVillagePoi pendingPoi) {
    }

    private record StructureCheckResult(boolean pass, PendingVillagePoi pendingPoi) {
    }

    record PendingVillagePoi(NativeWorld level, Start start) {
    }

}

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

package art.arcane.iris.core.modules;

import art.arcane.iris.builtin.anchor.PlaceTrait;
import art.arcane.iris.builtin.objects.CarvedSpace;
import art.arcane.iris.builtin.objects.Cavern;
import art.arcane.iris.builtin.objects.CavePlacement;
import art.arcane.iris.builtin.objects.ObjectChunk;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.PlaceableObject;
import art.arcane.iris.builtin.objects.PlacementChanges;
import art.arcane.iris.builtin.objects.PlacementSource;
import art.arcane.iris.builtin.objects.PlacementTags;
import art.arcane.iris.builtin.prepare.AxisRotationClamp;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.TreeBlockMaterial;
import art.arcane.iris.engine.mantle.MantleWriter;
import art.arcane.iris.engine.mantle.components.CaveObjectPlacementTransaction;
import art.arcane.iris.engine.mantle.components.GoldenDebugObjectPlacer;
import art.arcane.iris.engine.object.placement.DecayControlPlacer;
import art.arcane.iris.engine.object.placement.IObjectPlacer;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.engine.object.placement.ObjectPlacements;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.prefab.IrisObjectRotation;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.volmlib.util.math.BlockPosition;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterStructurePOI;
import com.volmit.ModuleLog;

import java.util.Optional;
import java.util.function.BiConsumer;

/** The chunk whose mantle {@code iris:objects} is placing into: Iris's mantle writer around it and the engine it belongs to. */
final class IrisObjectChunk implements ObjectChunk {
    private static final double UPSIDE_DOWN_TURN_DEGREES = 180D;
    private static final double QUARTER_TURN_DEGREES = 90D;

    private final Engine engine;
    private final MantleWriter writer;
    private final ModuleLog log;

    IrisObjectChunk(Engine engine, MantleWriter writer, ModuleLog log) {
        this.engine = engine;
        this.writer = writer;
        this.log = log;
    }

    @Override
    public CarvedSpace carved() {
        return new CarvedSpace() {
            @Override
            public boolean isCarved(int x, int y, int z) {
                return writer.isCarved(x, y, z);
            }

            @Override
            public byte[] column(int x, int z, int height) {
                return writer.getCarvedColumn(x, z, height);
            }

            @Override
            public Optional<Cavern> cavernAt(int x, int y, int z) {
                MatterCavern cavern = writer.getDataIfPresent(x, y, z, MatterCavern.class);
                if (cavern == null || !cavern.isCavern()) {
                    return Optional.empty();
                }
                return Optional.of(new Cavern(cavern.isAir(), cavern.getLiquid()));
            }
        };
    }

    @Override
    public boolean goldenDebug(int chunkX, int chunkZ) {
        return GoldenDebugObjectPlacer.isGoldenDebugChunk(chunkX, chunkZ);
    }

    @Override
    public int placeOnSurface(PlaceableObject object, int x, int z, PlacementSource placement, PlacementChanges changes,
                              PlacementTags tags, RNG rng, String goldenTag) {
        IrisObjectPlacement effective = effective(placement, changes);
        IObjectPlacer base = placement instanceof IrisProceduralPlacementView procedural && procedural.plausible()
                ? new DecayControlPlacer(writer) : writer;
        IObjectPlacer placer = goldenTag == null ? base : new GoldenDebugObjectPlacer(base, goldenTag, log);
        IObjectPlacer written = placement instanceof IrisProceduralPlacementView ? placer : writer;
        return ((IrisObject) object).place(x, -1, z, placer, effective, rng, taggedBlocks(written, tags), null, engine.getData());
    }

    @Override
    public CavePlacement placeInCave(PlaceableObject object, int x, int anchorY, int z, PlacementSource placement, PlacementChanges changes,
                                     int minDepthBelowSurface, PlacementTags tags, RNG rng, String goldenTag) {
        IrisObjectPlacement effective = effective(placement, changes);
        IrisObject irisObject = (IrisObject) object;
        IObjectPlacer placer = caveBase(placement, goldenTag);
        CaveObjectPlacementTransaction transaction = new CaveObjectPlacementTransaction(placer, anchorY, minDepthBelowSurface);
        int placeY = anchorY;
        if (effective.getMode().placeMode().has(PlaceTrait.UPSIDE_DOWN)) {
            placeY = Math.max(1, transaction.getCaveCeiling(x, z) - 1 - Math.floorDiv(irisObject.getH(), 2));
        }
        int result = irisObject.place(x, placeY, z, transaction, effective, rng, taggedBlocks(transaction, tags), null, engine.getData());
        if (result < 0) {
            transaction.discard();
            return new CavePlacement(result, CavePlacement.Commit.EMPTY);
        }
        return new CavePlacement(result, commitResult(transaction.commit()));
    }

    @Override
    public int placeUpper(PlaceableObject object, int x, int anchorY, int z, ObjectPlacement placement, PlacementChanges changes,
                          PlacementTags tags, RNG rng) {
        IrisObjectPlacement effective = effective(placement, changes);
        return ((IrisObject) object).place(x, anchorY, z, writer, effective, rng, taggedBlocks(writer, tags), null, engine.getData());
    }

    private IObjectPlacer caveBase(PlacementSource placement, String goldenTag) {
        if (!(placement instanceof IrisProceduralPlacementView procedural)) {
            return writer;
        }
        IObjectPlacer base = procedural.plausible() ? new DecayControlPlacer(writer) : writer;
        return goldenTag == null ? base : new GoldenDebugObjectPlacer(base, goldenTag, log);
    }

    private static CavePlacement.Commit commitResult(CaveObjectPlacementTransaction.CommitResult result) {
        return switch (result) {
            case COMMITTED -> CavePlacement.Commit.COMMITTED;
            case EMPTY -> CavePlacement.Commit.EMPTY;
            case REJECTED_BOUNDS -> CavePlacement.Commit.REJECTED_BOUNDS;
        };
    }

    /** Writes the tags beside each block of the object, into {@code placer}. */
    private static BiConsumer<BlockPosition, PlatformBlockState> taggedBlocks(IObjectPlacer placer, PlacementTags tags) {
        return (position, state) -> {
            if (tags.marker() != null) {
                placer.setData(position.getX(), position.getY(), position.getZ(), tags.marker());
                if (tags.treeMaterial()) {
                    placer.setData(position.getX(), position.getY(), position.getZ(), TreeBlockMaterial.of(state));
                }
            }
            if (tags.buriedTreasure() && B.isStorageChest(state)) {
                placer.setData(position.getX(), position.getY(), position.getZ(), MatterStructurePOI.BURIED_TREASURE);
            }
        };
    }

    /** Iris's placement for {@code placement}, with {@code changes} made to a copy of it. */
    private IrisObjectPlacement effective(PlacementSource placement, PlacementChanges changes) {
        IrisObjectPlacement base = placement instanceof ObjectPlacement settings
                ? ObjectPlacements.host(settings, engine.getData())
                : ((IrisProceduralPlacementView) placement).options();
        return withChanges(base, changes);
    }

    /** {@code base} with {@code changes} made to a copy of it; {@code base} itself when there are none. */
    static IrisObjectPlacement withChanges(IrisObjectPlacement base, PlacementChanges changes) {
        if (changes.isNone()) {
            return base;
        }
        String[] keys = (changes.place() == null ? base.getPlace() : changes.place()).toArray(new String[0]);
        IrisObjectPlacement changed = base.toPlacement(keys);
        if (changes.mode() != null) {
            changed.setMode(changes.mode());
        }
        if (changes.upsideDown() != null && changes.upsideDown()) {
            changed.setRotation(upsideDownRotation());
        }
        if (changes.carvingSupport() != null) {
            changed.setCarvingSupport(changes.carvingSupport());
        }
        if (changes.requireSurfaceSupport() != null) {
            changed.setRequireSurfaceSupport(changes.requireSurfaceSupport());
        }
        if (changes.surfaceSupportBuffer() != null) {
            changed.setSurfaceSupportBuffer(changes.surfaceSupportBuffer());
        }
        if (changes.forcePlace() != null) {
            changed.setForcePlace(changes.forcePlace());
        }
        return changed;
    }

    private static IrisObjectRotation upsideDownRotation() {
        IrisObjectRotation rotation = new IrisObjectRotation();
        rotation.setEnabled(true);
        rotation.setXAxis(AxisRotationClamp.of(true, true, UPSIDE_DOWN_TURN_DEGREES, UPSIDE_DOWN_TURN_DEGREES, QUARTER_TURN_DEGREES));
        rotation.setYAxis(AxisRotationClamp.of(true, false, 0D, 0D, QUARTER_TURN_DEGREES));
        rotation.setZAxis(new AxisRotationClamp());
        return rotation;
    }
}

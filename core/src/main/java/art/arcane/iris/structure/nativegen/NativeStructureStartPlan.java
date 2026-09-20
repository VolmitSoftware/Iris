package art.arcane.iris.structure.nativegen;

import art.arcane.volmlib.nativelib.terrain.structure.StructureTerrainSettings;

import art.arcane.volmlib.nativelib.terrain.structure.JigsawSettings;

import art.arcane.volmlib.nativelib.terrain.structure.StructureStartPlan;

import art.arcane.iris.structure.placement.IrisStructurePlacement;
import art.arcane.iris.structure.placement.StructurePlacementGrid;

public record NativeStructureStartPlan(
        IrisStructurePlacement placement,
        IrisNativeStructure source,
        int chunkX,
        int chunkZ,
        int baseY
) implements StructureStartPlan {
    @Override
    public String structureKey() {
        return source.getStructure();
    }

    @Override
    public JigsawSettings jigsaw() {
        return source.getJigsaw();
    }

    @Override
    public boolean underground() {
        return placement.isUnderground();
    }

    @Override
    public boolean replacesSource() {
        return placement.getNativeSuppression() == NativeStructureSuppression.REPLACE_SOURCE;
    }

    @Override
    public StructureTerrainSettings terrain() {
        return placement.resolvedTerrain();
    }
    @Override
    public long placementIdentity() {
        return StructurePlacementGrid.placementIdentity(placement);
    }

}

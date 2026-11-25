package art.arcane.iris.world.tree;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.placement.StructurePlacementMarker;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.generation.decoration.tree.IrisTree;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TreeDefinitionIndexTest {
    @Test
    public void indexSeparatesOrdinaryAndStructureOwnership() {
        Engine engine = mock(Engine.class);
        IrisDimension dimension = mock(IrisDimension.class);
        IrisRegion region = new IrisRegion();
        IrisBiome biome = new IrisBiome();

        IrisObjectPlacement explicit = new IrisObjectPlacement();
        explicit.getPlace().add("custom/ancient_oak");
        explicit.getTrees().add(new IrisTree());
        region.getObjects().add(explicit);

        when(engine.getDimension()).thenReturn(dimension);
        when(dimension.getAllRegions(engine)).thenReturn(new KList<IrisRegion>().qadd(region));
        when(dimension.getReachableBiomes(engine)).thenReturn(new KList<IrisBiome>().qadd(biome));

        TreeDefinitionIndex index = TreeDefinitionIndex.build(engine);

        assertTrue(index.isTreeMarker("trees/oak/giant@1"));
        assertTrue(index.isTreeMarker("custom/ancient_oak@2"));
        assertFalse(index.isTreeMarker("procedural/trees/towering-oak#0@3"));
        assertFalse(index.isTreeMarker(StructurePlacementMarker.encodeStructure(
                "trees/oak/giant",
                1,
                "village"
        )));
    }
}

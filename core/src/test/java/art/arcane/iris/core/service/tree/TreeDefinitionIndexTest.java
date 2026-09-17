package art.arcane.iris.core.service.tree;

import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.StructurePlacementMarker;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.procedural.IrisProceduralObjects;
import art.arcane.iris.engine.object.procedural.tree.IrisProceduralTree;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.engine.object.placement.IrisTree;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TreeDefinitionIndexTest {
    @Test
    public void indexSeparatesOrdinaryProceduralAndStructureOwnership() {
        Engine engine = mock(Engine.class);
        IrisDimension dimension = mock(IrisDimension.class);
        IrisRegion region = new IrisRegion();
        IrisBiome biome = new IrisBiome();

        IrisObjectPlacement explicit = new IrisObjectPlacement();
        explicit.getPlace().add("custom/ancient_oak");
        explicit.getTrees().add(new IrisTree());
        region.getObjects().add(explicit);

        IrisProceduralTree proceduralTree = new IrisProceduralTree();
        proceduralTree.setName("towering-oak");
        proceduralTree.setVariants(2);
        IrisProceduralObjects proceduralObjects = new IrisProceduralObjects();
        proceduralObjects.getTrees().add(proceduralTree);
        biome.setProceduralObjects(proceduralObjects);

        when(engine.getDimension()).thenReturn(dimension);
        when(dimension.getAllRegions(engine)).thenReturn(new KList<IrisRegion>().qadd(region));
        when(dimension.getReachableBiomes(engine)).thenReturn(new KList<IrisBiome>().qadd(biome));

        TreeDefinitionIndex index = TreeDefinitionIndex.build(engine);

        assertTrue(index.isTreeMarker("trees/oak/giant@1"));
        assertTrue(index.isTreeMarker("custom/ancient_oak@2"));
        assertTrue(index.isTreeMarker("procedural/tree/towering-oak#0@3"));
        assertTrue(index.isTreeMarker("procedural/tree/towering-oak#1@4"));
        assertFalse(index.isTreeMarker("procedural/towering-oak#0@3"));
        assertFalse(index.isTreeMarker("procedural/tree/towering-oak#2@5"));
        assertFalse(index.isTreeMarker(StructurePlacementMarker.encodeStructure(
                "trees/oak/giant",
                1,
                "village"
        )));
    }
}

package art.arcane.iris.core.service.tree;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.builtin.structure.StructurePlacementMarker;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.ModuleResourceFixture;
import art.arcane.iris.core.modules.ModuleSettings;
import art.arcane.iris.engine.framework.EngineGeneration;
import art.arcane.iris.engine.mantle.components.ObjectModule;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectSettings;
import art.arcane.iris.builtin.objects.ProceduralObjectSettings;
import art.arcane.iris.builtin.objects.tree.Tree;
import art.arcane.iris.core.modules.BuiltInPack;
import art.arcane.iris.engine.object.TestSettings;
import org.junit.Test;

import java.util.List;

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
        region.setLoadKey("forest");
        biome.setLoadKey("forest/oaks");

        ObjectPlacement explicit = TestSettings.of(ObjectPlacement.class,
                "{\"place\":[\"custom/ancient_oak\"],\"trees\":[{\"treeTypes\":[\"TREE\"],\"sizes\":[{}]}]}");
        region.setModuleSettings(BuiltInPack.settings(TestSettings.with(new ObjectSettings(), "objects", List.of(explicit))));

        Tree proceduralTree = TestSettings.of(Tree.class, "{\"name\":\"towering-oak\",\"variants\":2}");
        biome.setModuleSettings(BuiltInPack.settings(TestSettings.with(new ProceduralObjectSettings(), "trees", List.of(proceduralTree))));

        when(engine.getDimension()).thenReturn(dimension);
        when(dimension.getModuleSettings()).thenReturn(ModuleSettings.NONE);
        IrisData data = mock(IrisData.class);
        when(engine.getData()).thenReturn(data);
        ModuleResourceFixture.serve(data);
        ModuleResourceFixture.serveModules(data, new ObjectModule());
        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));
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

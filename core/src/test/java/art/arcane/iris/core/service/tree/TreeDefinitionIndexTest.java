package art.arcane.iris.core.service.tree;

import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.core.modules.IrisPackResources;
import art.arcane.iris.core.modules.PlacementMarkers;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.procedural.GameLikeBlocks;
import art.arcane.iris.engine.procedural.ProceduralObjectCatalog;
import art.arcane.iris.engine.procedural.part.ProceduralObject;
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
import art.arcane.iris.core.modules.BuiltInPack;
import art.arcane.iris.engine.object.TestSettings;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class TreeDefinitionIndexTest {
    private static final String TWO_VARIANT_TREE = "{\"variants\":2,\"materials\":[{\"role\":\"wood\",\"block\":\"minecraft:oak_log\"}],"
            + "\"parts\":[{\"stem\":{\"length\":4}}]}";

    @After
    public void unbindPlatform() {
        UsableBlockPlatform.unbind();
    }

    @Test
    public void treesAreTheTreesFolderTreePlacementsAndProceduralTreesVariantByVariant() {
        UsableBlockPlatform.bind(new GameLikeBlocks().registries());
        Engine engine = mock(Engine.class);
        IrisDimension dimension = mock(IrisDimension.class);
        IrisRegion region = new IrisRegion();
        IrisBiome biome = new IrisBiome();
        region.setLoadKey("forest");
        biome.setLoadKey("forest/oaks");

        ObjectPlacement explicit = TestSettings.of(ObjectPlacement.class,
                "{\"place\":[\"custom/ancient_oak\"],\"trees\":[{\"treeTypes\":[\"TREE\"],\"sizes\":[{}]}]}");
        region.setModuleSettings(BuiltInPack.settings(TestSettings.with(new ObjectSettings(), "objects", List.of(explicit))));

        ObjectPlacement proceduralTrees = TestSettings.of(ObjectPlacement.class,
                "{\"place\":[\"procedural/trees/towering-oak\",\"procedural/rocks/boulder\"]}");
        biome.setModuleSettings(BuiltInPack.settings(TestSettings.with(new ObjectSettings(), "objects", List.of(proceduralTrees))));

        when(engine.getDimension()).thenReturn(dimension);
        when(dimension.getModuleSettings()).thenReturn(ModuleSettings.NONE);
        IrisData data = mock(IrisData.class);
        when(engine.getData()).thenReturn(data);
        ModuleResourceFixture.serve(data)
                .resource(ProceduralObjectCatalog.DEFINITIONS, "trees/towering-oak", TestSettings.of(ProceduralObject.class, TWO_VARIANT_TREE))
                .resource(ProceduralObjectCatalog.DEFINITIONS, "rocks/boulder", TestSettings.of(ProceduralObject.class, TWO_VARIANT_TREE));
        IrisPackResources host = hostResources();
        when(data.getPackResources().blocks()).thenReturn(host.blocks());
        when(data.getPackResources().objects()).thenReturn(host.objects());
        ModuleResourceFixture.serveModules(data, new ObjectModule());
        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));
        when(dimension.getAllRegions(engine)).thenReturn(new KList<IrisRegion>().qadd(region));
        when(dimension.getReachableBiomes(engine)).thenReturn(new KList<IrisBiome>().qadd(biome));

        TreeDefinitionIndex index = TreeDefinitionIndex.build(engine);

        assertTrue(index.isTreeMarker("trees/oak/giant@1"));
        assertTrue(index.isTreeMarker("custom/ancient_oak@2"));
        assertTrue(index.isTreeMarker("procedural/trees/towering-oak#0@3"));
        assertTrue(index.isTreeMarker("procedural/trees/towering-oak#1@4"));
        assertFalse(index.isTreeMarker("procedural/trees/towering-oak#2@5"));
        assertFalse(index.isTreeMarker("procedural/rocks/boulder#0@6"));
        assertFalse(index.isTreeMarker(PlacementMarkers.encodeStructure(
                "trees/oak/giant",
                1,
                "village"
        )));
    }

    private static IrisPackResources hostResources() {
        IrisData data = mock(IrisData.class, withSettings().stubOnly().defaultAnswer(CALLS_REAL_METHODS));
        doReturn(new File("host")).when(data).getDataFolder();
        doReturn(mock(ResourceLoader.class)).when(data).getBlockLoader();
        doReturn(null).when(data).getEngine();
        IrisPackResources resources = new IrisPackResources(data);
        doReturn(resources).when(data).getPackResources();
        return resources;
    }
}

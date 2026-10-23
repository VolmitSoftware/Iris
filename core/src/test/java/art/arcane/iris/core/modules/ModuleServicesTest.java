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

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineGeneration;
import art.arcane.iris.engine.framework.SeedManager;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleLog;
import com.volmit.ModuleSetup;
import com.volmit.host.LoadedPack;
import com.volmit.host.ModuleHost;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.service.LocateResult;
import com.volmit.service.ObjectsAt;
import com.volmit.service.StructureLocator;
import com.volmit.world.BlockPosition;
import com.volmit.world.PackWorld;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What Iris core asks modules through services: every module's objects, and structures through the module that knows
 * them, whose heights above the dimension's bottom come back as the world's Y.
 */
public class ModuleServicesTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final List<String> searches = new ArrayList<>();
    private final List<String> logOwners = new ArrayList<>();
    private LoadedPack pack;
    private IrisData data;
    private Engine engine;

    /** Places {@code object} in every chunk and finds structure {@code structure} at a fixed spot. */
    public final class ServingModule implements IrisModule {
        private final String id;
        private final String object;
        private final String structure;

        ServingModule(String id, String object, String structure) {
            this.id = id;
            this.object = object;
            this.structure = structure;
        }

        @Override
        public ModuleInfo info() {
            return ModuleInfo.of(id, "Serving " + id, "1");
        }

        @Override
        public void setup(ModuleSetup setup) {
            ModuleLog registeredLog = setup.log();
            var registry = setup.registry();
            registry.registerService(ObjectsAt.class, new ObjectsAt() {
                @Override
                public Set<ResourceId> objectsAt(PackWorld world, int chunkX, int chunkZ) {
                    logOwners.add(id + (world.log() == registeredLog ? " has its own log" : " has another module's log"));
                    return Set.of(world.resources().idOf(object + "/" + chunkX + "/" + chunkZ));
                }

                @Override
                public PlacedBy placedBy(PackWorld world, ResourceId placedObject) {
                    return new PlacedBy(Set.of(world.resources().idOf(object + "-biome")), Set.of(world.resources().idOf(id.replace(':', '-') + "-region")));
                }
            });
            registry.registerService(StructureLocator.class, new StructureLocator() {
                @Override
                public Set<ResourceId> structures(PackResources resources) {
                    return Set.of(resources.idOf(structure), resources.idOf(structure + "/unplaced"));
                }

                @Override
                public Set<ResourceId> locatable(PackWorld world) {
                    return Set.of(world.resources().idOf(structure));
                }

                @Override
                public LocateResult locate(PackWorld world, ResourceId located, int fromX, int fromZ, int maxRadiusChunks) {
                    searches.add(id + " " + located.path() + " " + fromX + " " + fromZ + " " + maxRadiusChunks);
                    return LocateResult.found(new BlockPosition(fromX + 16, 70, fromZ));
                }
            });
        }
    }

    @Before
    public void servePack() throws IOException {
        pack = LoadedPack.load(folder.newFolder("pack").toPath(), ModuleHost.of(List.of(
                        new ServingModule("test:zeta", "rock", "castle"),
                        new ServingModule("test:alpha", "tree", "tower"),
                        new ServingModule("test:beta", "bush", "castle")))
                .copyingJarsTo(folder.newFolder("copies").toPath()));
        data = mock(IrisData.class);
        PackModuleSet modules = mock(PackModuleSet.class);
        when(modules.pack()).thenReturn(pack);
        when(data.getModules()).thenReturn(modules);
        when(data.getDataFolder()).thenReturn(folder.getRoot());
        PackResourceFixture.holdNoObjectsBiomesOrRegions(data);
        IrisPackResources resources = new IrisPackResources(data, List::of);
        when(data.getPackResources()).thenReturn(resources);
        engine = mock(Engine.class);
        SeedManager seeds = mock(SeedManager.class);
        when(engine.getData()).thenReturn(data);
        when(engine.getSeedManager()).thenReturn(seeds);
        when(engine.getMinHeight()).thenReturn(-64);
        EngineGeneration generation = new EngineGeneration(engine);
        when(engine.getGeneration()).thenReturn(generation);
    }

    @After
    public void closePack() {
        pack.close();
    }

    @Test
    public void objectsAtCombinesEveryModulesObjectsInModuleOrder() {
        assertEquals(List.of("tree/3/-4", "bush/3/-4", "rock/3/-4"),List.copyOf(ModuleServices.objectsAt(engine, 3, -4)));
    }

    @Test
    public void everyModuleIsAskedWithAWorldCarryingItsOwnLog() {
        ModuleServices.objectsAt(engine, 0, 0);

        assertEquals(List.of("test:alpha has its own log", "test:beta has its own log", "test:zeta has its own log"), logOwners);
    }

    @Test
    public void objectPlacersCombineTheBiomesAndRegionsOfEveryModule() {
        ModuleServices.ObjectPlacers placers = ModuleServices.objectPlacers(engine, "trees/oak");

        assertEquals(Set.of("rock-biome", "tree-biome", "bush-biome"), placers.biomes());
        assertEquals(Set.of("test-zeta-region", "test-alpha-region", "test-beta-region"), placers.regions());
    }

    @Test
    public void structureListsCombineEveryModule() {
        assertEquals(Set.of("tower", "castle"), ModuleServices.locatableStructures(engine));
        assertEquals(Set.of("tower", "tower/unplaced", "castle", "castle/unplaced"), ModuleServices.structureKeys(data));
    }

    @Test
    public void aStructureIsLocatedByTheFirstModuleThatCanLocateItIgnoringCase() {
        assertTrue(ModuleServices.canLocateStructure(engine, "CASTLE"));
        assertFalse(ModuleServices.canLocateStructure(engine, "castle/unplaced"));
        assertFalse(ModuleServices.canLocateStructure(engine, " "));

        ModuleServices.StructureSearch result = ModuleServices.locateStructure(engine, "Castle", 100, -200, 64);

        assertEquals(new ModuleServices.StructureSearch(ModuleServices.StructureSearch.Outcome.FOUND, 116, 70 - 64, -200), result);
        assertEquals("the key is read as the pack's lower-case id", List.of("test:beta castle 100 -200 64"), searches);
    }

    @Test
    public void aStructureNoModuleCanLocateIsNotFoundWithoutSearching() {
        assertEquals(ModuleServices.StructureSearch.Outcome.NOT_FOUND,
                ModuleServices.locateStructure(engine, "castle/unplaced", 0, 0, 64).outcome());
        assertEquals(List.of(), searches);
    }
}

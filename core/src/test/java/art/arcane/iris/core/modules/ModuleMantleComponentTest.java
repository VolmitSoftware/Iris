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

import art.arcane.iris.builtin.structure.StructureStages;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineGeneration;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.flag.ReservedFlag;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.host.LoadedPack;
import com.volmit.stage.Stage;
import com.volmit.stage.StageGraph;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Every mantle stage is a mantle pass of its own, in the stage graph's order. */
public class ModuleMantleComponentTest {
    private static final int MARK_REACH = 5;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void everyMantleStageIsAPassOfItsOwnWithItsFlagAndThePacksReach() throws IOException {
        IrisModule marks = new IrisModule() {
            public ModuleInfo info() { return ModuleInfo.of("test:marks", "Marks", "1"); }
            public void install(StageGraph graph) {
                graph.define(Stage.mantle("test:marks/mark")).requires(StructureStages.PLACE).reach(settings -> MARK_REACH).run(context -> { });
            }
        };
        IrisData data = mock(IrisData.class);
        Engine engine = mock(Engine.class);
        when(engine.getData()).thenReturn(data);
        when(engine.getDimension()).thenReturn(new IrisDimension());
        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));

        try (LoadedPack ignored = BuiltInPack.serve(data, folder, List.of(marks))) {
            List<ModuleMantleComponent> components = ModuleMantleComponent.forPack(engine, Set.of(ReservedFlag.CARVED));

            assertEquals(List.of("iris:carve/caves", "iris:objects/place", "iris:floating/objects", "iris:structure/place", "test:marks/mark"),
                    components.stream().map(component -> component.stage().stage().id().toString()).toList());
            assertEquals(List.of(ReservedFlag.CARVED, ReservedFlag.OBJECT, ReservedFlag.FLOATING_OBJECT, ReservedFlag.JIGSAW, MantleFlag.of(MantleFlag.MIN_ORDINAL)),
                    components.stream().map(ModuleMantleComponent::getFlag).toList());
            assertEquals(List.of(0, 1, 2, 3, 4), components.stream().map(ModuleMantleComponent::getPriority).toList());
            assertEquals("a pack without objects or structures: caves 1, floating objects at least 16", List.of(1, 0, 16, 0, MARK_REACH),
                    components.stream().map(ModuleMantleComponent::getRadius).toList());
            assertEquals("disabledComponents: [CARVED] turns the cave pass off", List.of(false, true, true, true, true),
                    components.stream().map(ModuleMantleComponent::isEnabled).toList());
        }
    }
}

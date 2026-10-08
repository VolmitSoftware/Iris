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
import com.volmit.host.LoadedPack;
import com.volmit.host.ResolvedGraph;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class BuiltInPlacementGraphTest {
    private static final List<String> EXPECTED_ORDER = List.of(
            "iris:prepare/slope", "iris:prepare/transforms", "iris:anchor/height", "iris:validate/surface",
            "iris:validate/limits", "iris:validate/structures", "iris:validate/collisions", "iris:anchor/final",
            "iris:bore/clear", "iris:blocks/write", "iris:stilt/layers", "iris:blocks/markers",
            "iris:blocks/complete", "iris:stilt/fill", "iris:vacuum/terrain", "iris:snow/cover");

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void writesTheObjectAndFindsItsStiltLayersInOneBlockPass() throws IOException {
        try (LoadedPack pack = BuiltInPack.serve(mock(IrisData.class), folder, List.of())) {
            ResolvedGraph graph = pack.placement();

            assertEquals(graph.describe(), EXPECTED_ORDER, graph.stages().stream().map(stage -> stage.stage().id().toString()).toList());
            assertEquals(graph.describe(), 1, graph.blockPasses().size());
        }
    }
}

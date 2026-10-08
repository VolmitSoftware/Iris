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
import art.arcane.iris.builtin.deposit.DepositSettings;
import com.volmit.FeatureAttachment.Target;
import com.volmit.host.LoadedPack;
import com.volmit.host.ResolvedGraph;
import com.volmit.host.schema.FeatureSchema;
import com.volmit.host.schema.ValueKind;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/** Every world generates through the built-in modules' stages, in the order the old overworld mode ran them. */
public class BuiltInGenerationGraphTest {
    private static final List<String> EXPECTED_CHUNK_ORDER = List.of(
            "iris:biome/write", "iris:terrain/shape", "iris:carve/apply", "iris:post/smooth", "iris:floating/solids",
            "iris:deposit/ores", "iris:matter/insert", "iris:decorant/decorate", "iris:floating/decorate",
            "iris:perfection/surface", "iris:custom/blocks");

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void chunkStagesRunInTheOverworldOrder() throws IOException {
        try (LoadedPack pack = BuiltInPack.serve(mock(IrisData.class), folder, List.of())) {
            ResolvedGraph chunk = pack.chunk();

            assertEquals(chunk.describe(), EXPECTED_CHUNK_ORDER, ids(chunk));
        }
    }

    @Test
    public void cavesArePlannedInTheMantleOneBlockIntoEachNeighbor() throws IOException {
        try (LoadedPack pack = BuiltInPack.serve(mock(IrisData.class), folder, List.of())) {
            ResolvedGraph mantle = pack.mantle();

            assertEquals(List.of("iris:carve/caves"), ids(mantle));
            assertEquals(1, mantle.reach().blocks());
        }
    }

    @Test
    public void depositsAreSettingsOfEveryLevelWrittenAsTheirOldList() throws IOException {
        try (LoadedPack pack = BuiltInPack.serve(mock(IrisData.class), folder, List.of())) {
            for (Target level : List.of(Target.DIMENSION, Target.REGION, Target.BIOME)) {
                FeatureSchema deposits = pack.schema().fields(level).stream()
                        .filter(feature -> feature.settings().name().equals(DepositSettings.class.getName()))
                        .findFirst().orElseThrow();
                assertEquals("deposits", deposits.alias().orElseThrow());
                ValueKind generators = deposits.shorthand().orElseThrow().kind();
                assertTrue(generators.toString(), generators instanceof ValueKind.ListKind list
                        && list.element() instanceof ValueKind.HostKind host
                        && host.type().equals("art.arcane.iris.engine.object.terrain.deposit.IrisDepositGenerator"));
            }
        }
    }

    private static List<String> ids(ResolvedGraph graph) {
        return graph.stages().stream().map(stage -> stage.stage().id().toString()).toList();
    }
}

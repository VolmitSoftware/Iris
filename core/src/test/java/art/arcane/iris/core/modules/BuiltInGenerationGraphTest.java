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

import art.arcane.iris.builtin.IrisStages;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.builtin.deposit.DepositGenerator;
import art.arcane.iris.builtin.deposit.DepositSettings;
import com.volmit.FeatureAttachment.Target;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.config.PackSettings;
import com.volmit.host.LoadedPack;
import com.volmit.host.ResolvedGraph;
import com.volmit.host.schema.FeatureSchema;
import com.volmit.host.schema.ObjectType;
import com.volmit.host.schema.SchemaOption;
import com.volmit.host.schema.ValueKind;
import com.volmit.stage.MantleStage;
import com.volmit.stage.Stage;
import com.volmit.stage.StageGraph;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/** Every world generates through the built-in modules' stages, in the order the old overworld mode ran them. */
public class BuiltInGenerationGraphTest {
    private static final List<String> EXPECTED_CHUNK_ORDER = List.of(
            "iris:biome/write", "iris:terrain/shape", "iris:carve/apply", "iris:post/smooth", "iris:floating/solids",
            "iris:deposit/ores", "iris:matter/insert", "iris:decorant/decorate", "iris:floating/decorate",
            "iris:perfection/surface", "iris:custom/blocks");
    private static final List<String> EXPECTED_MANTLE_ORDER = List.of(
            "iris:carve/caves", "iris:objects/place", "iris:floating/objects", "iris:structure/place");

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
    public void mantleStagesRunCavesObjectsFloatingObjectsThenStructures() throws IOException {
        try (LoadedPack pack = BuiltInPack.serve(mock(IrisData.class), folder, List.of())) {
            ResolvedGraph mantle = pack.mantle();

            assertEquals(mantle.describe(), EXPECTED_MANTLE_ORDER, ids(mantle));
            assertEquals("blended cave walls reach one block into each neighbor", 1,
                    mantle.stages().getFirst().reachBlocks(mock(PackSettings.class)));
        }
    }

    @Test
    public void aPackMantleStageCanOrderItselfAfterIrisStructures() throws IOException {
        MantleStage<Void> mark = Stage.mantle("test:marks/mark");
        IrisModule marks = new IrisModule() {
            public ModuleInfo info() { return ModuleInfo.of("test:marks", "Marks", "1"); }
            public void install(StageGraph graph) { graph.define(mark).requires(IrisStages.Structure.PLACE).run(context -> { }); }
        };
        try (LoadedPack pack = BuiltInPack.serve(mock(IrisData.class), folder, List.of(marks))) {
            List<String> order = ids(pack.mantle());

            assertEquals(order.toString(), order.size() - 1, order.indexOf("test:marks/mark"));
        }
    }

    @Test
    public void depositsAreSettingsOfEveryLevelWrittenAsTheirIdWithGenerators() throws IOException {
        try (LoadedPack pack = BuiltInPack.serve(mock(IrisData.class), folder, List.of())) {
            for (Target level : List.of(Target.DIMENSION, Target.REGION, Target.BIOME)) {
                FeatureSchema deposits = pack.schema().fields(level).stream()
                        .filter(feature -> feature.settings().name().equals(DepositSettings.class.getName()))
                        .findFirst().orElseThrow();
                assertEquals("iris:deposit", deposits.key());
                SchemaOption option = deposits.settings().options().getFirst();
                assertEquals("generators", option.name());
                ValueKind generators = option.kind();
                assertEquals("generators are a module class of IrisApi types, not an Iris class",
                        new ValueKind.ListKind(new ValueKind.ObjectKind(DepositGenerator.class.getName()), false), generators);
            }
        }
    }

    @Test
    public void depositGeneratorsKeepTheMinimumListLengthsTheOldClassHad() throws IOException {
        try (LoadedPack pack = BuiltInPack.serve(mock(IrisData.class), folder, List.of())) {
            ObjectType generator = pack.schema().type(DepositGenerator.class.getName());

            for (String list : List.of("palette", "replaceableBlocks", "includedBiomes", "excludedBiomes")) {
                SchemaOption option = generator.options().stream().filter(candidate -> candidate.name().equals(list)).findFirst().orElseThrow();
                assertEquals(list, OptionalInt.of(1), option.minItems());
            }
        }
    }

    private static List<String> ids(ResolvedGraph graph) {
        return graph.stages().stream().map(stage -> stage.stage().id().toString()).toList();
    }
}

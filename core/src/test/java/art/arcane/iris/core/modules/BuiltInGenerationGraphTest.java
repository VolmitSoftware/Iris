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
import art.arcane.iris.builtin.decorant.DecorantSettings;
import art.arcane.iris.builtin.decorant.Decorator;
import art.arcane.iris.builtin.deposit.DepositGenerator;
import art.arcane.iris.modules.deposit.DepositSettings;
import art.arcane.iris.builtin.structure.StructureSettings;
import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.config.Feature;
import com.volmit.config.PackSettings;
import com.volmit.host.LoadedPack;
import com.volmit.host.ModuleLoadException;
import com.volmit.host.ResolvedGraph;
import com.volmit.host.schema.FeatureSchema;
import com.volmit.host.schema.ObjectType;
import com.volmit.host.schema.SchemaOption;
import com.volmit.host.schema.ValueKind;
import com.volmit.stage.MantleStage;
import com.volmit.stage.Stage;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
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
        MantleStage mark = Stage.mantle("test:marks/mark");
        IrisModule marks = new IrisModule() {
            public ModuleInfo info() { return ModuleInfo.of("test:marks", "Marks", "1"); }
            public void setup(ModuleSetup setup) { setup.stages().define(mark).requires(IrisStages.Structure.PLACE).run(context -> { }); }
        };
        try (LoadedPack pack = BuiltInPack.serve(mock(IrisData.class), folder, List.of(marks))) {
            List<String> order = ids(pack.mantle());

            assertEquals(order.toString(), order.size() - 1, order.indexOf("test:marks/mark"));
        }
    }

    @Test
    public void depositsAreTheListOfGeneratorsOfEveryLevelWrittenUnderDeposits() throws IOException {
        try (LoadedPack pack = BuiltInPack.serve(mock(IrisData.class), folder, List.of())) {
            for (Level level : List.of(Level.DIMENSION, Level.REGION, Level.BIOME)) {
                FeatureSchema deposits = pack.schema().fields(level).stream()
                        .filter(feature -> feature.settings().name().equals(DepositSettings.class.getName()))
                        .findFirst().orElseThrow();
                assertEquals("deposits", deposits.key());
                assertEquals(Optional.of("generators"), deposits.root());
                SchemaOption option = deposits.rootOption().orElseThrow();
                assertEquals("generators", option.name());
                ValueKind generators = option.kind();
                assertEquals("generators are a module class of IrisApi types, not an Iris class",
                        new ValueKind.ListKind(new ValueKind.ObjectKind(DepositGenerator.class.getName()), false), generators);
            }
        }
    }

    @Test
    public void importedStructuresAreAnObjectOfOptionsOfTheDimension() throws IOException {
        try (LoadedPack pack = BuiltInPack.serve(mock(IrisData.class), folder, List.of())) {
            FeatureSchema structures = pack.schema().fields(Level.DIMENSION).stream()
                    .filter(feature -> feature.settings().name().equals(StructureSettings.class.getName()))
                    .findFirst().orElseThrow();

            assertEquals("importedStructures", structures.key());
            assertEquals(Optional.empty(), structures.root());
            assertEquals(List.of("disabled", "disabledExact", "undergroundYShift", "datapackOverrides", "frequencyOverrides", "adjustments"),
                    structures.settings().options().stream().map(SchemaOption::name).toList());
        }
    }

    @Test
    public void theBuiltInKeysAreUniquePerTargetAndNoFieldOfIris() throws IOException {
        try (LoadedPack pack = BuiltInPack.serve(mock(IrisData.class), folder, List.of())) {
            for (Level target : Level.values()) {
                List<String> keys = pack.schema().fields(target).stream().map(FeatureSchema::key).toList();
                assertEquals(target + " " + keys, keys.size(), keys.stream().distinct().count());
                assertTrue(target + " " + keys, keys.stream().noneMatch(ModuleTargets.reservedFields().get(target)::contains));
            }
        }
    }

    @Test
    public void aModuleCannotTakeTheKeyOfABuiltInOrAFieldOfIris() {
        ModuleLoadException deposits = assertThrows(ModuleLoadException.class,
                () -> BuiltInPack.serve(mock(IrisData.class), folder, List.of(new RivalModule(TakesDeposits.class))));
        assertEquals("region key \"deposits\" is claimed by iris:deposit (host classpath) and test:rival/deposits (host classpath);"
                + " give one of the settings another key in its @Feature", deposits.getMessage());

        ModuleLoadException field = assertThrows(ModuleLoadException.class,
                () -> BuiltInPack.serve(mock(IrisData.class), folder, List.of(new RivalModule(TakesAHostField.class))));
        assertEquals("biome key \"generators\" is claimed by Iris and test:rival/generators (host classpath);"
                + " give one of the settings another key in its @Feature", field.getMessage());
    }

    @Feature(id = "test:rival/deposits", name = "Rival deposits", key = "deposits", targets = Level.REGION)
    public static final class TakesDeposits implements IrisFeature {
    }

    @Feature(id = "test:rival/generators", name = "Rival generators", key = "generators", targets = Level.BIOME)
    public static final class TakesAHostField implements IrisFeature {
    }

    private static final class RivalModule implements IrisModule {
        private final Class<? extends IrisFeature> settings;

        RivalModule(Class<? extends IrisFeature> settings) {
            this.settings = settings;
        }

        public ModuleInfo info() {
            return ModuleInfo.of("test:rival", "Rival", "1");
        }

        public void setup(ModuleSetup setup) {
            setup.registry().registerSettings(settings);
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

    @Test
    public void decoratorsAreTheBiomesListOfDecoratorsWrittenUnderDecorators() throws IOException {
        try (LoadedPack pack = BuiltInPack.serve(mock(IrisData.class), folder, List.of())) {
            for (Level level : List.of(Level.DIMENSION, Level.REGION)) {
                assertTrue(level + " has no decorators", pack.schema().fields(level).stream()
                        .noneMatch(feature -> feature.settings().name().equals(DecorantSettings.class.getName())));
            }
            FeatureSchema decorant = pack.schema().fields(Level.BIOME).stream()
                    .filter(feature -> feature.settings().name().equals(DecorantSettings.class.getName()))
                    .findFirst().orElseThrow();
            assertEquals("decorators", decorant.key());
            assertEquals(Optional.of("decorators"), decorant.root());
            SchemaOption option = decorant.rootOption().orElseThrow();
            assertEquals("decorators", option.name());
            assertEquals("decorators are a module class of IrisApi types, not an Iris class",
                    new ValueKind.ListKind(new ValueKind.ObjectKind(Decorator.class.getName()), false), option.kind());
            assertEquals(OptionalInt.of(1), option.minItems());
        }
    }

    @Test
    public void decoratorsKeepTheMinimumListLengthsTheOldClassHad() throws IOException {
        try (LoadedPack pack = BuiltInPack.serve(mock(IrisData.class), folder, List.of())) {
            ObjectType decorator = pack.schema().type(Decorator.class.getName());

            for (String list : List.of("whitelist", "blacklist", "palette", "topPalette")) {
                SchemaOption option = decorator.options().stream().filter(candidate -> candidate.name().equals(list)).findFirst().orElseThrow();
                assertEquals(list, OptionalInt.of(1), option.minItems());
            }
        }
    }

    private static List<String> ids(ResolvedGraph graph) {
        return graph.stages().stream().map(stage -> stage.stage().id().toString()).toList();
    }
}

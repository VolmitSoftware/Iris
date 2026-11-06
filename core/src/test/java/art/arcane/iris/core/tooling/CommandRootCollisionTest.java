package art.arcane.iris.core.tooling;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.ModuleResourceFixture;
import art.arcane.iris.core.modules.StructureModelModule;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.tooling.Command;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CommandRootCollisionTest {
    private static IrisModule registering(String id, Command root) {
        return new IrisModule() {
            @Override
            public ModuleInfo info() {
                return ModuleInfo.of(id, id, "1");
            }

            @Override
            public void setup(ModuleSetup setup) {
                setup.tooling().registerCommand(root);
            }
        };
    }

    @Test
    public void aModuleCannotTakeTheRootNameOfTheStructureModule() {
        var collision = assertThrows(IllegalArgumentException.class, () -> ModuleResourceFixture.catalogOf(
                new StructureModelModule(), registering("test:rival", Command.named("structure"))));

        assertTrue(collision.getMessage(), collision.getMessage().contains("Command root structure is registered by module"));
        assertTrue(collision.getMessage(), collision.getMessage().contains("iris:structure-model"));
        assertTrue(collision.getMessage(), collision.getMessage().contains("test:rival"));
    }

    @Test
    public void aModuleCannotTakeAnAliasOfTheStructureModule() {
        var collision = assertThrows(IllegalArgumentException.class, () -> ModuleResourceFixture.catalogOf(
                registering("test:rival", Command.named("shapes").alias("str")), new StructureModelModule()));

        assertTrue(collision.getMessage(), collision.getMessage().contains("Command root str is registered by module"));
        assertTrue(collision.getMessage(), collision.getMessage().contains("test:rival"));
        assertTrue(collision.getMessage(), collision.getMessage().contains("iris:structure-model"));
    }

    @Test
    public void aPackWhoseModulesCollideAnswersEveryLineWithTheReason() {
        IrisData data = mock(IrisData.class);
        when(data.getDataFolder()).thenReturn(new File("clashing"));
        when(data.getModules()).thenThrow(new IllegalArgumentException("Command root structure is registered by module "
                + "iris:structure-model already, and module test:rival registers it too"));
        ServedPack clashing = new IrisServedPack(data);
        RecordingSender sender = new RecordingSender();
        sender.studio = Optional.of(clashing);
        ToolingCommands dispatcher = new ToolingCommands(new PackDirectory() {
            @Override
            public Optional<ServedPack> find(String name) {
                return Optional.empty();
            }

            @Override
            public List<String> names() {
                return List.of();
            }
        }, Runnable::run, Optional::empty);

        assertTrue(dispatcher.execute(sender, List.of("structure", "list")));

        assertEquals(1, sender.errors.size());
        assertTrue(sender.errors.get(0), sender.errors.get(0).startsWith("The modules of pack clashing did not load: Command root structure"));
        assertTrue(sender.errors.get(0), sender.errors.get(0).contains("test:rival"));
    }
}

package art.arcane.iris.modded.command;

import art.arcane.iris.core.localization.IrisLanguage;
import art.arcane.iris.core.localization.PackDownloadMessages;
import art.arcane.iris.core.lifecycle.LifecycleOperationCoordinator;
import art.arcane.iris.core.pack.PackDownloader;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.Bootstrap;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class IrisModdedCommandParityTest {
    @Test
    public void registersPluginParityWhatCommandsAndAliases() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();

        IrisModdedCommands.register(dispatcher);

        CommandNode<CommandSourceStack> iris = child(dispatcher.getRoot(), "iris");
        CommandNode<CommandSourceStack> what = child(iris, "what");
        child(what, "here");
        child(what, "biome");
        child(what, "region");
        child(what, "block");
        child(what, "hand");
        child(what, "markers");
        child(iris, "dust");
        child(iris, "d");
        child(iris, "create");
        child(iris, "c");
        child(iris, "teleport");
        child(iris, "tp");
        child(iris, "height");
        child(iris, "worlds");
        child(iris, "accesslist");
        child(child(iris, "goto"), "unregistered");

        CommandNode<CommandSourceStack> edit = child(iris, "edit");
        child(edit, "b");
        child(edit, "r");
        child(edit, "d");

        CommandNode<CommandSourceStack> download = child(iris, "download");
        CommandNode<CommandSourceStack> source = child(download, "source");
        assertTrue(source.getChildren().isEmpty());

        assertSame(iris, child(dispatcher.getRoot(), "ir").getRedirect());
        assertSame(iris, child(dispatcher.getRoot(), "irs").getRedirect());
    }

    @Test
    public void downloadRequestAcceptsOnlyBuiltInsAndZipLinks() {
        IrisModdedCommands.DownloadRequest overworld = IrisModdedCommands.parseDownloadRequest("pack=overworld");
        IrisModdedCommands.DownloadRequest underworld = IrisModdedCommands.parseDownloadRequest("pack=UNDERWORLD");
        IrisModdedCommands.DownloadRequest link = IrisModdedCommands.parseDownloadRequest(
                "link=https://packs.example.test/custom.zip?token=a=b"
        );

        assertNotNull(overworld);
        assertEquals("overworld", overworld.pack());
        assertNotNull(underworld);
        assertEquals("underworld", underworld.pack());
        assertNotNull(link);
        assertEquals("https://packs.example.test/custom.zip?token=a=b", link.url());
        String displayTarget = IrisModdedCommands.downloadDisplayTarget(link);
        assertEquals(IrisLanguage.plain(PackDownloadMessages.PROGRESS_SOURCE_REMOTE), displayTarget);
        assertFalse(displayTarget.contains("token"));
        assertNull(IrisModdedCommands.parseDownloadRequest("overworld"));
        assertNull(IrisModdedCommands.parseDownloadRequest("pack=custom"));
        assertNull(IrisModdedCommands.parseDownloadRequest("link=https://packs.example.test/custom.tar.gz"));
        assertNull(IrisModdedCommands.parseDownloadRequest("pack=underworld branch=stable"));
        assertNull(IrisModdedCommands.parseDownloadRequest("pack=underworld overwrite=true"));
    }

    @Test
    public void downloadCompletionMessageOnlyReportsActualPackChanges() {
        assertEquals(
                "Pack installed on disk. Restart the server before using it.",
                IrisModdedCommands.downloadCompletionMessage(
                        new PackDownloader.PackInstallResult("overworld", true, true)
                )
        );
        assertEquals(
                "Pack installed on disk.",
                IrisModdedCommands.downloadCompletionMessage(
                        new PackDownloader.PackInstallResult("overworld", true, false)
                )
        );
        assertNull(IrisModdedCommands.downloadCompletionMessage(
                new PackDownloader.PackInstallResult("overworld", false, false)
        ));
        assertNull(IrisModdedCommands.downloadCompletionMessage(null));
    }

    @Test
    public void downloadBusyMessageDistinguishesPackDownloadsFromOtherLifecycleWork() {
        LifecycleOperationCoordinator.ActiveOperation packDownload = new LifecycleOperationCoordinator.ActiveOperation(
                1L,
                LifecycleOperationCoordinator.Domain.PACK_MUTATION,
                LifecycleOperationCoordinator.OperationKind.PACK_DOWNLOAD,
                "overworld"
        );
        LifecycleOperationCoordinator.ActiveOperation worldCreation = new LifecycleOperationCoordinator.ActiveOperation(
                2L,
                LifecycleOperationCoordinator.Domain.WORLD_MUTATION,
                LifecycleOperationCoordinator.OperationKind.WORLD_CREATE,
                "iris_world"
        );

        assertEquals(
                IrisLanguage.plain(PackDownloadMessages.IN_PROGRESS),
                IrisModdedCommands.downloadBusyMessage(packDownload)
        );
        assertEquals(
                "Iris pack changes are busy with world_create for 'iris_world'. Try again when it completes.",
                IrisModdedCommands.downloadBusyMessage(worldCreation)
        );
    }

    @Test
    public void helpDocumentsParityCommandsAndPlatformStubs() {
        assertTrue(ModdedCommandHelp.documents("what", "here"));
        assertTrue(ModdedCommandHelp.documents("what", "biome"));
        assertTrue(ModdedCommandHelp.documents("what", "region"));
        assertTrue(ModdedCommandHelp.documents("what", "block"));
        assertTrue(ModdedCommandHelp.documents("what", "hand"));
        assertTrue(ModdedCommandHelp.documents("what", "markers"));
        assertTrue(ModdedCommandHelp.documents("", "dust"));
        assertTrue(ModdedCommandHelp.documents("", "teleport"));
        assertTrue(ModdedCommandHelp.documents("", "c"));
        assertTrue(ModdedCommandHelp.documents("edit", "b"));
        assertTrue(ModdedCommandHelp.documents("object", "we"));
        assertTrue(ModdedCommandHelp.documents("world", "mainworld"));
        assertTrue(ModdedCommandHelp.documents("goto", "unregistered"));
    }

    @Test
    public void categoryLiteralsFallBackToHelpWithoutArguments() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();

        IrisModdedCommands.register(dispatcher);

        CommandNode<CommandSourceStack> iris = child(dispatcher.getRoot(), "iris");
        assertNotNull("iris", iris.getCommand());
        for (String category : new String[]{"help", "find", "goto", "pregen", "pregenerate", "object", "o",
                "edit", "studio", "std", "s", "pack", "pk", "world", "w", "datapack", "datapacks", "dp",
                "developer", "dev", "module", "m"}) {
            assertNotNull(category, child(iris, category).getCommand());
        }
    }

    @Test
    public void moduleCommandsHaveTheirOwnBranchAndNoTailElsewhere() {
        CommandNode<CommandSourceStack> iris = irisNode();

        for (CommandNode<CommandSourceStack> node : iris.getChildren()) {
            assertTrue(node.getName() + " must be a literal", node instanceof LiteralCommandNode);
        }
        for (String branch : new String[]{"module", "m"}) {
            CommandNode<CommandSourceStack> module = child(iris, branch);
            assertNotNull(branch + " lists the mounted commands on its own", module.getCommand());
            CommandNode<CommandSourceStack> rest = child(module, "command");
            assertTrue(rest instanceof ArgumentCommandNode);
            assertEquals(StringArgumentType.StringType.GREEDY_PHRASE, ((StringArgumentType) ((ArgumentCommandNode<?, ?>) rest).getType()).getType());
            assertNotNull(rest.getCommand());
        }
    }

    private static CommandNode<CommandSourceStack> irisNode() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        IrisModdedCommands.register(dispatcher);
        return child(dispatcher.getRoot(), "iris");
    }

    @Test
    public void consoleHelpListsEveryEntryWithInlineUsageAndDescription() {
        List<String> root = ModdedCommandHelp.consoleLines("");

        assertTrue(root.stream().anyMatch((String line) -> line.startsWith("/iris developer (dev) - ")));
        assertTrue(root.stream().anyMatch((String line) -> line.startsWith("/iris teleport <dimension> [player] (tp) - ")));
        assertTrue(ModdedCommandHelp.consoleLines("pregen").stream().anyMatch((String line) ->
                line.startsWith("/iris pregen start <radius> [dimension] [at] [x] [z] [gui] [sync] [nocache] - ")));
    }

    @Test
    public void helpListsOnlyWhatTheTreeHas() {
        CommandNode<CommandSourceStack> iris = irisNode();

        for (String section : ModdedCommandHelp.sections()) {
            CommandNode<CommandSourceStack> node = section.isEmpty() ? iris : child(iris, section);
            for (String word : ModdedCommandHelp.documentedWords(section)) {
                assertNotNull("/iris " + section + " " + word + " is in the help but not in the tree", node.getChild(word));
            }
        }
    }

    @Test
    public void everyHelpSectionRendersItsEntries() {
        for (String section : ModdedCommandHelp.sections()) {
            assertFalse(section, ModdedCommandHelp.consoleLines(section).isEmpty());
        }
    }

    @Test
    public void structureImportAndCaptureAreModuleCommandsAndHaveNoLiteralsOfTheirOwn() {
        CommandNode<CommandSourceStack> iris = irisNode();

        for (String structure : new String[]{"structure", "struct", "str"}) {
            assertNull(structure, iris.getChild(structure));
        }
        assertFalse(ModdedCommandHelp.sections().contains("structure"));
    }

    private static CommandNode<CommandSourceStack> child(
            CommandNode<CommandSourceStack> parent, String name) {
        CommandNode<CommandSourceStack> child = parent.getChild(name);
        assertNotNull(name, child);
        return child;
    }
}

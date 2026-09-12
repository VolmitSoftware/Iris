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

package art.arcane.iris.modded.command;

import art.arcane.iris.modded.ModdedIrisLog;
import art.arcane.iris.pack.validation.PackCompatReport;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.pack.PackDirectoryResolver;
import art.arcane.iris.pack.PackResourceCleanup;
import art.arcane.iris.pack.PackValidationRegistry;
import art.arcane.iris.pack.PackValidationResult;
import art.arcane.iris.pack.PackValidator;
import art.arcane.iris.modded.ModdedEngineBootstrap;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.modded.localization.ModdedCommandMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
public final class ModdedPackCommands {
    private static final Predicate<CommandSourceStack> GATE = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);

    private ModdedPackCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> tree(String name) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name).requires(GATE);

        root.executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedCommandHelp.send(context.getSource(), name)));

        root.then(Commands.literal("validate")
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> validate(context.getSource(), null)))
                .then(Commands.argument("pack", StringArgumentType.word()).suggests(IrisModdedCommands.PACK_NAMES)
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> validate(context.getSource(), StringArgumentType.getString(context, "pack"))))));
        root.then(Commands.literal("v")
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> validate(context.getSource(), null)))
                .then(Commands.argument("pack", StringArgumentType.word()).suggests(IrisModdedCommands.PACK_NAMES)
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> validate(context.getSource(), StringArgumentType.getString(context, "pack"))))));

        root.then(Commands.literal("cleanup")
                .then(Commands.argument("pack", StringArgumentType.word()).suggests(IrisModdedCommands.PACK_NAMES)
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> cleanup(context.getSource(), StringArgumentType.getString(context, "pack"), false)))
                        .then(Commands.literal("apply")
                                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> cleanup(context.getSource(), StringArgumentType.getString(context, "pack"), true))))));
        root.then(Commands.literal("c")
                .then(Commands.argument("pack", StringArgumentType.word()).suggests(IrisModdedCommands.PACK_NAMES)
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> cleanup(context.getSource(), StringArgumentType.getString(context, "pack"), false)))
                        .then(Commands.literal("apply")
                                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> cleanup(context.getSource(), StringArgumentType.getString(context, "pack"), true))))));

        root.then(Commands.literal("restore")
                .then(Commands.argument("pack", StringArgumentType.word()).suggests(IrisModdedCommands.PACK_NAMES)
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> restore(context.getSource(), StringArgumentType.getString(context, "pack"), false)))
                        .then(Commands.literal("apply")
                                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> restore(context.getSource(), StringArgumentType.getString(context, "pack"), true))))));
        root.then(Commands.literal("r")
                .then(Commands.argument("pack", StringArgumentType.word()).suggests(IrisModdedCommands.PACK_NAMES)
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> restore(context.getSource(), StringArgumentType.getString(context, "pack"), false)))
                        .then(Commands.literal("apply")
                                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> restore(context.getSource(), StringArgumentType.getString(context, "pack"), true))))));

        root.then(Commands.literal("status")
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> status(context.getSource(), null)))
                .then(Commands.argument("pack", StringArgumentType.word()).suggests(IrisModdedCommands.PACK_NAMES)
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> status(context.getSource(), StringArgumentType.getString(context, "pack"))))));
        root.then(Commands.literal("s")
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> status(context.getSource(), null)))
                .then(Commands.argument("pack", StringArgumentType.word()).suggests(IrisModdedCommands.PACK_NAMES)
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> status(context.getSource(), StringArgumentType.getString(context, "pack"))))));

        root.then(Commands.literal("compat")
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> compat(context.getSource(), null)))
                .then(Commands.argument("pack", StringArgumentType.word()).suggests(IrisModdedCommands.PACK_NAMES)
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> compat(context.getSource(), StringArgumentType.getString(context, "pack"))))));
        root.then(Commands.literal("cp")
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> compat(context.getSource(), null)))
                .then(Commands.argument("pack", StringArgumentType.word()).suggests(IrisModdedCommands.PACK_NAMES)
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> compat(context.getSource(), StringArgumentType.getString(context, "pack"))))));

        return root;
    }

    public static File packsRoot() {
        return IrisPlatforms.get().packsFolderNoCreate();
    }

    private static int validate(CommandSourceStack source, String pack) {
        File packsRoot = packsRoot();
        if (!packsRoot.isDirectory()) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_PACK_COMMANDS_PACKS_FOLDER_NOT_FOUND, MessageArgument.untrusted("value", packsRoot.getAbsolutePath())));
            return 0;
        }

        List<File> targets = new ArrayList<>();
        if (pack == null || pack.isBlank()) {
            List<File> dirs = PackDirectoryResolver.listVisiblePackDirectories(packsRoot);
            if (dirs.isEmpty()) {
                IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_PACK_COMMANDS_NO_PACKS_VALIDATE_UNDER, MessageArgument.untrusted("value", packsRoot.getAbsolutePath())));
                return 0;
            }
            targets.addAll(dirs);
        } else {
            File target = PackDirectoryResolver.resolveExisting(packsRoot, pack);
            if (target == null) {
                IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_PACK_COMMANDS_PACK_NOT_FOUND_UNDER, MessageArgument.untrusted("pack", pack), MessageArgument.untrusted("value", packsRoot.getAbsolutePath())));
                return 0;
            }
            targets.add(target);
        }

        MinecraftServer server = source.getServer();
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_PACK_COMMANDS_VALIDATING_PACK_S, MessageArgument.untrusted("value", targets.size())));
        Thread thread = new Thread(() -> {
            int broken = 0;
            for (File target : targets) {
                try {
                    PackValidationResult result = PackValidator.validate(target);
                    PackValidationRegistry.publish(result);
                    if (!result.isLoadable()) {
                        broken++;
                    }
                    server.execute(() -> report(source, result));
                } catch (Throwable e) {
                    ModdedIrisLog.error("Iris pack validation failed for {}", target.getName(), e);
                    server.execute(() -> IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_PACK_COMMANDS_VALIDATION_FAILED, MessageArgument.untrusted("value", target.getName()), MessageArgument.untrusted("value2", String.valueOf(e.getMessage())))));
                    broken++;
                }
            }
            int brokenTotal = broken;
            server.execute(() -> IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_PACK_COMMANDS_VALIDATION_COMPLETE_BROKEN_PACKS, MessageArgument.untrusted("brokenTotal", brokenTotal), MessageArgument.untrusted("value", targets.size()))));
        }, "Iris Pack Validator");
        thread.setDaemon(true);
        thread.start();
        return 1;
    }

    private static int cleanup(CommandSourceStack source, String pack, boolean apply) {
        File packFolder = PackDirectoryResolver.resolveExisting(packsRoot(), pack);
        if (packFolder == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_PACK_COMMANDS_PACK_NOT_FOUND_UNDER_2, MessageArgument.untrusted("pack", pack), MessageArgument.untrusted("value", packsRoot().getAbsolutePath())));
            return 0;
        }
        MinecraftServer server = source.getServer();
        Thread thread = new Thread(() -> {
            if (apply) {
                PackResourceCleanup.ApplyResult result = PackResourceCleanup.apply(packFolder);
                server.execute(() -> reportCleanupApply(source, pack, result));
            } else {
                PackResourceCleanup.Preview result = PackResourceCleanup.preview(packFolder);
                server.execute(() -> reportCleanupPreview(source, pack, result));
            }
        }, "Iris Pack Cleanup");
        thread.setDaemon(true);
        thread.start();
        return 1;
    }

    private static int restore(CommandSourceStack source, String pack, boolean apply) {
        File packFolder = PackDirectoryResolver.resolveExisting(packsRoot(), pack);
        if (packFolder == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_PACK_COMMANDS_PACK_NOT_FOUND_UNDER_3, MessageArgument.untrusted("pack", pack), MessageArgument.untrusted("value", packsRoot().getAbsolutePath())));
            return 0;
        }
        MinecraftServer server = source.getServer();
        Thread thread = new Thread(() -> {
            if (apply) {
                PackResourceCleanup.RestoreResult result = PackResourceCleanup.restoreLatest(packFolder);
                server.execute(() -> reportRestoreApply(source, pack, result));
            } else {
                PackResourceCleanup.RestorePreview result = PackResourceCleanup.previewRestore(packFolder);
                server.execute(() -> reportRestorePreview(source, pack, result));
            }
        }, "Iris Pack Restore");
        thread.setDaemon(true);
        thread.start();
        return 1;
    }

    private static int status(CommandSourceStack source, String pack) {
        if (pack == null || pack.isBlank()) {
            Map<String, PackValidationResult> snapshot = PackValidationRegistry.snapshot();
            if (snapshot.isEmpty()) {
                IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_PACK_COMMANDS_NO_VALIDATION_RESULTS_RECORDED_RUN_IRIS_PACK_VALIDATE_FIRST));
                return 0;
            }
            for (Map.Entry<String, PackValidationResult> entry : snapshot.entrySet()) {
                PackValidationResult result = entry.getValue();
                String tag = result.isLoadable() ? "OK" : "BROKEN";
                IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_PACK_COMMANDS_BLOCKING_WARNINGS, MessageArgument.untrusted("tag", tag), MessageArgument.untrusted("value", entry.getKey()), MessageArgument.untrusted("value2", result.getBlockingErrors().size()), MessageArgument.untrusted("value3", result.getWarnings().size())));
            }
            return 1;
        }
        PackValidationResult result = PackValidationRegistry.get(pack);
        if (result == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_PACK_COMMANDS_NO_VALIDATION_RESULT_RUN_IRIS_PACK_VALIDATE, MessageArgument.untrusted("pack", pack), MessageArgument.untrusted("pack2", pack)));
            return 0;
        }
        report(source, result);
        return 1;
    }

    private static int compat(CommandSourceStack source, String pack) {
        if (pack == null || pack.isBlank()) {
            Map<String, PackValidationResult> snapshot = PackValidationRegistry.snapshot();
            if (snapshot.isEmpty()) {
                IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_PACK_COMMANDS_NO_VALIDATION_RESULTS_RECORDED_RUN_IRIS_PACK_VALIDATE_FIRST));
                return 0;
            }
            for (Map.Entry<String, PackValidationResult> entry : snapshot.entrySet()) {
                for (String line : compatOutput(entry.getKey(), entry.getValue())) {
                    IrisModdedCommands.ok(source, line);
                }
            }
            return 1;
        }
        PackValidationResult result = PackValidationRegistry.get(pack);
        for (String line : compatOutput(pack, result)) {
            if (result == null) {
                IrisModdedCommands.fail(source, line);
            } else {
                IrisModdedCommands.ok(source, line);
            }
        }
        return result == null ? 0 : 1;
    }

    /**
     * The compat listing for one pack, straight from the persisted validation so no pack is reloaded. Every key is
     * printed with every subject; the boot listing is the capped form of the same data.
     */
    static List<String> compatOutput(String pack, PackValidationResult result) {
        List<String> lines = new ArrayList<>();
        if (result == null) {
            lines.add(IrisLanguage.plain(
                    ModdedCommandMessages.MODDED_PACK_COMMANDS_NO_VALIDATION_RESULT_RUN_IRIS_PACK_VALIDATE,
                    MessageArgument.untrusted("pack", pack),
                    MessageArgument.untrusted("pack2", pack)));
            return lines;
        }
        String version = compatVersion(result);
        if (result.getCompatFindings().isEmpty()) {
            lines.add(IrisLanguage.plain(
                    ModdedCommandMessages.MODDED_PACK_COMMANDS_COMPAT_NONE,
                    MessageArgument.untrusted("pack", pack),
                    MessageArgument.untrusted("version", version)));
            return lines;
        }
        lines.add(IrisLanguage.plain(
                ModdedCommandMessages.MODDED_PACK_COMMANDS_COMPAT_HEADER,
                MessageArgument.untrusted("pack", pack),
                MessageArgument.untrusted("version", version)));
        for (String line : PackCompatReport.of(result.getCompatFindings()).keyLines(0)) {
            lines.add(IrisLanguage.plain(
                    ModdedCommandMessages.MODDED_PACK_COMMANDS_MESSAGE_2,
                    MessageArgument.untrusted("value", line)));
        }
        lines.add(IrisLanguage.plain(ModdedCommandMessages.MODDED_PACK_COMMANDS_COMPAT_REMEDY));
        return lines;
    }

    /** The live Minecraft version, falling back to the one the validation ran against. */
    static String compatVersion(PackValidationResult result) {
        if (IrisPlatforms.isBound()) {
            String live = IrisPlatforms.get().minecraftVersion();
            if (live != null && !live.isBlank()) {
                return live;
            }
        }
        String persisted = result.getMinecraftVersion();
        return persisted == null || persisted.isBlank() ? "unknown" : persisted;
    }

    private static void report(CommandSourceStack source, PackValidationResult result) {
        if (result.isLoadable()) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_PACK_COMMANDS_PACK_IS_LOADABLE_WARNINGS, MessageArgument.untrusted("value", result.getPackName()), MessageArgument.untrusted("value2", result.getWarnings().size())));
        } else {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_PACK_COMMANDS_PACK_IS_BROKEN, MessageArgument.untrusted("value", result.getPackName())));
            for (String reason : result.getBlockingErrors()) {
                IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_PACK_COMMANDS_MESSAGE, MessageArgument.untrusted("reason", reason)));
            }
        }
        int warningMax = Math.min(10, result.getWarnings().size());
        for (int i = 0; i < warningMax; i++) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_PACK_COMMANDS_MESSAGE_2, MessageArgument.untrusted("value", result.getWarnings().get(i))));
        }
        if (result.getWarnings().size() > warningMax) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_PACK_COMMANDS_MORE_WARNING_S, MessageArgument.untrusted("value", (result.getWarnings().size() - warningMax))));
        }
    }

    private static void reportCleanupPreview(CommandSourceStack source, String pack, PackResourceCleanup.Preview result) {
        if (!result.success()) {
            IrisModdedCommands.fail(source, result.error());
            return;
        }
        if (!result.hasCandidates()) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_PACK_COMMANDS_NO_CLEANUP_CANDIDATES_FOUND_PACK, MessageArgument.untrusted("pack", pack)));
            return;
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_PACK_COMMANDS_CLEANUP_PREVIEW_PACK_CANDIDATE_S_NO_FILES_WERE_CHANGED, MessageArgument.untrusted("pack", pack), MessageArgument.untrusted("value", result.candidatePaths().size())));
        reportPaths(source, result.candidatePaths(), "candidate");
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_PACK_COMMANDS_RUN_IRIS_PACK_CLEANUP_APPLY_QUARANTINE_AFTER_FRESH_SCAN, MessageArgument.untrusted("pack", pack)));
    }

    private static void reportCleanupApply(CommandSourceStack source, String pack, PackResourceCleanup.ApplyResult result) {
        if (!result.success()) {
            IrisModdedCommands.fail(source, result.error());
            reportPaths(source, result.quarantinedPaths(), "still quarantined");
            return;
        }
        if (!result.changed()) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_PACK_COMMANDS_NO_CLEANUP_CANDIDATES_FOUND_PACK_2, MessageArgument.untrusted("pack", pack)));
            return;
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_PACK_COMMANDS_QUARANTINED_CLEANUP_CANDIDATE_S_UNDER, MessageArgument.untrusted("value", result.quarantinedPaths().size()), MessageArgument.untrusted("value2", result.quarantinePath())));
        reportPaths(source, result.quarantinedPaths(), "quarantined");
    }

    private static void reportRestorePreview(CommandSourceStack source, String pack, PackResourceCleanup.RestorePreview result) {
        if (!result.success()) {
            IrisModdedCommands.fail(source, result.error());
            return;
        }
        if (!result.hasFiles()) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_PACK_COMMANDS_NOTHING_RESTORE_PACK, MessageArgument.untrusted("pack", pack)));
            return;
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_PACK_COMMANDS_RESTORE_PREVIEW_FILE_S_NO_FILES_WERE_CHANGED, MessageArgument.untrusted("value", result.dumpPath()), MessageArgument.untrusted("value2", result.filePaths().size())));
        reportPaths(source, result.filePaths(), "file");
        if (!result.conflicts().isEmpty()) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_PACK_COMMANDS_RESTORE_IS_BLOCKED_BY_EXISTING_DESTINATION_S, MessageArgument.untrusted("value", result.conflicts().size())));
            reportPaths(source, result.conflicts(), "conflict");
            return;
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_PACK_COMMANDS_RUN_IRIS_PACK_RESTORE_APPLY_RESTORE_AFTER_FRESH_CONFLICT_CHECK, MessageArgument.untrusted("pack", pack)));
    }

    private static void reportRestoreApply(CommandSourceStack source, String pack, PackResourceCleanup.RestoreResult result) {
        if (!result.conflicts().isEmpty()) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_PACK_COMMANDS_RESTORE_REFUSED_BECAUSE_DESTINATION_S_ALREADY_EXIST, MessageArgument.untrusted("value", result.conflicts().size())));
            reportPaths(source, result.conflicts(), "conflict");
            return;
        }
        if (!result.success()) {
            IrisModdedCommands.fail(source, result.error());
            return;
        }
        if (!result.changed()) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_PACK_COMMANDS_NOTHING_RESTORE_PACK_2, MessageArgument.untrusted("pack", pack)));
            return;
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_PACK_COMMANDS_RESTORED_FILE_S_FROM, MessageArgument.untrusted("value", result.restoredPaths().size()), MessageArgument.untrusted("value2", result.dumpPath())));
        reportPaths(source, result.restoredPaths(), "restored");
    }

    private static void reportPaths(CommandSourceStack source, List<String> paths, String label) {
        int max = Math.min(10, paths.size());
        for (int i = 0; i < max; i++) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_PACK_COMMANDS_MESSAGE_3, MessageArgument.untrusted("label", label), MessageArgument.untrusted("value", paths.get(i))));
        }
        if (paths.size() > max) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_PACK_COMMANDS_MORE, MessageArgument.untrusted("value", (paths.size() - max))));
        }
    }
}

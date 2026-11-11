/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core.commands;

import art.arcane.iris.Iris;
import art.arcane.iris.core.localization.BukkitCommandMessagesExtended;
import art.arcane.iris.core.modules.KeptModuleUpdates;
import art.arcane.iris.core.tools.IrisToolbelt;
import art.arcane.iris.core.pack.PackDirectoryResolver;
import art.arcane.iris.core.pack.PackResourceCleanup;
import art.arcane.iris.core.pack.PackValidationCache;
import art.arcane.iris.core.pack.PackValidationRegistry;
import art.arcane.iris.core.pack.PackValidationResult;
import art.arcane.iris.core.pack.PackValidator;
import art.arcane.iris.util.common.director.DirectorExecutor;
import art.arcane.iris.util.common.plugin.VolmitSender;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.localization.TextKey;

import org.bukkit.World;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import art.arcane.iris.core.localization.IrisLanguage;
import art.arcane.iris.core.localization.BukkitCommandMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.iris.core.localization.BukkitRuntimeMessages;
@Director(name = "pack", aliases = {"pk"}, description = "Pack validation and maintenance", descriptionKey = "iris.director.commandpack.director.pack_validation_maintenance")
public class CommandPack implements DirectorExecutor {
    /**
     * Director treats a blank defaultValue as "required", which made the all-packs branch
     * unreachable from chat. The "*" sentinel keeps the parameter genuinely optional; a blank
     * value (the documented "pack=" escape hatch) still means every pack.
     */
    static boolean wantsAllPacks(String pack) {
        return pack == null || pack.isBlank() || "*".equals(pack.trim());
    }

    @Director(description = "Validate a pack (or all packs) and re-publish results", descriptionKey = "iris.director.commandpack.director.validate_pack_all_packs_re_publish_results", aliases = {"v"})
    public void validate(
            @Param(description = "The pack folder name to validate, or * for every pack", descriptionKey = "iris.director.commandpack.param.pack_folder_name_validate_leave_empty_all", defaultValue = "*")
            String pack
    ) {
        VolmitSender s = sender();
        File packsRoot = Iris.instance.getDataFolder("packs");
        if (!packsRoot.isDirectory()) {
            s.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_PACKS_FOLDER_NOT_FOUND));
            return;
        }

        if (wantsAllPacks(pack)) {
            List<File> dirs = PackDirectoryResolver.listVisiblePackDirectories(packsRoot);
            if (dirs.isEmpty()) {
                s.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_NO_PACKS_VALIDATE));
                return;
            }
            int broken = 0;
            for (File dir : dirs) {
                PackValidationResult result = runValidate(s, dir);
                if (result != null && !result.isLoadable()) {
                    broken++;
                }
            }
            persistValidationCache(packsRoot);
            s.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_VALIDATION_COMPLETE_BROKEN_PACKS, MessageArgument.untrusted("broken", String.valueOf(broken)), MessageArgument.untrusted("value", String.valueOf(dirs.size()))));
            return;
        }

        File target = PackDirectoryResolver.resolveExisting(packsRoot, pack);
        if (target == null) {
            s.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_PACK_NOT_FOUND_UNDER_PACKS, MessageArgument.untrusted("pack", String.valueOf(pack))));
            return;
        }
        runValidate(s, target);
        persistValidationCache(packsRoot);
    }

    @Director(description = "Take the module updates a world kept because they change its terrain", descriptionKey = "iris.director.commandpack.director.take_kept_module_updates")
    public void update(
            @Param(name = "world", description = "The Iris world whose modules to update", descriptionKey = "iris.director.commandpack.param.world_take_module_updates", contextual = true, contextualOverride = true)
            World world
    ) {
        if (!IrisToolbelt.isIrisWorld(world)) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_STUDIO_YOU_NEED_BE_SPECIFY_IRIS_GENERATED_WORLD));
            return;
        }
        KeptModuleUpdates.take(IrisToolbelt.access(world).getEngine().getData()).forEach(sender()::sendMessage);
    }

    @Director(description = "Preview or apply unused-resource cleanup", descriptionKey = "iris.director.commandpack.director.preview_apply_unused_resource_cleanup", aliases = {"c"})
    public void cleanup(
            @Param(description = "The pack folder name to clean", descriptionKey = "iris.director.commandpack.param.pack_folder_name_clean")
            String pack,
            @Param(description = "preview or apply", descriptionKey = "iris.director.commandpack.param.preview_apply", defaultValue = "preview")
            String mode
    ) {
        VolmitSender s = sender();
        File packFolder = findPack(s, pack);
        if (packFolder == null) {
            return;
        }
        if ("apply".equalsIgnoreCase(mode)) {
            PackResourceCleanup.ApplyResult result = PackResourceCleanup.apply(packFolder);
            if (!result.success()) {
                s.sendMessage(IrisLanguage.text(
                        BukkitRuntimeMessages.COMMAND_PACK_CLEANUP_FAILED,
                        MessageArgument.untrusted("error", String.valueOf(result.error()))
                ));
                reportPaths(s, result.quarantinedPaths(), BukkitRuntimeMessages.COMMAND_PACK_PATH_STILL_QUARANTINED);
                return;
            }
            if (!result.changed()) {
                s.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_NO_CLEANUP_CANDIDATES_FOUND_PACK, MessageArgument.untrusted("pack", String.valueOf(pack))));
                return;
            }
            PackValidationRegistry.remove(packFolder.getName());
            s.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_QUARANTINED_CLEANUP_CANDIDATE_S_UNDER, MessageArgument.untrusted("size", String.valueOf(result.quarantinedPaths().size())), MessageArgument.untrusted("quarantinePath", String.valueOf(result.quarantinePath()))));
            reportPaths(s, result.quarantinedPaths(), BukkitRuntimeMessages.COMMAND_PACK_PATH_QUARANTINED);
            return;
        }
        if (!"preview".equalsIgnoreCase(mode)) {
            s.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_CLEANUP_MODE_MUST_BE_PREVIEW_APPLY));
            return;
        }
        PackResourceCleanup.Preview preview = PackResourceCleanup.preview(packFolder);
        if (!preview.success()) {
            s.sendMessage(IrisLanguage.text(
                    BukkitRuntimeMessages.COMMAND_PACK_CLEANUP_FAILED,
                    MessageArgument.untrusted("error", String.valueOf(preview.error()))
            ));
            return;
        }
        if (!preview.hasCandidates()) {
            s.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_NO_CLEANUP_CANDIDATES_FOUND_PACK_2, MessageArgument.untrusted("pack", String.valueOf(pack))));
            return;
        }
        s.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_CLEANUP_PREVIEW_PACK_CANDIDATE_S_NO_FILES_WERE_CHANGED, MessageArgument.untrusted("pack", String.valueOf(pack)), MessageArgument.untrusted("size", String.valueOf(preview.candidatePaths().size()))));
        reportPaths(s, preview.candidatePaths(), BukkitRuntimeMessages.COMMAND_PACK_PATH_CANDIDATE);
        s.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_RUN_IRIS_PACK_CLEANUP_MODE_APPLY_QUARANTINE_THESE_CANDIDATES_AFTER_FRESH_SCAN, MessageArgument.untrusted("pack", String.valueOf(pack))));
    }

    @Director(description = "Preview or apply restoration of the latest quarantine", descriptionKey = "iris.director.commandpack.director.preview_apply_restoration_latest_quarantine", aliases = {"r"})
    public void restore(
            @Param(description = "The pack folder name to restore", descriptionKey = "iris.director.commandpack.param.pack_folder_name_restore")
            String pack,
            @Param(description = "preview or apply", descriptionKey = "iris.director.commandpack.param.preview_apply_2", defaultValue = "preview")
            String mode
    ) {
        VolmitSender s = sender();
        File packFolder = findPack(s, pack);
        if (packFolder == null) {
            return;
        }
        if ("apply".equalsIgnoreCase(mode)) {
            PackResourceCleanup.RestoreResult result = PackResourceCleanup.restoreLatest(packFolder);
            if (!result.conflicts().isEmpty()) {
                s.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_RESTORE_REFUSED_BECAUSE_DESTINATION_S_ALREADY_EXIST, MessageArgument.untrusted("size", String.valueOf(result.conflicts().size()))));
                reportPaths(s, result.conflicts(), BukkitRuntimeMessages.COMMAND_PACK_PATH_CONFLICT);
                return;
            }
            if (!result.success()) {
                s.sendMessage(IrisLanguage.text(
                        BukkitRuntimeMessages.COMMAND_PACK_RESTORE_FAILED,
                        MessageArgument.untrusted("error", String.valueOf(result.error()))
                ));
                return;
            }
            if (!result.changed()) {
                s.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_NOTHING_RESTORE_PACK, MessageArgument.untrusted("pack", String.valueOf(pack))));
                return;
            }
            PackValidationRegistry.remove(packFolder.getName());
            s.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_RESTORED_FILE_S_FROM, MessageArgument.untrusted("size", String.valueOf(result.restoredPaths().size())), MessageArgument.untrusted("dumpPath", String.valueOf(result.dumpPath()))));
            reportPaths(s, result.restoredPaths(), BukkitRuntimeMessages.COMMAND_PACK_PATH_RESTORED);
            return;
        }
        if (!"preview".equalsIgnoreCase(mode)) {
            s.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_RESTORE_MODE_MUST_BE_PREVIEW_APPLY));
            return;
        }
        PackResourceCleanup.RestorePreview preview = PackResourceCleanup.previewRestore(packFolder);
        if (!preview.success()) {
            s.sendMessage(IrisLanguage.text(
                    BukkitRuntimeMessages.COMMAND_PACK_RESTORE_FAILED,
                    MessageArgument.untrusted("error", String.valueOf(preview.error()))
            ));
            return;
        }
        if (!preview.hasFiles()) {
            s.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_NOTHING_RESTORE_PACK_2, MessageArgument.untrusted("pack", String.valueOf(pack))));
            return;
        }
        s.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_RESTORE_PREVIEW_FILE_S_NO_FILES_WERE_CHANGED, MessageArgument.untrusted("dumpPath", String.valueOf(preview.dumpPath())), MessageArgument.untrusted("size", String.valueOf(preview.filePaths().size()))));
        reportPaths(s, preview.filePaths(), BukkitRuntimeMessages.COMMAND_PACK_PATH_FILE);
        if (!preview.conflicts().isEmpty()) {
            s.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_RESTORE_IS_BLOCKED_BY_EXISTING_DESTINATION_S, MessageArgument.untrusted("size", String.valueOf(preview.conflicts().size()))));
            reportPaths(s, preview.conflicts(), BukkitRuntimeMessages.COMMAND_PACK_PATH_CONFLICT);
            return;
        }
        s.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_RUN_IRIS_PACK_RESTORE_MODE_APPLY_RESTORE_AFTER_FRESH_CONFLICT_CHECK, MessageArgument.untrusted("pack", String.valueOf(pack))));
    }

    @Director(description = "Show cached validation status for a pack", descriptionKey = "iris.director.commandpack.director.show_cached_validation_status_pack", aliases = {"s"})
    public void status(
            @Param(description = "The pack folder name, or * for every pack", descriptionKey = "iris.director.commandpack.param.pack_folder_name", defaultValue = "*")
            String pack
    ) {
        VolmitSender s = sender();
        if (wantsAllPacks(pack)) {
            if (PackValidationRegistry.snapshot().isEmpty()) {
                s.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_NO_VALIDATION_RESULTS_RECORDED_RUN_IRIS_PACK_VALIDATE_FIRST));
                return;
            }
            PackValidationRegistry.snapshot().forEach((name, result) -> {
                TextKey key = result.isLoadable()
                        ? BukkitRuntimeMessages.COMMAND_PACK_STATUS_OK
                        : BukkitRuntimeMessages.COMMAND_PACK_STATUS_BROKEN;
                s.sendMessage(IrisLanguage.text(
                        key,
                        MessageArgument.untrusted("pack", name),
                        MessageArgument.trusted("blocking", result.getBlockingErrors().size()),
                        MessageArgument.trusted("warnings", result.getWarnings().size())
                ));
            });
            return;
        }
        PackValidationResult result = PackValidationRegistry.get(pack);
        if (result == null) {
            s.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_NO_VALIDATION_RESULT_RUN_IRIS_PACK_VALIDATE, MessageArgument.untrusted("pack", String.valueOf(pack)), MessageArgument.untrusted("pack2", String.valueOf(pack))));
            return;
        }
        reportResult(s, result);
    }

    private PackValidationResult runValidate(VolmitSender s, File packFolder) {
        try {
            PackValidationResult result = PackValidator.validate(packFolder);
            PackValidationRegistry.publish(result);
            reportResult(s, result);
            return result;
        } catch (Throwable e) {
            Iris.reportError("Pack validation failed for '" + packFolder.getName() + "'", e);
            String detail = e.getMessage() == null || e.getMessage().isBlank()
                    ? e.getClass().getSimpleName()
                    : e.getMessage();
            PackValidationResult result = new PackValidationResult(
                    packFolder.getName(),
                    List.of("Pack validation failed with " + e.getClass().getSimpleName() + ": " + detail),
                    List.of(),
                    System.currentTimeMillis());
            PackValidationRegistry.publish(result);
            reportResult(s, result);
            return result;
        }
    }

    private void persistValidationCache(File packsRoot) {
        List<File> packDirectories = PackDirectoryResolver.listVisiblePackDirectories(packsRoot);
        List<PackValidationResult> results = new ArrayList<>(packDirectories.size());
        for (File packDirectory : packDirectories) {
            PackValidationResult result = PackValidationRegistry.get(packDirectory.getName());
            if (result == null) {
                // The boot cache only loads when it covers every pack, so a partial write is
                // pointless - but say so instead of silently skipping the persist forever.
                Iris.warn("Pack validation cache not written: \"" + packDirectory.getName()
                        + "\" has no registered result. Run /iris pack validate all to repopulate it.");
                return;
            }
            results.add(result);
        }
        try {
            PackValidationCache.save(
                    Iris.instance.getDataFile("cache", "pack-validation.json").toPath(),
                    PackValidationCache.contentFingerprint(packsRoot),
                    PackValidationCache.contextFingerprint(),
                    results);
        } catch (IOException | RuntimeException e) {
            Iris.reportError("Could not persist refreshed pack-validation results", e);
        }
    }

    private void reportResult(VolmitSender s, PackValidationResult result) {
        if (result.isLoadable()) {
            s.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_PACK_IS_LOADABLE_WARNINGS, MessageArgument.untrusted("packName", String.valueOf(result.getPackName())), MessageArgument.untrusted("size", String.valueOf(result.getWarnings().size()))));
        } else {
            s.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_PACK_IS_BROKEN, MessageArgument.untrusted("packName", String.valueOf(result.getPackName()))));
            for (String reason : result.getBlockingErrors()) {
                s.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_MESSAGE, MessageArgument.untrusted("reason", String.valueOf(reason))));
            }
        }
        int wMax = Math.min(10, result.getWarnings().size());
        for (int i = 0; i < wMax; i++) {
            s.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_MESSAGE_2, MessageArgument.untrusted("value", String.valueOf(result.getWarnings().get(i)))));
        }
        if (result.getWarnings().size() > wMax) {
            s.sendMessage(IrisLanguage.text(
                    BukkitRuntimeMessages.COMMAND_PACK_MORE_WARNING_S,
                    MessageArgument.trusted("count", result.getWarnings().size() - wMax)
            ));
        }
    }

    private File findPack(VolmitSender sender, String pack) {
        if (pack == null || pack.isBlank()) {
            sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_PACK_YOU_MUST_SPECIFY_PACK_NAME));
            return null;
        }
        File packFolder = PackDirectoryResolver.resolveExisting(Iris.instance.getDataFolder("packs"), pack);
        if (packFolder == null) {
            sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_PACK_PACK_NOT_FOUND_UNDER_PACKS, MessageArgument.untrusted("pack", pack)));
            return null;
        }
        return packFolder;
    }

    private void reportPaths(VolmitSender sender, List<String> paths, TextKey label) {
        int max = Math.min(10, paths.size());
        for (int i = 0; i < max; i++) {
            sender.sendMessage(IrisLanguage.text(
                    BukkitCommandMessages.COMMAND_PACK_MESSAGE,
                    MessageArgument.untrusted("label", IrisLanguage.text(label)),
                    MessageArgument.untrusted("value", paths.get(i))
            ));
        }
        if (paths.size() > max) {
            sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_PACK_MORE, MessageArgument.untrusted("value", (paths.size() - max))));
        }
    }
}

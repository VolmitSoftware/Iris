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

package art.arcane.iris.command;

import art.arcane.iris.command.specialhandlers.PackFolderHandler;
import art.arcane.iris.pack.PackFingerprints;

import art.arcane.iris.Iris;
import art.arcane.iris.pack.validation.PackCompatReport;
import art.arcane.iris.pack.PackDirectoryResolver;
import art.arcane.iris.pack.PackResourceCleanup;
import art.arcane.iris.pack.PackValidationCache;
import art.arcane.iris.pack.PackValidationRegistry;
import art.arcane.iris.pack.PackValidationResult;
import art.arcane.iris.pack.PackValidator;
import art.arcane.iris.studio.StudioSVC;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.iris.world.lifecycle.LifecycleOperationCoordinator;
import art.arcane.iris.localization.C;
import art.arcane.iris.localization.ChatReport;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.command.handlers.DimensionHandler;
import art.arcane.iris.command.specialhandlers.PreviewApplyModeHandler;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.localization.TextKey;

import art.arcane.iris.spi.IrisPlatforms;
import org.bukkit.World;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.BukkitCommandMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.iris.localization.BukkitRuntimeMessages;
@Director(name = "pack", aliases = {"pk"}, description = "Pack validation and maintenance", descriptionKey = "iris.director.commandpack.director.pack_validation_maintenance")
public class CommandPack implements DirectorExecutor {
    private static final int PATH_REPORT_LIMIT = 10;
    private static final Pattern LEADING_COLOR = Pattern.compile("^§[0-9a-fk-or]");

    @Director(name = "update-world", description = "Stage an installed pack for a world and request a clean restart", descriptionKey = "iris.director.commanddeveloper.director.stage_world_generation_update")
    public void updateWorld(
            @Param(description = "The world to update", descriptionKey = "iris.director.commanddeveloper.param.world_update", contextual = true, contextualOverride = true)
            World world,
            @Param(description = "The installed dimension to use", descriptionKey = "iris.director.commanddeveloper.param.pack_install_into_world", contextual = true, contextualOverride = true, aliases = "dimension")
            IrisDimension pack,
            @Param(description = "Confirm that the complete world has been backed up", descriptionKey = "iris.director.commanddeveloper.param.make_sure_make_backup_read_warnings_first", defaultValue = "false", aliases = "c")
            boolean confirm
    ) {
        stageWorldUpdate(sender(), world, pack, confirm);
    }

    static void stageWorldUpdate(VolmitSender sender, World world, IrisDimension pack, boolean confirm) {
        if (!IrisToolbelt.isIrisWorld(world) || IrisToolbelt.isIrisStudioWorld(world)) {
            sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_UPDATE_WORLD_REQUIRES_PERSISTENT_WORLD));
            return;
        }
        if (!confirm) {
            sender.sendMessage(IrisLanguage.text(
                    BukkitRuntimeMessages.COMMAND_DEVELOPER_UPDATE_WORLD_WARNING,
                    MessageArgument.untrusted("world", world.getName()),
                    MessageArgument.untrusted("pack", pack.getLoadKey())
            ));
            return;
        }
        try (LifecycleOperationCoordinator.Lease lease = LifecycleOperationCoordinator.get().acquire(
                LifecycleOperationCoordinator.Domain.PACK_MUTATION,
                LifecycleOperationCoordinator.OperationKind.PACK_PUBLISH,
                world.getName()
        )) {
            Iris.service(StudioSVC.class).replaceIntoWorld(sender, pack, world.getWorldFolder(), world.getSeed());
        } catch (LifecycleOperationCoordinator.BusyException e) {
            sender.sendMessage(C.YELLOW + e.getMessage());
        }
    }

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
            @Param(description = "The pack folder name to validate; omit for every pack", descriptionKey = "iris.director.commandpack.param.pack_folder_name_validate_leave_empty_all", defaultValue = "*", customHandler = PackFolderHandler.class)
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
                PackValidationResult result = runValidate(dir);
                if (!result.isLoadable()) {
                    broken++;
                    reportResult(s, result);
                }
            }
            persistValidationCache(packsRoot);
            ChatReport.Tone summaryTone = broken == 0 ? ChatReport.Tone.SUCCESS : ChatReport.Tone.ERROR;
            s.sendMessage(summaryTone.paint(IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_VALIDATION_COMPLETE_BROKEN_PACKS, MessageArgument.untrusted("broken", String.valueOf(broken)), MessageArgument.untrusted("value", String.valueOf(dirs.size())))));
            return;
        }

        File target = PackDirectoryResolver.resolveExisting(packsRoot, pack);
        if (target == null) {
            s.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_PACK_NOT_FOUND_UNDER_PACKS, MessageArgument.untrusted("pack", String.valueOf(pack))));
            return;
        }
        reportResult(s, runValidate(target));
        persistValidationCache(packsRoot);
    }

    @Director(description = "Package a validated dimension into a compressed format", descriptionKey = "iris.director.commandstudio.director.package_dimension_into_compressed_format", aliases = "package")
    public void pkg(
            @Param(name = "dimension", description = "The dimension pack to compress", descriptionKey = "iris.director.commandstudio.param.dimension_pack_compress", contextual = true, contextualOverride = true, defaultValue = "default", customHandler = DimensionHandler.class)
            IrisDimension dimension,
            @Param(name = "obfuscate", description = "Whether or not to obfuscate the pack", descriptionKey = "iris.director.commandstudio.param.whether_not_obfuscate_pack", defaultValue = "false")
            boolean obfuscate,
            @Param(name = "minify", description = "Whether or not to minify the pack", descriptionKey = "iris.director.commandstudio.param.whether_not_minify_pack", defaultValue = "true")
            boolean minify
    ) {
        Iris.service(StudioSVC.class).compilePackage(sender(), dimension.getLoadKey(), obfuscate, minify);
    }

    @Director(description = "Preview or apply unused-resource cleanup", descriptionKey = "iris.director.commandpack.director.preview_apply_unused_resource_cleanup", aliases = {"c"})
    public void cleanup(
            @Param(description = "The pack folder name to clean", descriptionKey = "iris.director.commandpack.param.pack_folder_name_clean", customHandler = PackFolderHandler.class)
            String pack,
            @Param(description = "preview or apply", descriptionKey = "iris.director.commandpack.param.preview_apply", defaultValue = "preview", customHandler = PreviewApplyModeHandler.class)
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
                reportPaths(s, ChatReport.Tone.ERROR, IrisLanguage.text(
                        BukkitRuntimeMessages.COMMAND_PACK_CLEANUP_FAILED,
                        MessageArgument.untrusted("error", String.valueOf(result.error()))
                ), result.quarantinedPaths(), BukkitRuntimeMessages.COMMAND_PACK_PATH_STILL_QUARANTINED);
                return;
            }
            if (!result.changed()) {
                s.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_NO_CLEANUP_CANDIDATES_FOUND_PACK, MessageArgument.untrusted("pack", String.valueOf(pack))));
                return;
            }
            reportPaths(s, ChatReport.Tone.SUCCESS, IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_QUARANTINED_CLEANUP_CANDIDATE_S_UNDER, MessageArgument.untrusted("size", String.valueOf(result.quarantinedPaths().size())), MessageArgument.untrusted("quarantinePath", String.valueOf(result.quarantinePath()))),
                    result.quarantinedPaths(), BukkitRuntimeMessages.COMMAND_PACK_PATH_QUARANTINED);
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
        reportPaths(s, ChatReport.Tone.INFO, IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_CLEANUP_PREVIEW_PACK_CANDIDATE_S_NO_FILES_WERE_CHANGED, MessageArgument.untrusted("pack", String.valueOf(pack)), MessageArgument.untrusted("size", String.valueOf(preview.candidatePaths().size()))),
                preview.candidatePaths(), BukkitRuntimeMessages.COMMAND_PACK_PATH_CANDIDATE);
        s.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_RUN_IRIS_PACK_CLEANUP_MODE_APPLY_QUARANTINE_THESE_CANDIDATES_AFTER_FRESH_SCAN, MessageArgument.untrusted("pack", String.valueOf(pack))));
    }

    @Director(description = "Preview or apply restoration of the latest quarantine", descriptionKey = "iris.director.commandpack.director.preview_apply_restoration_latest_quarantine", aliases = {"r"})
    public void restore(
            @Param(description = "The pack folder name to restore", descriptionKey = "iris.director.commandpack.param.pack_folder_name_restore", customHandler = PackFolderHandler.class)
            String pack,
            @Param(description = "preview or apply", descriptionKey = "iris.director.commandpack.param.preview_apply_2", defaultValue = "preview", customHandler = PreviewApplyModeHandler.class)
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
                reportPaths(s, ChatReport.Tone.ERROR, IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_RESTORE_REFUSED_BECAUSE_DESTINATION_S_ALREADY_EXIST, MessageArgument.untrusted("size", String.valueOf(result.conflicts().size()))),
                        result.conflicts(), BukkitRuntimeMessages.COMMAND_PACK_PATH_CONFLICT);
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
            reportPaths(s, ChatReport.Tone.SUCCESS, IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_RESTORED_FILE_S_FROM, MessageArgument.untrusted("size", String.valueOf(result.restoredPaths().size())), MessageArgument.untrusted("dumpPath", String.valueOf(result.dumpPath()))),
                    result.restoredPaths(), BukkitRuntimeMessages.COMMAND_PACK_PATH_RESTORED);
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
        reportPaths(s, ChatReport.Tone.INFO, IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_RESTORE_PREVIEW_FILE_S_NO_FILES_WERE_CHANGED, MessageArgument.untrusted("dumpPath", String.valueOf(preview.dumpPath())), MessageArgument.untrusted("size", String.valueOf(preview.filePaths().size()))),
                preview.filePaths(), BukkitRuntimeMessages.COMMAND_PACK_PATH_FILE);
        if (!preview.conflicts().isEmpty()) {
            reportPaths(s, ChatReport.Tone.ERROR, IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_RESTORE_IS_BLOCKED_BY_EXISTING_DESTINATION_S, MessageArgument.untrusted("size", String.valueOf(preview.conflicts().size()))),
                    preview.conflicts(), BukkitRuntimeMessages.COMMAND_PACK_PATH_CONFLICT);
            return;
        }
        s.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_RUN_IRIS_PACK_RESTORE_MODE_APPLY_RESTORE_AFTER_FRESH_CONFLICT_CHECK, MessageArgument.untrusted("pack", String.valueOf(pack))));
    }

    @Director(description = "Show cached validation status for a pack", descriptionKey = "iris.director.commandpack.director.show_cached_validation_status_pack", aliases = {"s"})
    public void status(
            @Param(description = "The pack folder name; omit for every pack", descriptionKey = "iris.director.commandpack.param.pack_folder_name", defaultValue = "*", customHandler = PackFolderHandler.class)
            String pack
    ) {
        VolmitSender s = sender();
        if (wantsAllPacks(pack)) {
            if (PackValidationRegistry.snapshot().isEmpty()) {
                s.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_NO_VALIDATION_RESULTS_RECORDED_RUN_IRIS_PACK_VALIDATE_FIRST));
                return;
            }
            ChatReport.Builder report = ChatReport.of(ChatReport.Tone.INFO, "Pack validation status").unlimited();
            PackValidationRegistry.snapshot().entrySet().stream()
                    .sorted(Comparator.comparing((Map.Entry<String, PackValidationResult> entry) -> statusTone(entry.getValue()).ordinal())
                            .reversed()
                            .thenComparing(Map.Entry::getKey))
                    .forEach(entry -> report.styledItem(statusLine(entry.getKey(), entry.getValue())));
            s.sendReport(report.build());
            return;
        }
        PackValidationResult result = PackValidationRegistry.get(pack);
        if (result == null) {
            s.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_NO_VALIDATION_RESULT_RUN_IRIS_PACK_VALIDATE, MessageArgument.untrusted("pack", String.valueOf(pack)), MessageArgument.untrusted("pack2", String.valueOf(pack))));
            return;
        }
        reportResult(s, result);
    }

    @Director(description = "Show pack content that cannot generate on this Minecraft version", descriptionKey = "iris.director.commandpack.director.show_content_cannot_generate_this_minecraft_version", aliases = {"cp"})
    public void compat(
            @Param(description = "The pack folder name to report on; omit for every pack", descriptionKey = "iris.director.commandpack.param.pack_folder_name_compat", defaultValue = "*", customHandler = PackFolderHandler.class)
            String pack
    ) {
        VolmitSender s = sender();
        if (wantsAllPacks(pack)) {
            Map<String, PackValidationResult> snapshot = PackValidationRegistry.snapshot();
            if (snapshot.isEmpty()) {
                s.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_NO_VALIDATION_RESULTS_RECORDED_RUN_IRIS_PACK_VALIDATE_FIRST));
                return;
            }
            for (Map.Entry<String, PackValidationResult> entry : snapshot.entrySet()) {
                s.sendReport(compatReport(entry.getKey(), entry.getValue()));
            }
            return;
        }
        s.sendReport(compatReport(pack, PackValidationRegistry.get(pack)));
    }

    /**
     * The compat listing for one pack, straight from the persisted validation so no pack is reloaded. Every key is
     * printed with every subject; the boot listing is the capped form of the same data.
     */
    static List<String> compatOutput(String pack, PackValidationResult result) {
        return compatReport(pack, result).lines().stream().map(ChatReport.Line::text).toList();
    }

    static ChatReport compatReport(String pack, PackValidationResult result) {
        if (result == null) {
            return ChatReport.of(ChatReport.Tone.WARNING, IrisLanguage.text(
                    BukkitRuntimeMessages.COMMAND_PACK_NO_VALIDATION_RESULT_RUN_IRIS_PACK_VALIDATE,
                    MessageArgument.untrusted("pack", String.valueOf(pack)),
                    MessageArgument.untrusted("pack2", String.valueOf(pack)))).build();
        }
        String version = compatVersion(result);
        if (result.getCompatFindings().isEmpty()) {
            return ChatReport.of(ChatReport.Tone.SUCCESS, IrisLanguage.text(
                    BukkitRuntimeMessages.COMMAND_PACK_COMPAT_NONE,
                    MessageArgument.untrusted("pack", pack),
                    MessageArgument.untrusted("version", version))).build();
        }
        return ChatReport.of(ChatReport.Tone.WARNING, IrisLanguage.text(
                        BukkitRuntimeMessages.COMMAND_PACK_COMPAT_HEADER,
                        MessageArgument.untrusted("pack", pack),
                        MessageArgument.untrusted("version", version)))
                .items(PackCompatReport.of(result.getCompatFindings()).keyLines(0))
                .unlimited()
                .hint(IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_COMPAT_REMEDY), null)
                .build();
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

    PackValidationResult runValidate(File packFolder) {
        PackValidationRegistry.ValidationTicket ticket =
                PackValidationRegistry.tryBeginValidation(packFolder.toPath());
        PackValidationResult result;
        String contentFingerprint = "";
        String contextFingerprint = "";
        try {
            if (ticket == null) {
                throw new IllegalStateException("Pack content is currently being changed.");
            }
            contentFingerprint = PackFingerprints.computePackTreeFingerprint(packFolder);
            contextFingerprint = PackValidationCache.contextFingerprint();
            result = PackValidator.validate(packFolder);
            if (!contentFingerprint.equals(PackFingerprints.computePackTreeFingerprint(packFolder))
                    || !contextFingerprint.equals(PackValidationCache.contextFingerprint())) {
                throw new IllegalStateException("Pack content or validation context changed during validation.");
            }
        } catch (Throwable e) {
            Iris.reportError("Pack validation failed for '" + packFolder.getName() + "'", e);
            String detail = e.getMessage() == null || e.getMessage().isBlank()
                    ? e.getClass().getSimpleName()
                    : e.getMessage();
            result = new PackValidationResult(
                    packFolder.getName(),
                    List.of("Pack validation failed with " + e.getClass().getSimpleName() + ": " + detail),
                    List.of(),
                    System.currentTimeMillis());
            contentFingerprint = "";
            contextFingerprint = "";
        }
        if (PackValidationRegistry.publishIfCurrent(ticket, result, contentFingerprint, contextFingerprint)) {
            PackValidationRegistry.publish(result);
        } else {
            result = new PackValidationResult(packFolder.getName(),
                    List.of("Pack content changed during validation."), List.of(), System.currentTimeMillis());
        }
        return result;
    }

    private void persistValidationCache(File packsRoot) {
        try {
            String contextFingerprint = PackValidationCache.contextFingerprint();
            PackFingerprints.PackContentSnapshot snapshot = PackFingerprints.computePackContentSnapshot(packsRoot);
            List<File> packDirectories = PackDirectoryResolver.listVisiblePackDirectories(packsRoot);
            List<PackValidationResult> results = new ArrayList<>(packDirectories.size());
            for (File packDirectory : packDirectories) {
                PackValidationResult result = PackValidationRegistry.getMatching(
                        packDirectory.toPath(), snapshot.packContents().get(packDirectory.getName()));
                if (result == null) {
                    Iris.warn("Pack validation cache not written: \"" + packDirectory.getName()
                            + "\" has no validation matching its current content. Run /iris pack validate to refresh it.");
                    return;
                }
                results.add(result);
            }
            if (!snapshot.content().equals(PackFingerprints.computePackContentSnapshot(packsRoot).content())
                    || !contextFingerprint.equals(PackValidationCache.contextFingerprint())) {
                Iris.warn("Pack validation cache not written because pack content or validation context changed.");
                return;
            }
            for (int index = 0; index < packDirectories.size(); index++) {
                File packDirectory = packDirectories.get(index);
                if (PackValidationRegistry.getMatching(packDirectory.toPath(),
                        snapshot.packContents().get(packDirectory.getName())) != results.get(index)) {
                    Iris.warn("Pack validation cache not written because validation changed during persistence.");
                    return;
                }
            }
            PackValidationCache.save(
                    Iris.instance.getDataFile("cache", "pack-validation.json").toPath(),
                    snapshot.content(),
                    contextFingerprint,
                    results);
        } catch (IOException | RuntimeException e) {
            Iris.reportError("Could not persist refreshed pack-validation results", e);
        }
    }

    private void reportResult(VolmitSender s, PackValidationResult result) {
        s.sendReport(validationReport(result));
    }

    private static String statusLine(String name, PackValidationResult result) {
        TextKey key = result.isLoadable()
                ? BukkitRuntimeMessages.COMMAND_PACK_STATUS_OK
                : BukkitRuntimeMessages.COMMAND_PACK_STATUS_BROKEN;
        String line = IrisLanguage.text(
                key,
                MessageArgument.trusted("pack", C.WHITE + ChatReport.clean(name)),
                MessageArgument.trusted("blocking", statusCount(result.getBlockingErrors().size(), C.RED)),
                MessageArgument.trusted("warnings", statusCount(result.getWarnings().size(), C.GOLD)));
        return LEADING_COLOR.matcher(line).replaceFirst(statusTone(result).color().toString());
    }

    private static ChatReport.Tone statusTone(PackValidationResult result) {
        return !result.isLoadable() ? ChatReport.Tone.ERROR
                : result.getWarnings().isEmpty() ? ChatReport.Tone.SUCCESS : ChatReport.Tone.WARNING;
    }

    private static String statusCount(int count, C color) {
        return (count == 0 ? C.DARK_GRAY : color) + String.valueOf(count) + C.GRAY;
    }

    static ChatReport validationReport(PackValidationResult result) {
        String packName = String.valueOf(result.getPackName());
        if (!result.isLoadable()) {
            String revalidate = "/iris pack validate " + packName;
            return ChatReport.of(ChatReport.Tone.ERROR, IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_PACK_IS_BROKEN,
                            MessageArgument.untrusted("packName", packName)))
                    .items(result.getBlockingErrors())
                    .hint("Fix the pack, then run " + revalidate, revalidate)
                    .build();
        }
        ChatReport.Tone tone = result.getWarnings().isEmpty() ? ChatReport.Tone.SUCCESS : ChatReport.Tone.WARNING;
        return ChatReport.of(tone, IrisLanguage.text(BukkitRuntimeMessages.COMMAND_PACK_PACK_IS_LOADABLE_WARNINGS,
                        MessageArgument.untrusted("packName", packName),
                        MessageArgument.untrusted("size", String.valueOf(result.getWarnings().size()))))
                .items(result.getWarnings())
                .build();
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

    private void reportPaths(VolmitSender sender, ChatReport.Tone tone, String header, List<String> paths, TextKey label) {
        ChatReport.Builder report = ChatReport.of(tone, header).limit(PATH_REPORT_LIMIT);
        String labelText = IrisLanguage.plain(label);
        for (String path : paths) {
            report.entry(labelText, path);
        }
        sender.sendReport(report.build());
    }
}

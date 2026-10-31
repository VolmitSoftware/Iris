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

import art.arcane.iris.Iris;
import art.arcane.iris.command.handlers.DimensionHandler;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.localization.BukkitCommandMessages;
import art.arcane.iris.localization.C;
import art.arcane.iris.localization.DirectorCommandMessages;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.DatapackIngestService;
import art.arcane.iris.pack.datapack.export.DatapackExportDestinations;
import art.arcane.iris.pack.datapack.export.WorldgenExportMode;
import art.arcane.iris.pack.datapack.export.WorldgenExportProgress;
import art.arcane.iris.pack.datapack.export.WorldgenExportRequest;
import art.arcane.iris.pack.datapack.export.WorldgenExportResult;
import art.arcane.iris.pack.datapack.export.WorldgenExportService;
import art.arcane.iris.pack.datapack.export.WorldgenExportServices;
import art.arcane.iris.pack.datapack.export.WorldgenExportTargets;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;
import java.util.UUID;

@Director(name = "datapack", aliases = {"datapacks", "dp"}, description = "Download & manage external datapack imports", descriptionKey = "iris.director.commanddatapack.director.download_manage_external_datapack_imports_modrinth")
public class CommandDatapack implements DirectorExecutor {
    private static final int RESOURCE_REPORTS = 4;

    @Director(description = "Download/update every datapack listed in a pack dimension's 'datapackImports' and install it into the world so its structures register like vanilla", descriptionKey = "iris.director.commanddatapack.director.download_update_every_datapack_listed_pack_dimension_s_datapackimports_install_it_into", aliases = {"pull"})
    public void ingest(
            @Param(description = "Restart the server when new datapacks are installed (required for new structures to register and generate)", descriptionKey = "iris.director.commanddatapack.param.restart_server_when_new_datapacks_are_installed_required_new_structures_register_generate", defaultValue = "false")
            boolean restart
    ) {
        VolmitSender sender = sender();
        sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_DATAPACK_STARTING_DATAPACK_INGEST));
        J.a(() -> DatapackIngestService.ingestAll(sender, restart));
    }

    @Director(description = "List configured datapack imports and their installed versions", descriptionKey = "iris.director.commanddatapack.director.list_configured_datapack_imports_their_installed_versions", aliases = {"ls"})
    public void list() {
        VolmitSender sender = sender();
        KList<String> configured = DatapackIngestService.collectConfiguredImports();
        List<DatapackIngestService.Entry> installed = DatapackIngestService.installed();

        sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_DATAPACK_CONFIGURED_DATAPACK_IMPORTS, MessageArgument.untrusted("value", configured.size())));
        for (String url : configured) {
            sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_DATAPACK_MESSAGE, MessageArgument.untrusted("url", url)));
        }

        sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_DATAPACK_INSTALLED_DATAPACKS, MessageArgument.untrusted("value", installed.size())));
        for (DatapackIngestService.Entry entry : installed) {
            sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_DATAPACK_MESSAGE_2, MessageArgument.untrusted("value", entry.id), MessageArgument.untrusted("value2", (entry.versionNumber == null ? "?" : entry.versionNumber))));
        }

        if (configured.isEmpty()) {
            sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_DATAPACK_ADD_MODRINTH_URLS_DIMENSION_S_DATAPACKIMPORTS_LIST_THEN_RUN_IRIS));
        }
    }

    @Director(description = "Remove an installed datapack by id (also delete its URL from datapackImports to keep it gone)", descriptionKey = "iris.director.commanddatapack.director.remove_installed_datapack_by_id_also_delete_its_url_from_datapackimports_keep", aliases = {"rm"})
    public void remove(
            @Param(description = "The datapack id (folder name) shown by /iris datapack list", descriptionKey = "iris.director.commanddatapack.param.datapack_id_folder_name_shown_by_iris_datapack_list")
            String id
    ) {
        DatapackIngestService.remove(sender(), id);
    }

    @Director(description = "Export a dimension as a vanilla worldgen datapack", descriptionKey = "iris.director.commanddatapack.director.export_dimension_vanilla_worldgen_datapack")
    public void export(
            @Param(name = "dimension", description = "The dimension to export", descriptionKey = "iris.director.commanddatapack.param.export_dimension", customHandler = DimensionHandler.class)
            IrisDimension dimension,
            @Param(name = "version", description = "Target Minecraft version, 26.3 or later (blank uses the latest)", descriptionKey = "iris.director.commanddatapack.param.export_version", defaultValue = "")
            String version,
            @Param(name = "mode", description = "Output mode: preset, dimension, or overworld", descriptionKey = "iris.director.commanddatapack.param.export_mode", defaultValue = WorldgenExportRequest.DEFAULT_MODE)
            String mode,
            @Param(name = "namespace", description = "Namespace for the exported resources", descriptionKey = "iris.director.commanddatapack.param.export_namespace", defaultValue = WorldgenExportRequest.DEFAULT_NAMESPACE)
            String namespace,
            @Param(name = "output", description = "Output name in the datapack exports folder (blank uses the dimension and version)", descriptionKey = "iris.director.commanddatapack.param.export_output", defaultValue = "")
            String output,
            @Param(name = "format", description = "zip or directory", descriptionKey = "iris.director.commanddatapack.param.export_format", defaultValue = WorldgenExportRequest.DEFAULT_FORMAT)
            String format,
            @Param(name = "objects", description = "Include objects as structure templates", descriptionKey = "iris.director.commanddatapack.param.export_objects", defaultValue = "true")
            boolean objects,
            @Param(name = "replace", description = "Replace an existing export with the same name", descriptionKey = "iris.director.commanddatapack.param.export_replace", defaultValue = "false")
            boolean replace
    ) {
        VolmitSender sender = sender();
        WorldgenExportRequest request = WorldgenExportRequest.builder()
                .pack(dimension.getLoader().getDataFolder().toPath())
                .dimension(dimension.getLoadKey())
                .version(version)
                .mode(mode)
                .namespace(namespace)
                .output(output)
                .format(format)
                .objects(objects)
                .replace(replace)
                .exportRoot(IrisPlatforms.get().dataFolder("exports", "datapacks").toPath())
                .build();
        DataVersion target;
        try {
            target = validateExport(request);
        } catch (IllegalArgumentException invalid) {
            sender.sendMessage(C.RED + IrisLanguage.text(DirectorCommandMessages.COMMAND_DATAPACK_EXPORT_INVALID, MessageArgument.untrusted("reason", invalid.getMessage())));
            return;
        }
        WorldgenExportService service = WorldgenExportServices.get();
        sender.sendMessage(C.GRAY + IrisLanguage.text(DirectorCommandMessages.COMMAND_DATAPACK_EXPORT_STARTED, MessageArgument.untrusted("dimension", request.dimension()), MessageArgument.untrusted("version", target.getVersion())));
        UUID audience = LanguageAudience.current();
        ExportFeedback feedback = new ExportFeedback(sender);
        J.a(() -> LanguageAudience.run(audience, () -> runExport(service, request, feedback)));
    }

    static DataVersion validateExport(WorldgenExportRequest request) {
        DataVersion target = WorldgenExportTargets.resolve(request.version());
        WorldgenExportMode.parse(request.mode());
        DatapackExportDestinations.parseFormat(request.format());
        return target;
    }

    private static void runExport(WorldgenExportService service, WorldgenExportRequest request, ExportFeedback feedback) {
        WorldgenExportResult result;
        try {
            result = service.export(request, feedback);
        } catch (Throwable failure) {
            Iris.reportError("Datapack export failed for '" + request.dimension() + "'.", failure);
            feedback.send(C.RED, IrisLanguage.text(DirectorCommandMessages.COMMAND_DATAPACK_EXPORT_FAILED, MessageArgument.untrusted("reason", IrisLanguage.errorDetail(failure))));
            return;
        }
        reportExport(feedback, result);
    }

    private static void reportExport(ExportFeedback feedback, WorldgenExportResult result) {
        for (ExportDiagnostic diagnostic : result.diagnostics()) {
            feedback.send(diagnosticColor(diagnostic.severity()), IrisLanguage.text(DirectorCommandMessages.COMMAND_DATAPACK_EXPORT_DIAGNOSTIC, MessageArgument.untrusted("key", diagnosticLabel(diagnostic)), MessageArgument.untrusted("message", diagnostic.message())));
        }
        if (result.isSuccess()) {
            feedback.send(C.GREEN, IrisLanguage.text(DirectorCommandMessages.COMMAND_DATAPACK_EXPORT_EXPORTED, MessageArgument.untrusted("path", result.output())));
            return;
        }
        TextKey outcome = result.status() == WorldgenExportResult.Status.REJECTED
                ? DirectorCommandMessages.COMMAND_DATAPACK_EXPORT_REJECTED
                : DirectorCommandMessages.COMMAND_DATAPACK_EXPORT_FAILED;
        feedback.send(C.RED, IrisLanguage.text(outcome, MessageArgument.untrusted("reason", result.message())));
    }

    private static C diagnosticColor(ExportDiagnostic.Severity severity) {
        return switch (severity) {
            case ERROR -> C.RED;
            case WARNING -> C.YELLOW;
            case INFO -> C.GRAY;
        };
    }

    private static String diagnosticLabel(ExportDiagnostic diagnostic) {
        return diagnostic.resource().isEmpty()
                ? diagnostic.limit().name()
                : diagnostic.limit().name() + " " + diagnostic.resource();
    }

    private static final class ExportFeedback implements WorldgenExportProgress {
        private final VolmitSender sender;

        private ExportFeedback(VolmitSender sender) {
            this.sender = sender;
        }

        @Override
        public void stage(String stage) {
            send(C.GRAY, IrisLanguage.text(DirectorCommandMessages.COMMAND_DATAPACK_EXPORT_STAGE, MessageArgument.untrusted("stage", stage)));
        }

        @Override
        public void resources(int done, int total) {
            int stride = Math.max(1, total / RESOURCE_REPORTS);
            if (done != total && done % stride != 0) {
                return;
            }
            send(C.GRAY, IrisLanguage.text(DirectorCommandMessages.COMMAND_DATAPACK_EXPORT_RESOURCES, MessageArgument.untrusted("done", done), MessageArgument.untrusted("total", total)));
        }

        private void send(C color, String message) {
            String line = color + message;
            if (sender.isPlayer()) {
                J.runEntity(sender.player(), () -> sender.sendMessage(line));
                return;
            }
            sender.sendMessage(line);
        }
    }
}

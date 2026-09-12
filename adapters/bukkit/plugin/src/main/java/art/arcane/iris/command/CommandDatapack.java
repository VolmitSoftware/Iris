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

import art.arcane.iris.pack.datapack.DatapackIngestService;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.localization.C;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;

import java.util.List;

import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.BukkitCommandMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
@Director(name = "datapack", aliases = {"datapacks", "dp"}, description = "Download & manage external datapack imports", descriptionKey = "iris.director.commanddatapack.director.download_manage_external_datapack_imports_modrinth")
public class CommandDatapack implements DirectorExecutor {
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
}

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
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.core.localization.BukkitCommandMessagesExtended;
import art.arcane.iris.core.localization.IrisLanguage;
import art.arcane.iris.core.project.StudioEditor;
import art.arcane.iris.core.project.StudioEditorLauncher;
import art.arcane.iris.core.service.StudioSVC;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.util.common.director.DirectorExecutor;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;

@Director(name = "edit", origin = DirectorOrigin.PLAYER, description = "Edit something", descriptionKey = "iris.director.commandedit.director.edit_something")
public class CommandEdit implements DirectorExecutor {
    private boolean noStudio() {
        if (!sender().isPlayer()) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_EDIT_PLAYERS_ONLY));
            return true;
        }
        if (!Iris.service(StudioSVC.class).isProjectOpen()) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_EDIT_NO_STUDIO_WORLD_IS_OPEN));
            return true;
        }
        if (!engine().isStudio()) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_EDIT_YOU_MUST_BE_STUDIO_WORLD));
            return true;
        }
        return editorUnavailable();
    }

    private boolean editorUnavailable() {
        StudioEditor editor = StudioEditorLauncher.configuredEditor();
        if (editor == StudioEditor.NONE) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_EDIT_NO_EDITOR_SET));
            return true;
        }
        if (!StudioEditorLauncher.canLaunch(editor)) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_EDIT_CANNOT_OPEN_FILES_HEADLESS_ENVIRONMENTS));
            return true;
        }
        return false;
    }

    @Director(description = "Edit the biome you specified", descriptionKey = "iris.director.commandedit.director.edit_biome_you_specified", aliases = {"b"}, origin = DirectorOrigin.PLAYER)
    public void biome(@Param(contextual = false, description = "The biome to edit", descriptionKey = "iris.director.commandedit.param.biome_edit") IrisBiome biome) {
        edit(biome,
                BukkitCommandMessagesExtended.COMMAND_EDIT_CANNOT_FIND_FILE_PERHAPS_IT_WAS_NOT_LOADED_DIRECTLY_FROM,
                BukkitCommandMessagesExtended.COMMAND_EDIT_CANT_FIND_FILE_REGISTRANT_DOES_NOT_EXIST);
    }

    @Director(description = "Edit the region you specified", descriptionKey = "iris.director.commandedit.director.edit_region_you_specified", aliases = {"r"}, origin = DirectorOrigin.PLAYER)
    public void region(@Param(contextual = false, description = "The region to edit", descriptionKey = "iris.director.commandedit.param.region_edit") IrisRegion region) {
        edit(region,
                BukkitCommandMessagesExtended.COMMAND_EDIT_CANNOT_FIND_FILE_PERHAPS_IT_WAS_NOT_LOADED_DIRECTLY_FROM_2,
                BukkitCommandMessagesExtended.COMMAND_EDIT_CANT_FIND_FILE_REGISTRANT_DOES_NOT_EXIST_2);
    }

    @Director(description = "Edit the dimension you specified", descriptionKey = "iris.director.commandedit.director.edit_dimension_you_specified", aliases = {"d"}, origin = DirectorOrigin.PLAYER)
    public void dimension(@Param(contextual = false, description = "The dimension to edit", descriptionKey = "iris.director.commandedit.param.dimension_edit") IrisDimension dimension) {
        edit(dimension,
                BukkitCommandMessagesExtended.COMMAND_EDIT_CANNOT_FIND_FILE_PERHAPS_IT_WAS_NOT_LOADED_DIRECTLY_FROM_3,
                BukkitCommandMessagesExtended.COMMAND_EDIT_CANT_FIND_FILE_REGISTRANT_DOES_NOT_EXIST_3);
    }

    private void edit(IrisRegistrant registrant, TextKey fileMissing, TextKey openFailed) {
        if (noStudio()) {
            return;
        }
        try {
            if (registrant == null || registrant.getLoadFile() == null) {
                sender().sendMessage(IrisLanguage.text(fileMissing));
                return;
            }
            openInEditor(registrant);
        } catch (Throwable e) {
            Iris.reportError(e);
            sender().sendMessage(IrisLanguage.text(openFailed));
        }
    }

    private void openInEditor(IrisRegistrant registrant) {
        MessageArgument editor = MessageArgument.untrusted("editor", StudioEditorLauncher.configuredEditor().displayName());
        if (registrant.openInEditor() == null) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_EDIT_COULD_NOT_OPEN_EDITOR, editor));
            return;
        }
        String fileName = registrant.getLoadFile().getName().split("\\Q.\\E")[0];
        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_EDIT_OPENING_IN_EDITOR,
                MessageArgument.untrusted("value", registrant.getTypeName()), MessageArgument.untrusted("value2", fileName), editor));
    }
}

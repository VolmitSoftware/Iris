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
import art.arcane.iris.studio.StudioSVC;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.iris.localization.C;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;


import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.BukkitCommandMessagesExtended;
import art.arcane.volmlib.util.localization.MessageArgument;
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

        if (GraphicsEnvironment.isHeadless()) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_EDIT_CANNOT_OPEN_FILES_HEADLESS_ENVIRONMENTS));
            return true;
        }

        if (!Desktop.isDesktopSupported()) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_EDIT_DESKTOP_IS_NOT_SUPPORTED_BY_THIS_ENVIRONMENT));
            return true;
        }
        return false;
    }


    @Director(description = "Edit the biome you specified", descriptionKey = "iris.director.commandedit.director.edit_biome_you_specified", aliases = {"b"}, origin = DirectorOrigin.PLAYER)
    public void biome(@Param(contextual = false, description = "The biome to edit", descriptionKey = "iris.director.commandedit.param.biome_edit") IrisBiome biome) {
        if (noStudio()) {
            return;
        }
        try {
            if (biome == null || biome.getLoadFile() == null) {
                sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_EDIT_CANNOT_FIND_FILE_PERHAPS_IT_WAS_NOT_LOADED_DIRECTLY_FROM));
                return;
            }
            Desktop.getDesktop().open(biome.getLoadFile());
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_EDIT_OPENING_VSCODE, MessageArgument.untrusted("value", biome.getTypeName()), MessageArgument.untrusted("value2", biome.getLoadFile().getName().split("\\Q.\\E")[0])));
        } catch (Throwable e) {
            Iris.reportError(e);
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_EDIT_CANT_FIND_FILE_REGISTRANT_DOES_NOT_EXIST));
        }
    }

    @Director(description = "Edit the region you specified", descriptionKey = "iris.director.commandedit.director.edit_region_you_specified", aliases = {"r"}, origin = DirectorOrigin.PLAYER)
    public void region(@Param(contextual = false, description = "The region to edit", descriptionKey = "iris.director.commandedit.param.region_edit") IrisRegion region) {
        if (noStudio()) {
            return;
        }
        try {
            if (region == null || region.getLoadFile() == null) {
                sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_EDIT_CANNOT_FIND_FILE_PERHAPS_IT_WAS_NOT_LOADED_DIRECTLY_FROM_2));
                return;
            }
            Desktop.getDesktop().open(region.getLoadFile());
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_EDIT_OPENING_VSCODE_2, MessageArgument.untrusted("value", region.getTypeName()), MessageArgument.untrusted("value2", region.getLoadFile().getName().split("\\Q.\\E")[0])));
        } catch (Throwable e) {
            Iris.reportError(e);
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_EDIT_CANT_FIND_FILE_REGISTRANT_DOES_NOT_EXIST_2));
        }
    }

    @Director(description = "Edit the dimension you specified", descriptionKey = "iris.director.commandedit.director.edit_dimension_you_specified", aliases = {"d"}, origin = DirectorOrigin.PLAYER)
    public void dimension(@Param(contextual = false, description = "The dimension to edit", descriptionKey = "iris.director.commandedit.param.dimension_edit") IrisDimension dimension) {
        if (noStudio()) {
            return;
        }
        try {
            if (dimension == null || dimension.getLoadFile() == null) {
                sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_EDIT_CANNOT_FIND_FILE_PERHAPS_IT_WAS_NOT_LOADED_DIRECTLY_FROM_3));
                return;
            }
            Desktop.getDesktop().open(dimension.getLoadFile());
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_EDIT_OPENING_VSCODE_3, MessageArgument.untrusted("value", dimension.getTypeName()), MessageArgument.untrusted("value2", dimension.getLoadFile().getName().split("\\Q.\\E")[0])));
        } catch (Throwable e) {
            Iris.reportError(e);
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_EDIT_CANT_FIND_FILE_REGISTRANT_DOES_NOT_EXIST_3));
        }
    }

}

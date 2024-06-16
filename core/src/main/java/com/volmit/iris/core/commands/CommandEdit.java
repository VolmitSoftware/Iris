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

package com.volmit.iris.core.commands;

import com.volmit.iris.Iris;
import com.volmit.iris.core.service.StudioSVC;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.util.decree.DecreeExecutor;
import com.volmit.iris.util.decree.DecreeOrigin;
import com.volmit.iris.util.decree.annotations.Decree;
import com.volmit.iris.util.decree.annotations.Param;
import com.volmit.iris.util.format.C;

import java.awt.*;


@Decree(name = "edit", origin = DecreeOrigin.PLAYER, studio = true, description = "Edit something")
public class CommandEdit implements DecreeExecutor {

    private boolean noStudio() {
        if (!sender().isPlayer()) {
            sender().sendMessage(C.RED + "Players only!");
            return true;
        }
        if (!Iris.service(StudioSVC.class).isProjectOpen()) {
            sender().sendMessage(C.RED + "No studio world is open!");
            return true;
        }
        if (!engine().isStudio()) {
            sender().sendMessage(C.RED + "You must be in a studio world!");
            return true;
        }
        return false;
    }


    @Decree(description = "Edit the biome you specified", aliases = {"b"}, origin = DecreeOrigin.PLAYER)
    public void biome(@Param(contextual = false, description = "The biome to edit") IrisBiome biome) {
        if (noStudio()) {
            return;
        }
        try {
            if (biome == null || biome.getLoadFile() == null) {
                sender().sendMessage(C.GOLD + "Cannot find the file; Perhaps it was not loaded directly from a file?");
                return;
            }
            Desktop.getDesktop().open(biome.getLoadFile());
            sender().sendMessage(C.GREEN + "Opening " + biome.getTypeName() + " " + biome.getLoadFile().getName().split("\\Q.\\E")[0] + " in VSCode! ");
        } catch (Throwable e) {
            Iris.reportError(e);
            sender().sendMessage(C.RED + "Cant find the file. Or registrant does not exist");
        }
    }

    @Decree(description = "Edit the region you specified", aliases = {"r"}, origin = DecreeOrigin.PLAYER)
    public void region(@Param(contextual = false, description = "The region to edit") IrisRegion region) {
        if (noStudio()) {
            return;
        }
        try {
            if (region == null || region.getLoadFile() == null) {
                sender().sendMessage(C.GOLD + "Cannot find the file; Perhaps it was not loaded directly from a file?");
                return;
            }
            Desktop.getDesktop().open(region.getLoadFile());
            sender().sendMessage(C.GREEN + "Opening " + region.getTypeName() + " " + region.getLoadFile().getName().split("\\Q.\\E")[0] + " in VSCode! ");
        } catch (Throwable e) {
            Iris.reportError(e);
            sender().sendMessage(C.RED + "Cant find the file. Or registrant does not exist");
        }
    }

    @Decree(description = "Edit the dimension you specified", aliases = {"d"}, origin = DecreeOrigin.PLAYER)
    public void dimension(@Param(contextual = false, description = "The dimension to edit") IrisDimension dimension) {
        if (noStudio()) {
            return;
        }
        try {
            if (dimension == null || dimension.getLoadFile() == null) {
                sender().sendMessage(C.GOLD + "Cannot find the file; Perhaps it was not loaded directly from a file?");
                return;
            }
            Desktop.getDesktop().open(dimension.getLoadFile());
            sender().sendMessage(C.GREEN + "Opening " + dimension.getTypeName() + " " + dimension.getLoadFile().getName().split("\\Q.\\E")[0] + " in VSCode! ");
        } catch (Throwable e) {
            Iris.reportError(e);
            sender().sendMessage(C.RED + "Cant find the file. Or registrant does not exist");
        }
    }

    @Decree(description = "Edit the cave file you specified", aliases = {"c"}, origin = DecreeOrigin.PLAYER)
    public void cave(@Param(contextual = false, description = "The cave to edit") IrisCave cave) {
        if (noStudio()) {
            return;
        }
        try {
            if (cave == null || cave.getLoadFile() == null) {
                sender().sendMessage(C.GOLD + "Cannot find the file; Perhaps it was not loaded directly from a file?");
                return;
            }
            Desktop.getDesktop().open(cave.getLoadFile());
            sender().sendMessage(C.GREEN + "Opening " + cave.getTypeName() + " " + cave.getLoadFile().getName().split("\\Q.\\E")[0] + " in VSCode! ");
        } catch (Throwable e) {
            Iris.reportError(e);
            sender().sendMessage(C.RED + "Cant find the file. Or registrant does not exist");
        }
    }

    @Decree(description = "Edit the structure file you specified", aliases = {"jigsawstructure", "structure"}, origin = DecreeOrigin.PLAYER)
    public void jigsaw(@Param(contextual = false, description = "The jigsaw structure to edit") IrisJigsawStructure jigsaw) {
        if (noStudio()) {
            return;
        }
        try {
            if (jigsaw == null || jigsaw.getLoadFile() == null) {
                sender().sendMessage(C.GOLD + "Cannot find the file; Perhaps it was not loaded directly from a file?");
                return;
            }
            Desktop.getDesktop().open(jigsaw.getLoadFile());
            sender().sendMessage(C.GREEN + "Opening " + jigsaw.getTypeName() + " " + jigsaw.getLoadFile().getName().split("\\Q.\\E")[0] + " in VSCode! ");
        } catch (Throwable e) {
            Iris.reportError(e);
            sender().sendMessage(C.RED + "Cant find the file. Or registrant does not exist");
        }
    }

    @Decree(description = "Edit the pool file you specified", aliases = {"jigsawpool", "pool"}, origin = DecreeOrigin.PLAYER)
    public void jigsawPool(@Param(contextual = false, description = "The jigsaw pool to edit") IrisJigsawPool pool) {
        if (noStudio()) {
            return;
        }
        try {
            if (pool == null || pool.getLoadFile() == null) {
                sender().sendMessage(C.GOLD + "Cannot find the file; Perhaps it was not loaded directly from a file?");
                return;
            }
            Desktop.getDesktop().open(pool.getLoadFile());
            sender().sendMessage(C.GREEN + "Opening " + pool.getTypeName() + " " + pool.getLoadFile().getName().split("\\Q.\\E")[0] + " in VSCode! ");
        } catch (Throwable e) {
            Iris.reportError(e);
            sender().sendMessage(C.RED + "Cant find the file. Or registrant does not exist");
        }
    }

    @Decree(description = "Edit the jigsaw piece file you specified", aliases = {"jigsawpiece", "piece"}, origin = DecreeOrigin.PLAYER)
    public void jigsawPiece(@Param(contextual = false, description = "The jigsaw piece to edit") IrisJigsawPiece piece) {
        if (noStudio()) {
            return;
        }
        try {
            if (piece == null || piece.getLoadFile() == null) {
                sender().sendMessage(C.GOLD + "Cannot find the file; Perhaps it was not loaded directly from a file?");
                return;
            }
            Desktop.getDesktop().open(piece.getLoadFile());
            sender().sendMessage(C.GREEN + "Opening " + piece.getTypeName() + " " + piece.getLoadFile().getName().split("\\Q.\\E")[0] + " in VSCode! ");
        } catch (Throwable e) {
            Iris.reportError(e);
            sender().sendMessage(C.RED + "Cant find the file. Or registrant does not exist");
        }
    }

}

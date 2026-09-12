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
import art.arcane.iris.studio.view.GuiHost;
import art.arcane.iris.pack.loading.IrisRegistrant;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.modded.localization.ModdedCommandMessages;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.volmlib.util.localization.MessageArgument;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.awt.Desktop;
import java.io.File;

final class ModdedEditCommands {

    private ModdedEditCommands() {
    }

    static int editBiome(CommandSourceStack source, String key) {
        Engine engine = IrisModdedCommands.engineFor(source.getLevel());
        if (engine == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_DIMENSION_IS_NOT_GENERATED_BY_IRIS));
            return 0;
        }
        IrisBiome biome;
        if (key == null || key.isBlank()) {
            ServerPlayer player = source.getPlayer();
            if (player == null) {
                IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_CONSOLE_MUST_NAME_BIOME_IRIS_EDIT_BIOME_KEY));
                return 0;
            }
            BlockPos pos = player.blockPosition();
            try {
                biome = engine.getBiome(pos.getX(), pos.getY() - engine.getMinHeight(), pos.getZ());
            } catch (Throwable e) {
                IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_BIOME_LOOKUP_FAILED, MessageArgument.untrusted("value", e.getClass().getSimpleName())));
                return 0;
            }
        } else {
            biome = engine.getData().getBiomeLoader().load(key.trim());
            if (biome == null) {
                IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_UNKNOWN_BIOME, MessageArgument.untrusted("key", key)));
                return 0;
            }
        }
        return openJson(source, biome);
    }

    static int editRegion(CommandSourceStack source, String key) {
        Engine engine = IrisModdedCommands.engineFor(source.getLevel());
        if (engine == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_DIMENSION_IS_NOT_GENERATED_BY_IRIS_2));
            return 0;
        }
        IrisRegion region;
        if (key == null || key.isBlank()) {
            ServerPlayer player = source.getPlayer();
            if (player == null) {
                IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_CONSOLE_MUST_NAME_REGION_IRIS_EDIT_REGION_KEY));
                return 0;
            }
            BlockPos pos = player.blockPosition();
            try {
                region = engine.getRegion(
                        pos.getX(), pos.getY() - engine.getMinHeight(), pos.getZ());
            } catch (Throwable e) {
                IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_REGION_LOOKUP_FAILED, MessageArgument.untrusted("value", e.getClass().getSimpleName())));
                return 0;
            }
        } else {
            region = engine.getData().getRegionLoader().load(key.trim());
            if (region == null) {
                IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_UNKNOWN_REGION, MessageArgument.untrusted("key", key)));
                return 0;
            }
        }
        return openJson(source, region);
    }

    static int editDimension(CommandSourceStack source) {
        Engine engine = IrisModdedCommands.engineFor(source.getLevel());
        if (engine == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_DIMENSION_IS_NOT_GENERATED_BY_IRIS_3));
            return 0;
        }
        return openJson(source, engine.getDimension());
    }

    private static int openJson(CommandSourceStack source, IrisRegistrant registrant) {
        if (!GuiHost.isAvailable() || !Desktop.isDesktopSupported()) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_CANNOT_OPEN_FILES_HERE, MessageArgument.untrusted("value", ModdedGuiHost.guiUnavailableReason())));
            return 0;
        }
        if (registrant == null || registrant.getLoadFile() == null || !registrant.getLoadFile().isFile()) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_CANNOT_FIND_FILE_PERHAPS_IT_WAS_NOT_LOADED_DIRECTLY_FROM));
            return 0;
        }
        File file = registrant.getLoadFile();
        try {
            Desktop.getDesktop().open(file);
        } catch (Throwable e) {
            ModdedIrisLog.error("Iris edit failed to open {}", file, e);
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_COULD_NOT_OPEN, MessageArgument.untrusted("value", file.getName()), MessageArgument.untrusted("value2", e.getClass().getSimpleName())));
            return 0;
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_OPENING_YOUR_EDITOR, MessageArgument.untrusted("value", registrant.getTypeName()), MessageArgument.untrusted("value2", file.getName())));
        return 1;
    }
}

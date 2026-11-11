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

package art.arcane.iris.core.modules;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.localization.IrisLanguage;
import art.arcane.iris.core.localization.IrisMessages;
import art.arcane.iris.core.modules.manifest.ModuleUpdateReport;
import art.arcane.volmlib.util.localization.MessageArgument;

import java.util.ArrayList;
import java.util.List;

/**
 * Takes the module updates a pack kept back because they are more than a patch, for the update commands: a pack's
 * author runs one before bringing the pack's settings in line with the new versions, a world's owner after deciding that
 * new chunks may differ from the old ones.
 */
public final class KeptModuleUpdates {
    private KeptModuleUpdates() {
    }

    /**
     * Updates the jars of the pack folder of {@code data} and loads them into {@code data}; a studio world of the pack
     * hotloads on its own when the jars change. Returns what to tell the sender, one line each.
     */
    public static List<String> takeForPack(IrisData data) {
        List<String> lines = take(data, false);
        if (data.getModules().reloadIfJarsChanged()) {
            data.hotloaded();
        }
        return lines;
    }

    /**
     * What the pack of {@code data} kept back when its modules loaded, for its author opening a studio: the server's
     * console does not say it, so whoever only runs the pack is not told to update it. Empty when nothing was kept or
     * the modules fail to load, which opening the studio reports itself.
     */
    public static List<String> keptAtLoad(IrisData data) {
        PackModuleSet modules = data.getModules();
        try {
            modules.pack();
        } catch (PackModulesException failure) {
            return List.of();
        }
        ModuleUpdateReport report = modules.lastUpdates();
        if (report.kept().isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        lines.add(IrisLanguage.text(IrisMessages.PACK_UPDATES_WAITING, MessageArgument.untrusted("pack", modules.label())));
        report.kept().forEach(change -> lines.add(IrisLanguage.text(IrisMessages.STUDIO_MODULES_KEPT,
                MessageArgument.untrusted("change", change.describe()))));
        if (report.keptOutOfDate()) {
            lines.add(IrisLanguage.text(IrisMessages.PACK_UPDATES_HOW_TO_TAKE,
                    MessageArgument.untrusted("pack", data.getDataFolder().getName())));
        }
        return lines;
    }

    /** Like {@link #takeForPack} for a running world, which keeps the modules it loaded until the server restarts. */
    public static List<String> takeForWorld(IrisData data) {
        return take(data, true);
    }

    private static List<String> take(IrisData data, boolean loadsAtRestart) {
        PackModuleSet modules = data.getModules();
        ModuleUpdateReport report = modules.takeEveryUpdate();
        MessageArgument pack = MessageArgument.untrusted("pack", modules.label());
        if (!report.managed()) {
            return List.of(IrisLanguage.text(IrisMessages.PACK_UPDATE_UNMANAGED, pack));
        }
        if (report.written().isEmpty() && report.kept().isEmpty()) {
            return List.of(IrisLanguage.text(IrisMessages.PACK_UPDATE_CURRENT, pack));
        }
        List<String> lines = new ArrayList<>();
        lines.add(IrisLanguage.text(IrisMessages.PACK_UPDATE_HEADER, pack));
        report.written().forEach(change -> lines.add(IrisLanguage.text(IrisMessages.PACK_UPDATE_WRITTEN,
                MessageArgument.untrusted("change", change.describe()))));
        report.kept().forEach(change -> lines.add(IrisLanguage.text(IrisMessages.STUDIO_MODULES_KEPT,
                MessageArgument.untrusted("change", change.describe()))));
        if (loadsAtRestart && !report.written().isEmpty()) {
            lines.add(IrisLanguage.text(IrisMessages.PACK_UPDATE_RESTART));
        }
        return lines;
    }
}

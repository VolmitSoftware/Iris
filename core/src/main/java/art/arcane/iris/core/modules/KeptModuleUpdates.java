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
 * Takes the module updates a world's pack kept because they change its terrain, for the command its owner runs after
 * deciding that new chunks may differ from the old ones. The jars change on disk; the world loads them at the next
 * server start.
 */
public final class KeptModuleUpdates {
    private KeptModuleUpdates() {
    }

    /** Updates the jars of the pack of {@code data} and returns what to tell the sender, one line each. */
    public static List<String> take(IrisData data) {
        PackModuleSet modules = data.getModules();
        ModuleUpdateReport report = modules.takeTerrainChanges();
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
        if (!report.written().isEmpty()) {
            lines.add(IrisLanguage.text(IrisMessages.PACK_UPDATE_RESTART));
        }
        return lines;
    }
}

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
import art.arcane.volmlib.util.localization.MessageArgument;
import com.volmit.ModuleInfo;
import com.volmit.host.DiscoveredModule;

import java.util.ArrayList;
import java.util.List;

/** The modules of a pack's jars, as chat lines with color codes: each with its version, id and jar. */
public final class PackModulesListing {
    /** @param lines the listing, or one line with the reason when {@code loaded} is false */
    public record Listing(boolean loaded, List<String> lines) {
    }

    private PackModulesListing() {
    }

    /** Loads the pack's modules when nothing has yet. */
    public static Listing describe(IrisData data) {
        String packName = data.getDataFolder().getName();
        try {
            return new Listing(true, moduleLines(packName, data.getModules().pack().packModules()));
        } catch (PackModulesException failure) {
            return new Listing(false, List.of(failureLine(packName, failure)));
        }
    }

    private static List<String> moduleLines(String packName, List<DiscoveredModule> modules) {
        List<String> lines = new ArrayList<>();
        lines.add(IrisLanguage.text(IrisMessages.STUDIO_MODULES_HEADER,
                MessageArgument.untrusted("pack", packName), MessageArgument.trusted("count", modules.size())));
        if (modules.isEmpty()) {
            lines.add(IrisLanguage.text(IrisMessages.STUDIO_MODULES_NONE));
        }
        modules.forEach(module -> lines.add(moduleLine(module)));
        return lines;
    }

    private static String failureLine(String packName, PackModulesException failure) {
        String reason = failure.getCause() == null ? failure.getMessage() : failure.getCause().getMessage();
        return IrisLanguage.text(IrisMessages.STUDIO_MODULES_FAILED,
                MessageArgument.untrusted("pack", packName), MessageArgument.untrusted("reason", reason));
    }

    private static String moduleLine(DiscoveredModule module) {
        ModuleInfo info = module.info();
        return IrisLanguage.text(IrisMessages.STUDIO_MODULES_ENTRY,
                MessageArgument.untrusted("name", info.name()),
                MessageArgument.untrusted("version", info.version()),
                MessageArgument.untrusted("id", info.id().toString()),
                MessageArgument.untrusted("jar", module.origin()));
    }
}

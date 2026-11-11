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
import java.util.stream.Collectors;

/** The modules a pack loads, as chat lines with color codes: the built-in ones by name, the pack's own with version, id and jar. */
public final class PackModulesListing {
    private static final String NAME_SEPARATOR = ", ";

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
        List<DiscoveredModule> builtIns = modules.stream().filter(DiscoveredModule::isBuiltIn).toList();
        List<DiscoveredModule> ownModules = modules.stream().filter(module -> !module.isBuiltIn()).toList();

        List<String> lines = new ArrayList<>();
        lines.add(IrisLanguage.text(IrisMessages.STUDIO_MODULES_HEADER,
                MessageArgument.untrusted("pack", packName), MessageArgument.trusted("count", modules.size())));
        lines.add(builtInLine(builtIns));
        lines.addAll(ownModuleLines(ownModules));
        return lines;
    }

    private static String failureLine(String packName, PackModulesException failure) {
        String reason = failure.getCause() == null ? failure.getMessage() : failure.getCause().getMessage();
        return IrisLanguage.text(IrisMessages.STUDIO_MODULES_FAILED,
                MessageArgument.untrusted("pack", packName), MessageArgument.untrusted("reason", reason));
    }

    private static String builtInLine(List<DiscoveredModule> builtIns) {
        String names = builtIns.isEmpty()
                ? IrisLanguage.text(IrisMessages.STUDIO_MODULES_NONE)
                : builtIns.stream().map(module -> module.info().name()).collect(Collectors.joining(NAME_SEPARATOR));
        return IrisLanguage.text(IrisMessages.STUDIO_MODULES_BUILT_IN, MessageArgument.untrusted("modules", names));
    }

    private static List<String> ownModuleLines(List<DiscoveredModule> ownModules) {
        if (ownModules.isEmpty()) {
            return List.of(IrisLanguage.text(IrisMessages.STUDIO_MODULES_FROM_PACK_NONE));
        }
        List<String> lines = new ArrayList<>();
        lines.add(IrisLanguage.text(IrisMessages.STUDIO_MODULES_FROM_PACK));
        ownModules.forEach(module -> lines.add(ownModuleLine(module)));
        return lines;
    }

    private static String ownModuleLine(DiscoveredModule module) {
        ModuleInfo info = module.info();
        return IrisLanguage.text(IrisMessages.STUDIO_MODULES_ENTRY,
                MessageArgument.untrusted("name", info.name()),
                MessageArgument.untrusted("version", info.version()),
                MessageArgument.untrusted("id", info.id().toString()),
                MessageArgument.untrusted("jar", module.origin()));
    }
}

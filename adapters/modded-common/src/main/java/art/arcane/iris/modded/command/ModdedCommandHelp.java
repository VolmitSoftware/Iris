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

import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.IrisMessages;
import art.arcane.iris.modded.localization.ModdedHelpMessages;
import art.arcane.volmlib.util.director.help.DirectorHelpMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static art.arcane.iris.modded.command.ModdedCommandFeedback.BACK;
import static art.arcane.iris.modded.command.ModdedCommandFeedback.CATEGORY;
import static art.arcane.iris.modded.command.ModdedCommandFeedback.DARK_GREEN;
import static art.arcane.iris.modded.command.ModdedCommandFeedback.DESCRIPTION;
import static art.arcane.iris.modded.command.ModdedCommandFeedback.DESCRIPTION_ICON;
import static art.arcane.iris.modded.command.ModdedCommandFeedback.EXAMPLE_ICON;
import static art.arcane.iris.modded.command.ModdedCommandFeedback.HOVER_TYPE;
import static art.arcane.iris.modded.command.ModdedCommandFeedback.OPTIONAL;
import static art.arcane.iris.modded.command.ModdedCommandFeedback.PARAMETER;
import static art.arcane.iris.modded.command.ModdedCommandFeedback.PARAMETER_ALT;
import static art.arcane.iris.modded.command.ModdedCommandFeedback.REQUIRED;
import static art.arcane.iris.modded.command.ModdedCommandFeedback.REQUIRED_TEXT;
import static art.arcane.iris.modded.command.ModdedCommandFeedback.USAGE;
import static art.arcane.iris.modded.command.ModdedCommandFeedback.USAGE_ICON;

final class ModdedCommandHelp {
    private static final int PAGE_SIZE = 17;
    private static final int PAGE_BUTTON_WIDTH = 10;
    private static final TextKey COMMAND_UNREGISTERED = TextKey.of(
            "iris.modded.help.entry.command.unregistered",
            "Print every structure excluded from goto completion and its exact reason to the server console");
    private static final Map<String, List<Entry>> SECTIONS = new LinkedHashMap<>();

    static {
        SECTIONS.put("", List.of(
                Entry.command("version", "", ModdedHelpMessages.COMMAND_VERSION_PRINT_VERSION_INFORMATION),
                Entry.command("info", "[dimension]", ModdedHelpMessages.COMMAND_INFO_LIST_LOADED_IRIS_DIMENSIONS_AND_PACK_DETAILS),
                Entry.command("what", "[here|biome|region|block|hand|markers]", ModdedHelpMessages.COMMAND_WHAT_INSPECT_THE_IRIS_BIOME_REGION_CAVE_BIOME_SURFACE_AND_CHUNK_AT_YOUR),
                Entry.group("find", ModdedHelpMessages.GROUP_FIND_FIND_AND_TELEPORT_TO_IRIS_BIOMES_REGIONS_OBJECTS_IRIS_STRUCTURES_NATIVE_STRUCTURES, "goto"),
                Entry.command("teleport", "<dimension> [player]", ModdedHelpMessages.COMMAND_TP_TELEPORT_YOURSELF_OR_A_NAMED_PLAYER_INTO_A_LOADED_IRIS_DIMENSION, "tp"),
                Entry.command("evacuate", "[dimension]", ModdedHelpMessages.COMMAND_EVACUATE_TELEPORT_EVERY_PLAYER_OUT_OF_AN_IRIS_DIMENSION_TO_THE_PRIMARY_WORLD),
                Entry.command("seed", "", ModdedHelpMessages.COMMAND_SEED_PRINT_WORLD_AND_ENGINE_SEED_INFORMATION),
                Entry.command("debug", "", ModdedHelpMessages.COMMAND_DEBUG_TOGGLE_IRIS_DEBUG_LOGGING_AND_SAVE_SETTINGS_JSON),
                Entry.command("reload", "", ModdedHelpMessages.COMMAND_RELOAD_RELOAD_SETTINGS_JSON_ALSO_HOTLOADED_AUTOMATICALLY_EVERY_3S),
                Entry.command("download", "<pack=overworld|pack=underworld|link=<zip-url>>", ModdedHelpMessages.COMMAND_DOWNLOAD_DOWNLOAD_A_PACK_PROJECT, "dl"),
                Entry.command("metrics", "", ModdedHelpMessages.COMMAND_METRICS_PRINT_GENERATION_METRICS_FOR_YOUR_CURRENT_IRIS_DIMENSION, "measure"),
                Entry.command("regen", "[radius]", ModdedHelpMessages.COMMAND_REGEN_DELETE_AND_REGENERATE_NEARBY_CHUNKS_IN_PLACE, "rg"),
                Entry.group("pregen", ModdedHelpMessages.GROUP_PREGEN_PREGENERATE_AN_IRIS_DIMENSION, "pregenerate"),
                Entry.command("wand", "", ModdedHelpMessages.COMMAND_WAND_GET_AN_IRIS_OBJECT_WAND),
                Entry.command("dust", "", ModdedHelpMessages.COMMAND_DUST_GET_DUST_THAT_REVEALS_OBJECT_PLACEMENTS, "d"),
                Entry.group("object", ModdedHelpMessages.GROUP_OBJECT_OBJECT_WAND_SAVE_PASTE_ANALYZE_AND_UNDO_TOOLS, "o"),
                Entry.group("edit", ModdedHelpMessages.GROUP_EDIT_OPEN_PACK_BIOME_REGION_AND_DIMENSION_JSON_FILES_IN_YOUR_DESKTOP_EDITOR),
                Entry.command("create", "<name> [pack|pack:dimensionKey] [seed]", ModdedHelpMessages.COMMAND_CREATE_CREATE_AND_INJECT_A_PERSISTENT_IRIS_DIMENSION_QUOTE_PACK_DIMENSIONKEY_TO_PICK, "c"),
                Entry.command("height", "", ModdedHelpMessages.COMMAND_HEIGHT_PRINT_WORLD_HEIGHT),
                Entry.command("worlds", "", ModdedHelpMessages.COMMAND_WORLDS_LIST_WORLD_ACCESS, "accesslist"),
                Entry.group("studio", ModdedHelpMessages.GROUP_STUDIO_PACK_PROJECT_CREATION_PACKAGING_AND_REPORTS, "std", "s"),
                Entry.group("pack", ModdedHelpMessages.GROUP_PACK_PACK_VALIDATION_AND_MAINTENANCE, "pk"),
                Entry.group("world", ModdedHelpMessages.GROUP_WORLD_RUNTIME_IRIS_DIMENSION_CREATION_REMOVAL_AND_STATUS, "w"),
                Entry.group("datapack", ModdedHelpMessages.GROUP_DATAPACK_WORLD_DATAPACK_INSTALL_AND_STATUS_HELPERS, "datapacks", "dp"),
                Entry.group("structure", ModdedHelpMessages.GROUP_STRUCTURE_IRIS_STRUCTURE_INDEX_INFO_AND_PLACEMENT_TOOLS, "struct", "str"),
                Entry.command("goldenhash", "[radius] [threads] [capture|verify]", ModdedHelpMessages.COMMAND_GOLDENHASH_GENERATE_DETERMINISTIC_BLOCK_HASHES_FOR_PARITY_TESTING, "gold"),
                Entry.group("developer", ModdedHelpMessages.GROUP_DEVELOPER_DEVELOPER_DIAGNOSTICS_NETWORK_INTERFACES_REGION_FILE_SCAN, "dev")
        ));
        SECTIONS.put("what", List.of(
                Entry.command("here", "", ModdedHelpMessages.COMMAND_WHAT_HERE_INSPECT_CURRENT_IRIS_CONTEXT),
                Entry.command("biome", "", ModdedHelpMessages.COMMAND_WHAT_BIOME_INSPECT_CURRENT_BIOME),
                Entry.command("region", "", ModdedHelpMessages.COMMAND_WHAT_REGION_INSPECT_CURRENT_REGION),
                Entry.command("block", "", ModdedHelpMessages.COMMAND_WHAT_BLOCK_INSPECT_TARGET_BLOCK),
                Entry.command("hand", "", ModdedHelpMessages.COMMAND_WHAT_HAND_INSPECT_HELD_ITEM),
                Entry.command("markers", "<marker>", ModdedHelpMessages.COMMAND_WHAT_MARKERS_REVEAL_NEARBY_MARKERS)
        ));
        SECTIONS.put("find", List.of(
                Entry.command("biome", "<key>", ModdedHelpMessages.COMMAND_BIOME_FIND_AN_IRIS_BIOME),
                Entry.command("region", "<key>", ModdedHelpMessages.COMMAND_REGION_FIND_AN_IRIS_REGION),
                Entry.command("object", "<key>", ModdedHelpMessages.COMMAND_OBJECT_FIND_AN_OBJECT_PLACEMENT),
                Entry.command("unregistered", "", COMMAND_UNREGISTERED),
                Entry.command("structure", "<key>", ModdedHelpMessages.COMMAND_STRUCTURE_FIND_AN_IRIS_PLACED_OR_NATIVE_DATAPACK_STRUCTURE),
                Entry.command("poi", "<type>", ModdedHelpMessages.COMMAND_POI_FIND_A_SUPPORTED_POINT_OF_INTEREST)
        ));
        SECTIONS.put("goto", SECTIONS.get("find"));
        SECTIONS.put("edit", List.of(
                Entry.command("biome", "[key]", ModdedHelpMessages.COMMAND_BIOME_OPEN_A_BIOME_JSON_IN_YOUR_DESKTOP_EDITOR_NO_KEY_OPENS_THE, "b"),
                Entry.command("region", "[key]", ModdedHelpMessages.COMMAND_REGION_OPEN_A_REGION_JSON_IN_YOUR_DESKTOP_EDITOR_NO_KEY_OPENS_THE, "r"),
                Entry.command("dimension", "", ModdedHelpMessages.COMMAND_DIMENSION_OPEN_THE_CURRENT_PACK_S_DIMENSION_JSON_IN_YOUR_DESKTOP_EDITOR, "d")
        ));
        SECTIONS.put("pregen", List.of(
                Entry.command("start", "<radius> [dimension] [at] [x] [z] [gui] [sync] [nocache]", ModdedHelpMessages.COMMAND_START_START_PREGENERATION_RADIUS_IN_BLOCKS_RESUMABLE_CHECKPOINT_CACHE_ON_BY_DEFAULT_CENTER),
                Entry.command("stop", "", ModdedHelpMessages.COMMAND_STOP_STOP_THE_ACTIVE_PREGENERATION_TASK, "x"),
                Entry.command("pause", "", ModdedHelpMessages.COMMAND_PAUSE_PAUSE_OR_RESUME_PREGENERATION, "resume"),
                Entry.command("status", "", ModdedHelpMessages.COMMAND_STATUS_SHOW_PREGENERATION_STATUS)
        ));
        SECTIONS.put("pregenerate", SECTIONS.get("pregen"));
        SECTIONS.put("object", List.of(
                Entry.command("wand", "", ModdedHelpMessages.COMMAND_WAND_GET_AN_IRIS_OBJECT_WAND_2),
                Entry.command("dust", "", ModdedHelpMessages.COMMAND_DUST_GET_DUST_THAT_REVEALS_OBJECT_PLACEMENTS, "d"),
                Entry.command("save", "[overwrite] <name>", ModdedHelpMessages.COMMAND_SAVE_SAVE_THE_SELECTED_WAND_VOLUME_AS_AN_OBJECT),
                Entry.command("paste", "[at] [x] [y] [z] [rotate] [degrees] <key>", ModdedHelpMessages.COMMAND_PASTE_PASTE_AN_OBJECT_AT_YOUR_POSITION_OR_A_GIVEN_POSITION_OPTIONALLY_ROTATED),
                Entry.command("expand", "[amount]", ModdedHelpMessages.COMMAND_EXPAND_EXPAND_THE_WAND_SELECTION_IN_YOUR_LOOKING_DIRECTION),
                Entry.command("contract", "[amount]", ModdedHelpMessages.COMMAND_CONTRACT_CONTRACT_THE_WAND_SELECTION_IN_YOUR_LOOKING_DIRECTION, "-"),
                Entry.command("shift", "[amount]", ModdedHelpMessages.COMMAND_SHIFT_SHIFT_THE_WAND_SELECTION_IN_YOUR_LOOKING_DIRECTION),
                Entry.command("position1", "[look]", ModdedHelpMessages.COMMAND_POSITION1_SET_SELECTION_POINT_1, "p1"),
                Entry.command("position2", "[look]", ModdedHelpMessages.COMMAND_POSITION2_SET_SELECTION_POINT_2, "p2"),
                Entry.command("x+y", "", ModdedHelpMessages.COMMAND_X_Y_AUTOSELECT_UP_AND_OUT, "xpy"),
                Entry.command("x&y", "", ModdedHelpMessages.COMMAND_X_Y_AUTOSELECT_UP_DOWN_AND_OUT, "xay"),
                Entry.command("analyze", "<key>", ModdedHelpMessages.COMMAND_ANALYZE_SHOW_OBJECT_COMPOSITION),
                Entry.command("shrink", "<key>", ModdedHelpMessages.COMMAND_SHRINK_SHRINK_AN_OBJECT_TO_ITS_MINIMUM_SIZE),
                Entry.command("plausibilize", "<key|prefix/> [dryrun=true] [reach=N]", ModdedHelpMessages.COMMAND_PLAUSIBILIZE_GROW_BRANCHES_SO_TREE_LEAVES_SURVIVE_VANILLA_DECAY),
                Entry.command("undo", "[amount]", ModdedHelpMessages.COMMAND_UNDO_UNDO_PASTED_OBJECTS, "u"),
                Entry.command("we", "", ModdedHelpMessages.COMMAND_OBJECT_WE_BUKKIT_ONLY),
                Entry.command("studio", "", ModdedHelpMessages.COMMAND_OBJECT_STUDIO_BUKKIT_ONLY),
                Entry.command("convert", "", ModdedHelpMessages.COMMAND_OBJECT_CONVERT_BUKKIT_ONLY)
        ));
        SECTIONS.put("o", SECTIONS.get("object"));
        SECTIONS.put("studio", List.of(
                Entry.command("create", "[name] [template]", ModdedHelpMessages.COMMAND_CREATE_CREATE_A_NEW_PACK_PROJECT, "+"),
                Entry.command("package", "[pack]", ModdedHelpMessages.COMMAND_PACKAGE_PACKAGE_A_DIMENSION_INTO_A_COMPRESSED_FORMAT, "pkg"),
                Entry.command("version", "[pack]", ModdedHelpMessages.COMMAND_VERSION_PRINT_A_PACK_VERSION),
                Entry.command("regions", "[radius]", ModdedHelpMessages.COMMAND_REGIONS_CALCULATE_NEARBY_REGION_DISTRIBUTION),
                Entry.command("open", "<pack> [seed]", ModdedHelpMessages.COMMAND_OPEN_OPEN_A_TEMPORARY_STUDIO_DIMENSION_FOR_A_PACK, "o"),
                Entry.command("close", "", ModdedHelpMessages.COMMAND_CLOSE_CLOSE_THE_OPEN_STUDIO_DIMENSION_AND_DISCARD_ITS_WORLD, "x"),
                Entry.command("tpstudio", "", ModdedHelpMessages.COMMAND_TPSTUDIO_TELEPORT_INTO_THE_OPEN_STUDIO_DIMENSION, "stp"),
                Entry.command("status", "", ModdedHelpMessages.COMMAND_STATUS_SHOW_THE_OPEN_STUDIO_DIMENSION_AND_ITS_PACK),
                Entry.command("noise", "[generator] [seed]", ModdedHelpMessages.COMMAND_NOISE_OPEN_THE_NOISE_EXPLORER_GUI_ON_THE_SERVER_DISPLAY, "nmap"),
                Entry.command("map", "", ModdedHelpMessages.COMMAND_MAP_OPEN_THE_VISION_MAP_GUI_ON_THE_SERVER_DISPLAY, "render"),
                Entry.command("vscode", "[pack]", ModdedHelpMessages.COMMAND_VSCODE_REGENERATE_THE_CODE_WORKSPACE_FOR_A_PACK_AND_OPEN_IT_IN_YOUR, "vsc"),
                Entry.command("update", "[pack]", ModdedHelpMessages.COMMAND_UPDATE_REGENERATE_THE_CODE_WORKSPACE_FOR_A_PACK),
                Entry.command("importvanilla", "", ModdedHelpMessages.COMMAND_IMPORTVANILLA_EXPLAIN_VANILLA_IMPORT_WORKFLOW, "importv", "iv"),
                Entry.command("loot", "", ModdedHelpMessages.COMMAND_STUDIO_LOOT_BUKKIT_ONLY),
                Entry.command("profile", "", ModdedHelpMessages.COMMAND_STUDIO_PROFILE_BUKKIT_ONLY),
                Entry.command("spawn", "", ModdedHelpMessages.COMMAND_STUDIO_SPAWN_BUKKIT_ONLY, "summon"),
                Entry.command("objects", "", ModdedHelpMessages.COMMAND_STUDIO_OBJECTS_BUKKIT_ONLY, "find-objects")
        ));
        SECTIONS.put("std", SECTIONS.get("studio"));
        SECTIONS.put("s", SECTIONS.get("studio"));
        SECTIONS.put("pack", List.of(
                Entry.command("validate", "[pack]", ModdedHelpMessages.COMMAND_VALIDATE_VALIDATE_A_PACK_OR_EVERY_PACK, "v"),
                Entry.command("cleanup", "<pack> [apply]", ModdedHelpMessages.COMMAND_CLEANUP_PREVIEW_OR_QUARANTINE_UNUSED_RESOURCE_CANDIDATES, "c"),
                Entry.command("restore", "<pack> [apply]", ModdedHelpMessages.COMMAND_RESTORE_PREVIEW_OR_RESTORE_THE_LATEST_QUARANTINE, "r"),
                Entry.command("status", "[pack]", ModdedHelpMessages.COMMAND_STATUS_SHOW_CACHED_VALIDATION_STATUS, "s"),
                Entry.command("compat", "[pack]", ModdedHelpMessages.COMMAND_COMPAT_SHOW_CONTENT_THAT_CANNOT_GENERATE_ON_THIS_MINECRAFT_VERSION, "cp")
        ));
        SECTIONS.put("pk", SECTIONS.get("pack"));
        SECTIONS.put("world", List.of(
                Entry.command("enable", "<dimension> <pack|pack:dimensionKey> [seed|random]", ModdedHelpMessages.COMMAND_ENABLE_CREATE_AND_INJECT_A_PERSISTENT_IRIS_DIMENSION_AT_RUNTIME_DOWNLOADS_THE_PACK, "create"),
                Entry.command("replace-overworld", "<pack|pack:dimensionKey> [seed|random]", ModdedHelpMessages.COMMAND_REPLACE_OVERWORLD_INJECT_AN_IRIS_PRIMARY_WORLD_AND_ROUTE_PLAYERS_THERE_INSTEAD_OF_THE),
                Entry.command("mainworld", "<pack|pack:dimensionKey|off> [seed|random]", ModdedHelpMessages.COMMAND_MAINWORLD_CONFIGURE_PRIMARY_WORLD_PRESET),
                Entry.command("disable", "<dimension>", ModdedHelpMessages.COMMAND_DISABLE_EVACUATE_AND_UNLOAD_AN_IRIS_DIMENSION_WORLD_DATA_ON_DISK_IS_KEPT),
                Entry.command("delete", "<dimension>", ModdedHelpMessages.COMMAND_DELETE_DISABLE_AN_IRIS_DIMENSION_AND_WIPE_ITS_CHUNK_AND_MANTLE_DATA_FROM, "remove", "rm"),
                Entry.command("list", "", ModdedHelpMessages.COMMAND_LIST_LIST_LOADED_IRIS_DIMENSIONS, "ls"),
                Entry.command("status", "", ModdedHelpMessages.COMMAND_STATUS_SHOW_LOADED_IRIS_DIMENSIONS_AND_THE_CONFIGURED_PRIMARY_WORLD)
        ));
        SECTIONS.put("w", SECTIONS.get("world"));
        SECTIONS.put("datapack", List.of(
                Entry.command("status", "", ModdedHelpMessages.COMMAND_STATUS_CHECK_LOADED_IRIS_DIMENSION_TYPE_OVERRIDES),
                Entry.command("install", "", ModdedHelpMessages.COMMAND_INSTALL_INSTALL_DIMENSION_TYPE_OVERRIDES_FOR_LOADED_IRIS_DIMENSIONS),
                Entry.command("list", "", ModdedHelpMessages.COMMAND_LIST_LIST_CONFIGURED_AND_INSTALLED_DATAPACKS, "ls"),
                Entry.command("ingest", "", ModdedHelpMessages.COMMAND_INGEST_EXPLAIN_BUKKIT_DATAPACK_INGEST_WORKFLOW, "pull"),
                Entry.command("remove", "<id>", ModdedHelpMessages.COMMAND_REMOVE_EXPLAIN_DATAPACK_REMOVAL_WORKFLOW, "rm")
        ));
        SECTIONS.put("datapacks", SECTIONS.get("datapack"));
        SECTIONS.put("dp", SECTIONS.get("datapack"));
        SECTIONS.put("structure", List.of(
                Entry.command("list", "", ModdedHelpMessages.COMMAND_LIST_REGENERATE_STRUCTURE_INDEX_JSON, "ls"),
                Entry.command("info", "<key>", ModdedHelpMessages.COMMAND_INFO_RESOLVE_AN_IRIS_STRUCTURE_GRAPH_AND_REPORT_BOUNDS),
                Entry.command("place", "<key>", ModdedHelpMessages.COMMAND_PLACE_ASSEMBLE_AND_PLACE_AN_IRIS_STRUCTURE_AT_YOUR_LOCATION, "p"),
                Entry.command("import", "", ModdedHelpMessages.COMMAND_IMPORT_EXPLAIN_BUKKIT_STRUCTURE_IMPORT_WORKFLOW, "import-all", "reimport", "imp", "all"),
                Entry.command("capture", "", ModdedHelpMessages.COMMAND_CAPTURE_EXPLAIN_BUKKIT_STRUCTURE_CAPTURE_WORKFLOW, "cap"),
                Entry.command("verify", "[key]", ModdedHelpMessages.COMMAND_VERIFY_REPORT_NATIVE_AND_IRIS_STRUCTURE_REACHABILITY_IN_THE_CURRENT_DIMENSION, "locateall")
        ));
        SECTIONS.put("struct", SECTIONS.get("structure"));
        SECTIONS.put("str", SECTIONS.get("structure"));
        SECTIONS.put("developer", List.of(
                Entry.command("network", "", ModdedHelpMessages.COMMAND_NETWORK_LIST_NETWORK_INTERFACES_AND_THEIR_ADDRESSES, "ip")
        ));
        SECTIONS.put("dev", SECTIONS.get("developer"));
    }

    private ModdedCommandHelp() {
    }

    static boolean documents(String section, String command) {
        List<Entry> entries = SECTIONS.get(section);
        if (entries == null) {
            return false;
        }
        for (Entry entry : entries) {
            if (entry.name().equals(command)) {
                return true;
            }
            for (String alias : entry.aliases()) {
                if (alias.equals(command)) {
                    return true;
                }
            }
        }
        return false;
    }

    static int send(CommandSourceStack source, String path) {
        Request request = parse(path);
        List<Entry> entries = SECTIONS.get(request.section());
        if (entries == null) {
            ModdedCommandFeedback.fail(source, IrisLanguage.plain(
                    IrisMessages.MODDED_HELP_UNKNOWN_SECTION,
                    MessageArgument.untrusted("section", request.section())
            ));
            return 0;
        }

        ModdedCommandFeedback.clear(source);

        if (source.getPlayer() == null) {
            return sendConsole(source, request.section());
        }

        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) PAGE_SIZE));
        int page = Math.max(0, Math.min(request.page(), totalPages - 1));
        int from = page * PAGE_SIZE;
        int to = Math.min(entries.size(), from + PAGE_SIZE);

        sendHeader(source, request.section(), page, totalPages);
        if (!Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source)) {
            ModdedCommandFeedback.send(source, opNotice());
        }
        if (!request.section().isEmpty()) {
            ModdedCommandFeedback.send(source, backButton(request.section()));
        }
        for (Entry entry : entries.subList(from, to)) {
            ModdedCommandFeedback.send(source, line(request.section(), entry));
        }
        ModdedCommandFeedback.ok(source, footer(request.section(), page, totalPages));
        return 1;
    }

    private static int sendConsole(CommandSourceStack source, String section) {
        sendHeader(source, section, 0, 1);
        if (!Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source)) {
            ModdedCommandFeedback.send(source, opNotice());
        }
        for (String line : consoleLines(section)) {
            ModdedCommandFeedback.send(source, Component.literal(line));
        }
        return 1;
    }

    static List<String> consoleLines(String section) {
        List<Entry> entries = SECTIONS.get(section);
        if (entries == null) {
            return List.of();
        }

        List<String> lines = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            lines.add(consoleLine(section, entry));
        }
        return lines;
    }

    private static String consoleLine(String section, Entry entry) {
        StringBuilder line = new StringBuilder(section.isEmpty() ? "/iris " : "/iris " + section + " ");
        line.append(entry.name());
        if (!entry.usage().isBlank()) {
            line.append(' ').append(entry.usage());
        }
        if (entry.aliases().length > 0) {
            line.append(" (").append(String.join(", ", entry.aliases())).append(')');
        }
        if (entry.group()) {
            line.append(" - ").append(IrisLanguage.plain(DirectorHelpMessages.CATEGORY));
        }
        return line.append(" - ").append(IrisLanguage.plain(entry.description())).toString();
    }

    private static void sendHeader(CommandSourceStack source, String path, int page, int totalPages) {
        String title = path.isEmpty() ? "/iris" : "/iris " + path;
        if (totalPages > 1) {
            title += " {" + (page + 1) + "/" + totalPages + "}";
        }
        ModdedCommandFeedback.send(source, ModdedCommandFeedback.banner(title));
    }

    private static MutableComponent backButton(String path) {
        String parent = parentPath(path);
        String command = parent.isEmpty() ? "/iris" : "/iris help " + parent;
        MutableComponent hover = Component.empty()
                .append(text(IrisLanguage.plain(
                        IrisMessages.MODDED_HELP_BACK_HOVER,
                        MessageArgument.untrusted("parent", parent.isEmpty() ? "Iris" : parent)
                ), DARK_GREEN));
        return text("〈 " + IrisLanguage.plain(DirectorHelpMessages.BACK), BACK).withStyle((style) -> style
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(hover)));
    }

    private static MutableComponent line(String path, Entry entry) {
        MutableComponent row = Component.empty();
        row.append(clickableCommand(path, entry));
        row.append(nodes(entry));
        return row;
    }

    private static MutableComponent clickableCommand(String path, Entry entry) {
        String parent = path.isEmpty() ? "/iris" : "/iris " + path;
        String command = parent + " " + entry.name();
        String suggestion = entry.usage().isBlank() ? command : command + " " + entry.usage();
        ClickEvent clickEvent = entry.group() ? new ClickEvent.RunCommand(command) : new ClickEvent.SuggestCommand(suggestion);
        MutableComponent hover = entryHover(entry, suggestion);
        MutableComponent display = Component.empty();
        display.append(text("⇀", DARK_GREEN));
        display.append(Component.literal(" "));
        display.append(ModdedCommandFeedback.gradientText(entry.name(), PARAMETER, PARAMETER_ALT, false));
        return display.withStyle((style) -> style
                .withClickEvent(clickEvent)
                .withHoverEvent(new HoverEvent.ShowText(hover)));
    }

    private static MutableComponent nodes(Entry entry) {
        if (entry.group()) {
            return text(" - " + IrisLanguage.plain(DirectorHelpMessages.CATEGORY), CATEGORY);
        }

        List<String> tokens = usageTokens(entry.usage());
        if (tokens.isEmpty()) {
            return Component.empty();
        }

        MutableComponent nodes = Component.empty();
        for (String token : tokens) {
            nodes.append(Component.literal(" "));
            nodes.append(parameter(token));
        }
        return nodes;
    }

    private static MutableComponent parameter(String token) {
        String name = parameterName(token);
        boolean required = token.startsWith("<");
        MutableComponent title = Component.empty();
        if (required) {
            title.append(text("[", REQUIRED, true, false));
            title.append(text(name, PARAMETER, false, false));
            title.append(text("]", REQUIRED, true, false));
        } else {
            title.append(text("⊰", OPTIONAL));
            title.append(text(name, PARAMETER));
            title.append(text("⊱", OPTIONAL));
        }

        MutableComponent hover = Component.empty();
        hover.append(text(name, PARAMETER));
        hover.append(Component.literal("\n"));
        hover.append(text("✎ ", DESCRIPTION_ICON));
        hover.append(text(IrisLanguage.plain(IrisMessages.MODDED_HELP_COMMAND_PARAMETER), DESCRIPTION));
        hover.append(Component.literal("\n"));
        if (required) {
            hover.append(text("⚠ ", REQUIRED));
            hover.append(text(IrisLanguage.plain(DirectorHelpMessages.REQUIRED), REQUIRED_TEXT));
        } else {
            hover.append(text("✔ ", DESCRIPTION_ICON));
            hover.append(text(IrisLanguage.plain(DirectorHelpMessages.OPTIONAL), USAGE));
        }
        hover.append(Component.literal("\n"));
        hover.append(text("✢ ", DARK_GREEN));
        hover.append(text(IrisLanguage.plain(IrisMessages.MODDED_HELP_BRIGADIER_TEXT), HOVER_TYPE));

        return title.withStyle((style) -> style.withHoverEvent(new HoverEvent.ShowText(hover)));
    }

    private static MutableComponent entryHover(Entry entry, String suggestion) {
        MutableComponent hover = Component.empty();
        hover.append(text(names(entry), PARAMETER));
        hover.append(Component.literal("\n"));
        hover.append(text("✎ ", DESCRIPTION_ICON));
        hover.append(text(IrisLanguage.plain(entry.description()), DESCRIPTION));
        hover.append(Component.literal("\n"));
        hover.append(text("✒ ", USAGE_ICON));
        if (entry.group()) {
            hover.append(text(IrisLanguage.plain(DirectorHelpMessages.COMMAND_GROUP), USAGE));
        } else if (entry.usage().isBlank()) {
            hover.append(text(IrisLanguage.plain(DirectorHelpMessages.NO_PARAMETERS), USAGE));
        } else {
            hover.append(text(IrisLanguage.plain(DirectorHelpMessages.PARAMETERS_HOVER), USAGE));
            hover.append(Component.literal("\n"));
            hover.append(text("✦ ", EXAMPLE_ICON));
            hover.append(text(suggestion, PARAMETER));
        }
        return hover;
    }

    private static MutableComponent opNotice() {
        MutableComponent notice = Component.empty();
        notice.append(text("⚠ ", REQUIRED));
        notice.append(text(IrisLanguage.plain(IrisMessages.MODDED_HELP_OPERATOR_NOTICE) + " ", REQUIRED_TEXT));
        notice.append(text(IrisLanguage.plain(
                IrisMessages.MODDED_HELP_OPERATOR_INSTRUCTION,
                MessageArgument.trusted("command", "/op <you>")
        ), DESCRIPTION));
        return notice;
    }

    private static MutableComponent footer(String section, int page, int totalPages) {
        MutableComponent footer = Component.empty();
        int fill = ModdedCommandFeedback.PAGE_LINE_LENGTH;
        boolean hasPrevious = page > 0;
        boolean hasNext = page + 1 < totalPages;

        if (hasPrevious) {
            fill -= PAGE_BUTTON_WIDTH;
            footer.append(pageButton(section, page, false));
            footer.append(Component.literal(" "));
        }
        if (hasNext) {
            fill -= PAGE_BUTTON_WIDTH;
        }

        footer.append(ModdedCommandFeedback.gradientText(
                " ".repeat(fill),
                ModdedCommandFeedback.HEADER_B,
                ModdedCommandFeedback.HEADER_A,
                true
        ));

        if (hasNext) {
            footer.append(Component.literal(" "));
            footer.append(pageButton(section, page + 2, true));
        }
        return footer;
    }

    private static MutableComponent pageButton(String section, int target, boolean next) {
        String command = "/iris help " + (section.isEmpty() ? "" : section + " ") + target;
        String pageWord = IrisLanguage.plain(DirectorHelpMessages.PAGE);
        String label = next ? pageWord + " " + target + " ❭" : "〈 " + pageWord + " " + target;
        MutableComponent hover = text(IrisLanguage.plain(next
                ? DirectorHelpMessages.NEXT_PAGE
                : DirectorHelpMessages.PREVIOUS_PAGE), DESCRIPTION);
        return text(label, next ? PARAMETER_ALT : PARAMETER).withStyle((style) -> style
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(hover)));
    }

    private static MutableComponent text(String value, int color) {
        return ModdedCommandFeedback.text(value, color, false, false);
    }

    private static MutableComponent text(String value, int color, boolean bold, boolean strikethrough) {
        return ModdedCommandFeedback.text(value, color, bold, strikethrough);
    }

    private static List<String> usageTokens(String usage) {
        if (usage == null || usage.isBlank()) {
            return List.of();
        }
        return List.of(usage.trim().split("\\s+"));
    }

    private static String names(Entry entry) {
        if (entry.aliases().length == 0) {
            return entry.name();
        }

        List<String> names = new ArrayList<>(entry.aliases().length + 1);
        names.add(entry.name());
        for (String alias : entry.aliases()) {
            names.add(alias);
        }
        return String.join(", ", names);
    }

    private static String parameterName(String token) {
        String name = token;
        if ((name.startsWith("<") && name.endsWith(">")) || (name.startsWith("[") && name.endsWith("]"))) {
            name = name.substring(1, name.length() - 1);
        }
        return name;
    }

    private static String parentPath(String path) {
        int lastSpace = path.lastIndexOf(' ');
        if (lastSpace <= 0) {
            return "";
        }
        return path.substring(0, lastSpace);
    }

    private static Request parse(String path) {
        if (path == null) {
            return new Request("", 0);
        }

        String normalized = path.trim().toLowerCase();
        if (normalized.startsWith("/iris ")) {
            normalized = normalized.substring(6).trim();
        } else if (normalized.equals("/iris")) {
            normalized = "";
        }

        if (normalized.startsWith("help ")) {
            normalized = normalized.substring(5).trim();
        } else if (normalized.equals("help")) {
            normalized = "";
        }

        int page = 0;
        String[] tokens = normalized.isEmpty() ? new String[0] : normalized.split("\\s+");
        int end = tokens.length;
        if (end > 0 && tokens[end - 1].matches("\\d+")) {
            try {
                page = Math.max(0, Integer.parseInt(tokens[end - 1]) - 1);
            } catch (NumberFormatException ignored) {
                page = 0;
            }
            end--;
        }

        String section = end > 0 ? tokens[0] : "";
        return new Request(section, page);
    }

    private record Request(String section, int page) {
    }

    private record Entry(String name, String usage, TextKey description, boolean group, String... aliases) {
        static Entry command(String name, String usage, TextKey description, String... aliases) {
            return new Entry(name, usage, description, false, aliases);
        }

        static Entry group(String name, TextKey description, String... aliases) {
            return new Entry(name, "", description, true, aliases);
        }
    }
}

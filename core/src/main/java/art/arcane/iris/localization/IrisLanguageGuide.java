package art.arcane.iris.localization;

import art.arcane.volmlib.util.localization.LanguageFileEditor;
import art.arcane.volmlib.util.localization.LanguageFileHeader;
import art.arcane.volmlib.util.localization.TomlLanguageWriter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class IrisLanguageGuide {
    private static final String START = "# === Iris language guide ===";
    private static final String END = "# === End Iris language guide ===";
    private static final Pattern UNDESCRIBED_VARIABLES = Pattern.compile("#\\s+(?:\\{[A-Za-z][A-Za-z0-9_]*}\\s*)+");
    private static final Map<String, String> UNMARKED_GUIDE_FINGERPRINTS = Map.ofEntries(
            Map.entry("de_DE", "c32159c55e437c3e0e638a48618d2c4ea3bcefebacd3883e94095c986fb9a7e6"),
            Map.entry("en_US", "db2ebc5f392c7cd97ca0c3b58e7d9b75bd591b949f9adf9913ebd1053f5d8062"),
            Map.entry("es_ES", "2981096400cedaa8b2414c0e82f4476f6f4c45aa503005f852536342bd4a3f29"),
            Map.entry("fi_FI", "cfb3b61055061ce5a414b03a90a04c0b0e7cfdd10b5fea717a42a2d75da73824"),
            Map.entry("fr_FR", "caec157e7708cd30e0fc79a7fdb8b5468c2886e1e99a9d46a29b7dcfb0411b33"),
            Map.entry("he_IL", "5b21aa76fa8c5bf5c1c34e6855050c86321a886cf8d99383ae942b5198669f1b"),
            Map.entry("it_IT", "fbef45fa6c4f2edb6ce9c5dcd2c599c238ddec87ae69410a79a6d753cdebab35"),
            Map.entry("ja-JP", "59ab056560b61de78f4423153ae153d3ed7e8d68f4312c178617386f81164ae5"),
            Map.entry("ko_KR", "ab2cb82ad53b50c820b92c2fad4d4dc93e959c7a72429f9fa33c9768ab1965de"),
            Map.entry("lt_LT", "5493f682426006798f8e732157f3af750c092b181302e8879e438f75c0cb160a"),
            Map.entry("nl_NL", "5dbcd5250753367801615d8b5603f8027f51e5db14f27c7ed3198fae36177e05"),
            Map.entry("pl_PL", "ef896ad5ccc8a7c9507fd7012443c0ff3395d27ef179f8eaf1455e5339a0c6c6"),
            Map.entry("pt_PT", "98a1fda8484ccb2afc00225b65d5bdc1f9cc0db286709c19cd5aad5d457fa024"),
            Map.entry("ru_RU", "b6eb34594096aeab22dae072e74f186611a26b5da02d70896721f78157f30058"),
            Map.entry("tr_TR", "295294d1087ec5c1a2c7e1cabd7dc4495c323cdebee300d31d39103680fa48ae"),
            Map.entry("vi_VI", "fd2a3fb60cd84fba4d8da7269207f74db7b33cbf49e0d0d6f22fcb5c0a18f174"),
            Map.entry("zh_CN", "5e98574730c2c653f41431efcec5a4f8c6398e7d2c0fd10bfacd1f7f0f3a8948"),
            Map.entry("zh_TW", "c479ac67abb68400a3935e0fc745d510fd6bd1c257960ca0c370a58c5309b06d")
    );
    private static final Map<String, String> VARIABLE_DESCRIPTIONS = Map.ofEntries(
            Map.entry("MAXAUTOSELECTVOLUME", "Maximum number of blocks allowed in an automatically selected object."),
            Map.entry("MAXSAVEVOLUME", "Maximum number of blocks allowed in a saved object selection."),
            Map.entry("MAXSPAN", "Maximum allowed structure width, height or depth in blocks during capture."),
            Map.entry("NATIVESTRUCTURELOCATERADIUS", "Maximum native-structure search radius in chunks."),
            Map.entry("ROOTPERMISSION", "Root permission required to use the command."),
            Map.entry("WORKSPACENAME", "Name of the Studio workspace folder being searched."),
            Map.entry("WORLDPACKNAME", "Name of the generated world datapack."),
            Map.entry("active", "Number of workers currently running."),
            Map.entry("activeLocale", "Language that remains active when the requested language cannot be selected."),
            Map.entry("activeMax", "Upper boundary of the loaded dimension type's height range."),
            Map.entry("activeMin", "Lower boundary of the loaded dimension type's height range."),
            Map.entry("actualReverts", "Number of object pastes actually reverted."),
            Map.entry("afb", "Formatted part of an engine timing label after its bracketed portion."),
            Map.entry("after", "Value after the change, or remaining unreachable tree blocks after repair."),
            Map.entry("amount", "Quantity associated with the named entry in the report."),
            Map.entry("applied", "Number of chunks whose work has been applied."),
            Map.entry("argument", "Command argument that could not be accepted."),
            Map.entry("available", "Number of native structures eligible for generation."),
            Map.entry("bar", "Already formatted progress-bar text."),
            Map.entry("befb", "Formatted part of an engine timing label before its bracketed portion."),
            Map.entry("before", "Value before the change, or unreachable tree blocks before repair."),
            Map.entry("biome", "Name or identifier of the biome being shown."),
            Map.entry("block", "Block type or block description being inspected."),
            Map.entry("blockX", "X coordinate of the block or teleport destination."),
            Map.entry("blockY", "Y coordinate of the block or teleport destination."),
            Map.entry("blockZ", "Z coordinate of the block or teleport destination."),
            Map.entry("blocking", "Number of validation errors that prevent the pack from loading."),
            Map.entry("blocks", "Number of block variants in the imported group."),
            Map.entry("bpp", "World blocks represented by one pixel in Vision."),
            Map.entry("branches", "Number of branches added while repairing tree objects."),
            Map.entry("broken", "Number of packs that failed validation."),
            Map.entry("brokenTotal", "Total number of packs that failed validation."),
            Map.entry("cave", "Cave biome or cave description at the inspected position."),
            Map.entry("centerX", "X coordinate of the area's center; units follow the operation's message."),
            Map.entry("centerZ", "Z coordinate of the area's center; units follow the operation's message."),
            Map.entry("changed", "Number of processed objects that were changed."),
            Map.entry("chunk", "Coordinates or identifying label of the chunk being reported."),
            Map.entry("chunkX", "X coordinate measured in chunks, not blocks."),
            Map.entry("chunkZ", "Z coordinate measured in chunks, not blocks."),
            Map.entry("chunks", "Number of chunks included in the operation."),
            Map.entry("clampedY", "Teleport destination Y after adjustment to the allowed height range."),
            Map.entry("command", "Command name or complete command to run."),
            Map.entry("converted", "Number of leaf blocks converted into wood during tree repair."),
            Map.entry("count", "Number of entries, messages or players identified by the surrounding text."),
            Map.entry("current", "Current progress count, or number of chunks in the current comparison."),
            Map.entry("currentStage", "Name of the current Studio loading stage."),
            Map.entry("d", "Object depth in blocks."),
            Map.entry("depth", "Number of blocks below the terrain surface."),
            Map.entry("derivative", "Biome derivative or variant associated with the surface biome."),
            Map.entry("detail", "Additional description of the inspected object or current progress stage."),
            Map.entry("diffs", "Number of differences detected between generated results."),
            Map.entry("dimKey", "Identifier of the dimension being packaged."),
            Map.entry("dimension", "Name or identifier of the dimension involved in the message."),
            Map.entry("dimensionId", "Registered identifier of the loaded or requested dimension."),
            Map.entry("dimensionId2", "Dimension identifier repeated in the suggested delete command."),
            Map.entry("dimm", "Studio dimension identifier, or its pack name in the validation command."),
            Map.entry("disabled", "Number of native structures whose generation is disabled."),
            Map.entry("distances", "Number of leaf-distance values changed during tree repair."),
            Map.entry("done", "Number of work items completed so far."),
            Map.entry("downloadSource", "Source or selected reference used to download the pack."),
            Map.entry("downloadable", "Pack or downloadable project being imported."),
            Map.entry("dumpPath", "Location of the saved cleanup or restore data."),
            Map.entry("duration", "Formatted duration of the operation."),
            Map.entry("e", "Exception text from a failed structure-template scan."),
            Map.entry("elapsed", "Formatted time elapsed since the operation started."),
            Map.entry("engines", "Number of active Iris generation engines or dimensions."),
            Map.entry("entity", "Entity type selected by the inspected spawner."),
            Map.entry("error", "Details of the error that occurred."),
            Map.entry("errorMessage", "Optional error-detail suffix appended to the main failure message."),
            Map.entry("eta", "Formatted estimated time remaining; some messages supply it with its label."),
            Map.entry("failed", "Number of failed items, or a prepared failure suffix in progress titles."),
            Map.entry("failure", "Reason the search or operation failed."),
            Map.entry("failures", "Failed-work count or an optional formatted failure summary."),
            Map.entry("file", "Name or path of the file involved in the operation."),
            Map.entry("filter", "Text used to filter the list of Iris dimensions."),
            Map.entry("fps", "Current preview rendering rate in frames per second."),
            Map.entry("g", "Object files written since the previous packaging progress report."),
            Map.entry("generated", "Number of chunks generated so far."),
            Map.entry("generator", "Name of the noise generator shown in the explorer."),
            Map.entry("generatorKey", "Requested noise-generator identifier."),
            Map.entry("get", "Number of schematics successfully converted."),
            Map.entry("golden", "Number of chunks in the saved reference comparison."),
            Map.entry("goldenDimension", "Dimension identifier stored in the reference capture."),
            Map.entry("goldenSeed", "World seed stored in the reference capture."),
            Map.entry("goldenVersion", "Minecraft version used to create the reference capture."),
            Map.entry("group", "Message group in the language editor, or an imported object's category."),
            Map.entry("guiNote", "Optional note about the pregeneration progress window."),
            Map.entry("h", "Object height in blocks."),
            Map.entry("hash", "Combined checksum of the generated chunks being compared."),
            Map.entry("health", "Current health of the inspected entity."),
            Map.entry("height", "Terrain height, total height or displayed area's height, as named in the message."),
            Map.entry("hours", "Hours component of the formatted duration."),
            Map.entry("i", "Engine display name, or a listed entry in the command-list template."),
            Map.entry("id", "Registry ID of the biome being inspected."),
            Map.entry("imported", "Number of structures or objects successfully imported."),
            Map.entry("includeNonJigsaw", "Whether the import also includes structures that do not use jigsaw pieces."),
            Map.entry("iris", "Number of loaded dimensions using Iris generation."),
            Map.entry("irisLevels", "Number of loaded Iris dimensions checked against pack heights."),
            Map.entry("irisPlaced", "Number of structures placed by Iris instead of native generation."),
            Map.entry("job", "Name of the completed background operation."),
            Map.entry("k", "Region load key in the region-distribution report."),
            Map.entry("key", "Parameter, message or resource identifier named by the surrounding text."),
            Map.entry("key2", "Structure identifier repeated in the suggested locate command."),
            Map.entry("keyString", "Structure or resource identifier being imported or reported as invalid."),
            Map.entry("label", "Display name of the located target."),
            Map.entry("line", "Line number in the language editor."),
            Map.entry("locale", "Language identifier, such as en_US or de_DE."),
            Map.entry("logicalWorldName", "Logical name of the world targeted by the command."),
            Map.entry("loot", "Identifier of the inspected loot table."),
            Map.entry("m", "Extra evacuation message, or ignored-parameter text in that command template."),
            Map.entry("mantleStatus", "Result of resetting generation data before a repeat-generation comparison."),
            Map.entry("marker", "Name of the marker being searched for."),
            Map.entry("material", "Minecraft material of the inspected block."),
            Map.entry("maxScale", "Largest allowed object scale after clamping the requested scale."),
            Map.entry("maxX", "Upper X boundary of the displayed chunk range."),
            Map.entry("maxY", "Upper boundary of the world's height range."),
            Map.entry("maxZ", "Upper Z boundary of the displayed chunk range."),
            Map.entry("maximum", "Maximum permitted input length or maximum entity health, as named in the message."),
            Map.entry("mcmeta", "Path of the generated datapack's pack.mcmeta file."),
            Map.entry("message", "Detailed status or error text supplied by the operation."),
            Map.entry("method", "Selected pregeneration method."),
            Map.entry("minX", "Lower X boundary of the displayed chunk range."),
            Map.entry("minY", "Lower boundary of the world's height range."),
            Map.entry("minZ", "Lower Z boundary of the displayed chunk range."),
            Map.entry("minutes", "Minutes component of the formatted duration."),
            Map.entry("mismatches", "Number of chunks that differ from the saved reference."),
            Map.entry("mode", "Selected import, generation or preview mode."),
            Map.entry("modeNote", "Optional explanation of the selected pregeneration mode."),
            Map.entry("name", "Name of the world, object, project or entry identified by the message."),
            Map.entry("nameRaw", "Name exactly as entered, before normalization or validation."),
            Map.entry("normalized", "Normalized help-section name that could not be found."),
            Map.entry("num", "Formatted bracketed portion of an engine timing label; not necessarily a number."),
            Map.entry("object", "Name or identifier of the Iris object being edited or inspected."),
            Map.entry("objects", "Object list or count for the inspected chunk."),
            Map.entry("offset", "Vertical distance from the terrain surface in blocks."),
            Map.entry("output", "Path of the output file that could not be written."),
            Map.entry("overall", "Average pregeneration chunks per second over the whole operation."),
            Map.entry("ownerName", "Name of the player who owns the Studio session."),
            Map.entry("pack", "Name or identifier of the Iris pack involved in the operation."),
            Map.entry("pack2", "Pack identifier repeated in a suggested command or download source."),
            Map.entry("packDimension", "Dimension definition selected from the pack."),
            Map.entry("packLogical", "Logical height requested by the pack's dimension definition."),
            Map.entry("packMax", "Upper height boundary requested by the pack."),
            Map.entry("packMin", "Lower height boundary requested by the pack."),
            Map.entry("packName", "Display name of the pack being validated."),
            Map.entry("parameter", "Command parameter that is missing or cannot be parsed."),
            Map.entry("parent", "Display name of the parent menu or command route."),
            Map.entry("path", "Path of the file being saved, opened or reported."),
            Map.entry("percent", "Progress percentage; keep the message's percent sign outside the variable."),
            Map.entry("permission", "Permission node required for the requested action."),
            Map.entry("personal", "Player's saved personal language identifier."),
            Map.entry("phase", "Name of the current loading or download phase."),
            Map.entry("pinned", "Number of leaves pinned while repairing a tree object."),
            Map.entry("player", "Name of the player being followed or targeted."),
            Map.entry("plugin", "Plugin display name, Iris in Iris's shared language and debug messages."),
            Map.entry("position", "Selection corner number being set."),
            Map.entry("preset", "Registered world-generation preset used for the main world."),
            Map.entry("primary", "Identifier of the configured primary world."),
            Map.entry("processed", "Number of items processed, including successful, skipped and failed items."),
            Map.entry("properties", "Formatted list of the inspected block's properties."),
            Map.entry("quarantinePath", "Folder containing quarantined cleanup files."),
            Map.entry("queued", "Number of workers or work items waiting to run."),
            Map.entry("radius", "Pregeneration radius repeated in the suggested start command."),
            Map.entry("rarity", "Configured rarity value of the named region or placement."),
            Map.entry("rate", "Formatted transfer or pregeneration rate; the surrounding message adds the per-second suffix."),
            Map.entry("reach", "Configured branch-search reach used when repairing tree objects."),
            Map.entry("ready", "Number of exact Atlas pages currently available."),
            Map.entry("reason", "Explanation of why the requested action could not finish."),
            Map.entry("region", "Name or identifier of the region being inspected."),
            Map.entry("remaining", "Number of additional block states omitted from the displayed list."),
            Map.entry("repository", "Repository or downloaded archive being unpacked."),
            Map.entry("reverted", "Number of object pastes reverted."),
            Map.entry("rotation", "Rotation applied to the placed object."),
            Map.entry("rx", "Chunk X coordinate being hashed."),
            Map.entry("rz", "Chunk Z coordinate being hashed."),
            Map.entry("s", "Name of the new Studio project."),
            Map.entry("safeName", "Sanitized filename used for the imported object."),
            Map.entry("scale", "World blocks represented by one preview pixel."),
            Map.entry("scope", "Object or group of objects opened in Object Studio."),
            Map.entry("searchRadius", "Structure-verification search radius in chunks."),
            Map.entry("seconds", "Seconds component of a duration or time since the last update."),
            Map.entry("section", "Help-section name requested by the user."),
            Map.entry("seed", "Seed used for the world, Studio session, generator or reference comparison."),
            Map.entry("seedRaw", "Seed exactly as entered before validation."),
            Map.entry("sixtySecond", "Pregeneration chunks per second over the 60-second window."),
            Map.entry("size", "Number of files, warnings, entries or items named in the message."),
            Map.entry("skipped", "Number of items skipped by the operation."),
            Map.entry("slope", "Terrain slope at the inspected position."),
            Map.entry("solidBlocks", "Number of solid blocks counted by generation hashing."),
            Map.entry("source", "Pack, download source or input associated with the progress display."),
            Map.entry("speed", "Formatted pregeneration or processing rate per second."),
            Map.entry("stage", "Name of the current progress stage."),
            Map.entry("state", "Current state or property value named by the message."),
            Map.entry("status", "Current preview status text."),
            Map.entry("structure", "Name or identifier of the structure being located."),
            Map.entry("structureKey", "Requested structure's registry or pack identifier."),
            Map.entry("summary", "Formatted results of the completed operation."),
            Map.entry("suppressed", "Number of native structures replaced by Iris placements."),
            Map.entry("surface", "Surface Y coordinate used in the suggested teleport command."),
            Map.entry("surfaceY", "Y coordinate of the terrain surface at the inspected position."),
            Map.entry("tag", "Formatted validation status label placed before the pack name."),
            Map.entry("target", "Language recipient or object-selection target named in the message."),
            Map.entry("targetX", "X coordinate of the located or requested destination."),
            Map.entry("targetZ", "Z coordinate of the located or requested destination."),
            Map.entry("template", "Template selected when creating a project."),
            Map.entry("tenSecond", "Pregeneration chunks per second over the 10-second window."),
            Map.entry("thirtySecond", "Pregeneration chunks per second over the 30-second window."),
            Map.entry("threads", "Number of worker threads used for generation hashing."),
            Map.entry("tileNote", "Optional note about saved or placed block-entity data."),
            Map.entry("title", "Title of the operation whose result is being displayed."),
            Map.entry("to", "New debug setting: true for enabled, false for disabled."),
            Map.entry("token", "Reach argument that could not be parsed."),
            Map.entry("total", "Total number of work items, chunks, dimensions or entries named in the message."),
            Map.entry("totalObjects", "Number of objects opened in Object Studio."),
            Map.entry("transferred", "Formatted amount of data transferred so far."),
            Map.entry("type", "Expected parameter type, resource type or object category named in the message."),
            Map.entry("typeKey", "Registered Minecraft dimension-type identifier, or inline."),
            Map.entry("unreachableBiomes", "Number of native structures excluded by the pack's biome choices."),
            Map.entry("unsupported", "Number of native structures reported as unsupported by this platform."),
            Map.entry("updates", "Block updates per second in the developer display."),
            Map.entry("url", "Web address to open or copy."),
            Map.entry("usage", "Command syntax, or memory-usage text in the memory display."),
            Map.entry("used", "Formatted used-memory amount in the memory display."),
            Map.entry("value", "Message-specific value: for example a default setting, name, coordinate, count or error. Read the surrounding text."),
            Map.entry("value2", "Second message-specific value: for example total chunks, target Y, a pack name or an error. Read the surrounding text."),
            Map.entry("value3", "Third message-specific value: for example a percentage, target Z, Minecraft version or elapsed time."),
            Map.entry("value4", "Fourth message-specific value: for example block writes, a selection's Z coordinate or an optional status suffix."),
            Map.entry("value5", "Fifth message-specific value: non-air block writes in object placement, or chunk Z in the position template."),
            Map.entry("variables", "List of placeholders available for the message being edited."),
            Map.entry("velocity", "Current movement velocity of the inspected entity."),
            Map.entry("version", "Plugin or Minecraft version identified by the surrounding message."),
            Map.entry("volume", "Number of blocks contained in the object selection."),
            Map.entry("w", "Object width in blocks."),
            Map.entry("wantVariants", "Requested variants per imported vanilla tree or object feature."),
            Map.entry("warnings", "Number of non-blocking validation warnings."),
            Map.entry("width", "Width of the displayed world area in blocks."),
            Map.entry("wood", "Number of wood blocks added while repairing a tree object."),
            Map.entry("world", "Name of the world targeted by the command or progress display."),
            Map.entry("written", "Number of imported variants or object files successfully written."),
            Map.entry("x", "X coordinate in the block, chunk, region or preview context named by the message."),
            Map.entry("y", "Vertical world or block coordinate."),
            Map.entry("z", "Z coordinate in the block, chunk, region or preview context named by the message."),
            Map.entry("zoom", "Current zoom level of the preview.")
    );
    private static final List<String> PREFIX_HELP = List.of(
            "Iris has no language-wide prefix setting. Prefix text in one message changes only that message.",
            "Plugin log prefixes and menu themes are supplied separately.",
            "Example under [iris.bukkit.commandiris]:",
            "set_debug = \"&6[Worlds]&r &aSet debug to: {to}\"",
            "/iris debug then shows the Worlds prefix; {to} is true when enabled and false when disabled.",
            "Use only the variables already present in each message. {PC}, {SC} and {TC} are not Iris theme variables.",
            "For value, value2 and other numbered variables, the surrounding message determines their meaning."
    );
    private static final List<String> FORMATTING_HELP = List.of(
            "Classic colors: &0 black, &1 dark blue, &2 dark green, &3 dark aqua.",
            "&4 dark red, &5 dark purple, &6 gold, &7 gray.",
            "&8 dark gray, &9 blue, &a green, &b aqua.",
            "&c red, &d light purple, &e yellow, &f white.",
            "Styles: &k obfuscated, &l bold, &m strikethrough, &n underline, &o italic, &r reset.",
            "Example: &6[Worlds]&r &aReady uses a gold prefix, resets its style, then uses green text.",
            "The language parser accepts &#RRGGBB, &xRRGGBB, &x&R&R&G&G&B&B and [RRGGBB].",
            "Bukkit themes and downstream formatting can alter colors, including RGB colors.",
            "Modded commands, help and client screens use plain translated text with their own styles.",
            "In double-quoted TOML strings: \\n adds a newline, \\\" inserts a quote, \\\\ inserts a backslash.",
            "A backslash before & or [ only escapes the language parser; later display formatting may interpret it again."
    );

    private IrisLanguageGuide() {
    }

    static Map<String, String> variableDescriptions() {
        return VARIABLE_DESCRIPTIONS;
    }

    static String header(String locale) throws IOException {
        try (InputStream input = IrisLanguageGuide.class.getResourceAsStream("/iris-language-guides/" + locale + ".txt")) {
            if (input != null) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8).stripTrailing() + "\n";
            }
        }
        return englishHeader(locale);
    }

    static String englishHeader(String locale) {
        List<String> lines = new ArrayList<>();
        lines.add("=== Iris language guide ===");
        for (String line : LanguageFileHeader.render(new LanguageFileHeader.Options(
                "Iris", locale, PREFIX_HELP, FORMATTING_HELP, VARIABLE_DESCRIPTIONS))) {
            lines.add(line.equals("plugins/Iris/languages/" + locale + ".toml")
                    ? "languages/" + locale + ".toml" : line);
        }
        lines.add("=== End Iris language guide ===");
        return TomlLanguageWriter.render(Map.of(), lines);
    }

    static void refresh(File file, String locale) throws IOException {
        if (!Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)
                || Files.size(file.toPath()) > 2L * 1024L * 1024L) {
            return;
        }
        String original = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        String updated = replaceHeader(original, header(locale), locale);
        if (original.equals(updated)) {
            return;
        }
        LanguageFileEditor.update(file.toPath(), current -> {
            if (!current.equals(original)) {
                throw new IOException("Language file changed while refreshing its guide: " + file);
            }
            return new LanguageFileEditor.Prepared<>(updated, null);
        });
    }

    static String replaceHeader(String content, String header, String locale) {
        int offset = 0;
        int start = -1;
        int end = -1;
        boolean marked = false;
        while (offset < content.length()) {
            int newline = content.indexOf('\n', offset);
            int next = newline < 0 ? content.length() : newline + 1;
            String line = content.substring(offset, newline < 0 ? content.length() : newline).strip();
            if (!line.isEmpty() && !line.startsWith("#")) {
                break;
            }
            if (line.equals(START)) {
                start = offset;
                marked = true;
            } else if (marked && line.equals(END)) {
                return content.substring(0, start) + header + content.substring(next);
            } else if (!marked && (line.equals("# Iris - " + locale) || line.equals("# Iris — " + locale))) {
                start = offset;
            } else if (!marked && start >= 0 && UNDESCRIBED_VARIABLES.matcher(line).matches()) {
                end = next;
            }
            offset = next;
        }
        if (!marked && start >= 0 && end > start
                && matchesUnmarkedGuide(content.substring(start, end), locale)) {
            return content.substring(0, start) + header + content.substring(end);
        }
        return content;
    }

    private static boolean matchesUnmarkedGuide(String content, String locale) {
        String expected = UNMARKED_GUIDE_FINGERPRINTS.get(locale);
        if (expected == null) {
            return false;
        }
        StringBuilder prose = new StringBuilder();
        for (String line : content.lines().toList()) {
            String normalized = line.stripTrailing();
            if (normalized.isBlank() || normalized.equals("#")
                    || UNDESCRIBED_VARIABLES.matcher(normalized).matches()) {
                continue;
            }
            prose.append(normalized).append('\n');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(prose.toString().getBytes(StandardCharsets.UTF_8));
            return expected.equals(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }
}

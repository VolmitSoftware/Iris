package art.arcane.iris.modded.localization;

import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;

public final class ModdedHelpMessages {
    public static final TextKey COMMAND_VERSION_PRINT_VERSION_INFORMATION = TextKey.of(
            "iris.modded.help.entry.command.version",
            "Print version information"
    );
    public static final TextKey COMMAND_INFO_LIST_LOADED_IRIS_DIMENSIONS_AND_PACK_DETAILS = TextKey.of(
            "iris.modded.help.entry.command.info",
            "List loaded Iris dimensions and pack details"
    );
    public static final TextKey COMMAND_WHAT_INSPECT_THE_IRIS_BIOME_REGION_CAVE_BIOME_SURFACE_AND_CHUNK_AT_YOUR = TextKey.of(
            "iris.modded.help.entry.command.what",
            "Inspect the Iris biome, region, cave biome, surface and chunk at your position, the block you look at, your held item, or nearby markers"
    );
    public static final TextKey GROUP_FIND_FIND_AND_TELEPORT_TO_IRIS_BIOMES_REGIONS_OBJECTS_IRIS_STRUCTURES_NATIVE_STRUCTURES = TextKey.of(
            "iris.modded.help.entry.group.find",
            "Find and teleport to Iris biomes, regions, objects, Iris structures, native structures and points of interest"
    );
    public static final TextKey COMMAND_TP_TELEPORT_YOURSELF_OR_A_NAMED_PLAYER_INTO_A_LOADED_IRIS_DIMENSION = TextKey.of(
            "iris.modded.help.entry.command.tp",
            "Teleport yourself or a named player into a loaded Iris dimension"
    );
    public static final TextKey COMMAND_EVACUATE_TELEPORT_EVERY_PLAYER_OUT_OF_AN_IRIS_DIMENSION_TO_THE_PRIMARY_WORLD = TextKey.of(
            "iris.modded.help.entry.command.evacuate",
            "Teleport every player out of an Iris dimension to the primary world spawn"
    );
    public static final TextKey COMMAND_SEED_PRINT_WORLD_AND_ENGINE_SEED_INFORMATION = TextKey.of(
            "iris.modded.help.entry.command.seed",
            "Print world and engine seed information"
    );
    public static final TextKey COMMAND_DEBUG_TOGGLE_IRIS_DEBUG_LOGGING_AND_SAVE_SETTINGS_JSON = TextKey.of(
            "iris.modded.help.entry.command.debug",
            "Toggle Iris debug logging and save iris.json"
    );
    public static final TextKey COMMAND_RELOAD_RELOAD_SETTINGS_JSON_ALSO_HOTLOADED_AUTOMATICALLY_EVERY_3S = TextKey.of(
            "iris.modded.help.entry.command.reload",
            "Reload iris.json (also hotloaded automatically every 3s)"
    );
    public static final TextKey COMMAND_DOWNLOAD_DOWNLOAD_A_PACK_PROJECT = TextKey.of(
            "iris.modded.help.entry.command.download",
            "Download a pack project"
    );
    public static final TextKey COMMAND_METRICS_PRINT_GENERATION_METRICS_FOR_YOUR_CURRENT_IRIS_DIMENSION = TextKey.of(
            "iris.modded.help.entry.command.metrics",
            "Print generation metrics for your current Iris dimension"
    );
    public static final TextKey COMMAND_REGEN_DELETE_AND_REGENERATE_NEARBY_CHUNKS_IN_PLACE = TextKey.of(
            "iris.modded.help.entry.command.regen",
            "Delete and regenerate nearby chunks in place"
    );
    public static final TextKey GROUP_PREGEN_PREGENERATE_AN_IRIS_DIMENSION = TextKey.of(
            "iris.modded.help.entry.group.pregen",
            "Pregenerate an Iris dimension"
    );
    public static final TextKey COMMAND_WAND_GET_AN_IRIS_OBJECT_WAND = TextKey.of(
            "iris.modded.help.entry.command.wand",
            "Get an Iris object wand"
    );
    public static final TextKey GROUP_OBJECT_OBJECT_WAND_SAVE_PASTE_ANALYZE_AND_UNDO_TOOLS = TextKey.of(
            "iris.modded.help.entry.group.object",
            "Object wand, save, paste, analyze and undo tools"
    );
    public static final TextKey GROUP_EDIT_OPEN_PACK_BIOME_REGION_AND_DIMENSION_JSON_FILES_IN_YOUR_DESKTOP_EDITOR = TextKey.of(
            "iris.modded.help.entry.group.edit",
            "Open pack biome, region and dimension json files in your desktop editor"
    );
    public static final TextKey COMMAND_CREATE_CREATE_AND_INJECT_A_PERSISTENT_IRIS_DIMENSION_QUOTE_PACK_DIMENSIONKEY_TO_PICK = TextKey.of(
            "iris.modded.help.entry.command.create",
            "Create and inject a persistent Iris dimension; quote pack:dimensionKey to pick a specific pack dimension"
    );
    public static final TextKey GROUP_STUDIO_PACK_PROJECT_CREATION_PACKAGING_AND_REPORTS = TextKey.of(
            "iris.modded.help.entry.group.studio",
            "Pack project creation, packaging and reports"
    );
    public static final TextKey GROUP_PACK_PACK_VALIDATION_AND_MAINTENANCE = TextKey.of(
            "iris.modded.help.entry.group.pack",
            "Pack validation and maintenance"
    );
    public static final TextKey GROUP_WORLD_RUNTIME_IRIS_DIMENSION_CREATION_REMOVAL_AND_STATUS = TextKey.of(
            "iris.modded.help.entry.group.world",
            "Runtime Iris dimension creation, removal and status"
    );
    public static final TextKey GROUP_DATAPACK_WORLD_DATAPACK_INSTALL_AND_STATUS_HELPERS = TextKey.of(
            "iris.modded.help.entry.group.datapack",
            "World datapack install and status helpers"
    );
    public static final TextKey GROUP_STRUCTURE_IRIS_STRUCTURE_INDEX_INFO_AND_PLACEMENT_TOOLS = TextKey.of(
            "iris.modded.help.entry.group.structure",
            "Iris structure index, info and placement tools"
    );
    public static final TextKey COMMAND_GOLDENHASH_GENERATE_DETERMINISTIC_BLOCK_HASHES_FOR_PARITY_TESTING = TextKey.of(
            "iris.modded.help.entry.command.goldenhash",
            "Generate deterministic block hashes for parity testing"
    );
    public static final TextKey GROUP_DEVELOPER_DEVELOPER_DIAGNOSTICS_NETWORK_INTERFACES_REGION_FILE_SCAN = TextKey.of(
            "iris.modded.help.entry.group.developer",
            "Developer diagnostics: network interfaces and region-file scan"
    );
    public static final TextKey COMMAND_BIOME_FIND_AN_IRIS_BIOME = TextKey.of(
            "iris.modded.help.entry.command.biome",
            "Find an Iris biome"
    );
    public static final TextKey COMMAND_REGION_FIND_AN_IRIS_REGION = TextKey.of(
            "iris.modded.help.entry.command.region",
            "Find an Iris region"
    );
    public static final TextKey COMMAND_OBJECT_FIND_AN_OBJECT_PLACEMENT = TextKey.of(
            "iris.modded.help.entry.command.object",
            "Find an object placement"
    );
    public static final TextKey COMMAND_STRUCTURE_FIND_AN_IRIS_PLACED_OR_NATIVE_DATAPACK_STRUCTURE = TextKey.of(
            "iris.modded.help.entry.command.structure",
            "Find an Iris-placed or native/datapack structure"
    );
    public static final TextKey COMMAND_POI_FIND_A_SUPPORTED_POINT_OF_INTEREST = TextKey.of(
            "iris.modded.help.entry.command.poi",
            "Find a supported point of interest"
    );
    public static final TextKey COMMAND_BIOME_OPEN_A_BIOME_JSON_IN_YOUR_DESKTOP_EDITOR_NO_KEY_OPENS_THE = TextKey.of(
            "iris.modded.help.entry.command.biome_2",
            "Open a biome json in your desktop editor; no key opens the biome at your position"
    );
    public static final TextKey COMMAND_REGION_OPEN_A_REGION_JSON_IN_YOUR_DESKTOP_EDITOR_NO_KEY_OPENS_THE = TextKey.of(
            "iris.modded.help.entry.command.region_2",
            "Open a region json in your desktop editor; no key opens the region at your position"
    );
    public static final TextKey COMMAND_DIMENSION_OPEN_THE_CURRENT_PACK_S_DIMENSION_JSON_IN_YOUR_DESKTOP_EDITOR = TextKey.of(
            "iris.modded.help.entry.command.dimension",
            "Open the current pack's dimension json in your desktop editor"
    );
    public static final TextKey COMMAND_START_START_PREGENERATION_RADIUS_IN_BLOCKS_RESUMABLE_CHECKPOINT_CACHE_ON_BY_DEFAULT_CENTER = TextKey.of(
            "iris.modded.help.entry.command.start",
            "Start pregeneration; radius in blocks, resumable checkpoint cache on by default, center via 'at <x> <z>', flags compose in any order"
    );
    public static final TextKey COMMAND_STOP_STOP_THE_ACTIVE_PREGENERATION_TASK = TextKey.of(
            "iris.modded.help.entry.command.stop",
            "Stop the active pregeneration task"
    );
    public static final TextKey COMMAND_PAUSE_PAUSE_OR_RESUME_PREGENERATION = TextKey.of(
            "iris.modded.help.entry.command.pause",
            "Pause or resume pregeneration"
    );
    public static final TextKey COMMAND_STATUS_SHOW_PREGENERATION_STATUS = TextKey.of(
            "iris.modded.help.entry.command.status",
            "Show pregeneration status"
    );
    public static final TextKey COMMAND_WAND_GET_AN_IRIS_OBJECT_WAND_2 = TextKey.of(
            "iris.modded.help.entry.command.wand_3",
            "Get an Iris object wand"
    );
    public static final TextKey COMMAND_DUST_GET_DUST_THAT_REVEALS_OBJECT_PLACEMENTS = TextKey.of(
            "iris.modded.help.entry.command.dust",
            "Get dust that reveals object placements"
    );
    public static final TextKey COMMAND_SAVE_SAVE_THE_SELECTED_WAND_VOLUME_AS_AN_OBJECT = TextKey.of(
            "iris.modded.help.entry.command.save",
            "Save the selected wand volume as an object"
    );
    public static final TextKey COMMAND_PASTE_PASTE_AN_OBJECT_AT_YOUR_POSITION_OR_A_GIVEN_POSITION_OPTIONALLY_ROTATED = TextKey.of(
            "iris.modded.help.entry.command.paste",
            "Paste an object at your position or a given position, optionally rotated"
    );
    public static final TextKey COMMAND_EXPAND_EXPAND_THE_WAND_SELECTION_IN_YOUR_LOOKING_DIRECTION = TextKey.of(
            "iris.modded.help.entry.command.expand",
            "Expand the wand selection in your looking direction"
    );
    public static final TextKey COMMAND_CONTRACT_CONTRACT_THE_WAND_SELECTION_IN_YOUR_LOOKING_DIRECTION = TextKey.of(
            "iris.modded.help.entry.command.contract",
            "Contract the wand selection in your looking direction"
    );
    public static final TextKey COMMAND_SHIFT_SHIFT_THE_WAND_SELECTION_IN_YOUR_LOOKING_DIRECTION = TextKey.of(
            "iris.modded.help.entry.command.shift",
            "Shift the wand selection in your looking direction"
    );
    public static final TextKey COMMAND_POSITION1_SET_SELECTION_POINT_1 = TextKey.of(
            "iris.modded.help.entry.command.position1",
            "Set selection point 1"
    );
    public static final TextKey COMMAND_POSITION2_SET_SELECTION_POINT_2 = TextKey.of(
            "iris.modded.help.entry.command.position2",
            "Set selection point 2"
    );
    public static final TextKey COMMAND_X_Y_AUTOSELECT_UP_AND_OUT = TextKey.of(
            "iris.modded.help.entry.command.x_y",
            "Autoselect up and out"
    );
    public static final TextKey COMMAND_X_Y_AUTOSELECT_UP_DOWN_AND_OUT = TextKey.of(
            "iris.modded.help.entry.command.x_y_2",
            "Autoselect up, down and out"
    );
    public static final TextKey COMMAND_ANALYZE_SHOW_OBJECT_COMPOSITION = TextKey.of(
            "iris.modded.help.entry.command.analyze",
            "Show object composition"
    );
    public static final TextKey COMMAND_SHRINK_SHRINK_AN_OBJECT_TO_ITS_MINIMUM_SIZE = TextKey.of(
            "iris.modded.help.entry.command.shrink",
            "Shrink an object to its minimum size"
    );
    public static final TextKey COMMAND_PLAUSIBILIZE_GROW_BRANCHES_SO_TREE_LEAVES_SURVIVE_VANILLA_DECAY = TextKey.of(
            "iris.modded.help.entry.command.plausibilize",
            "Grow branches so tree leaves survive vanilla decay"
    );
    public static final TextKey COMMAND_UNDO_UNDO_PASTED_OBJECTS = TextKey.of(
            "iris.modded.help.entry.command.undo",
            "Undo pasted objects"
    );
    public static final TextKey COMMAND_CREATE_CREATE_A_NEW_PACK_PROJECT = TextKey.of(
            "iris.modded.help.entry.command.create_2",
            "Create a new pack project"
    );
    public static final TextKey COMMAND_PACKAGE_PACKAGE_A_DIMENSION_INTO_A_COMPRESSED_FORMAT = TextKey.of(
            "iris.modded.help.entry.command.package",
            "Package a dimension into a compressed format"
    );
    public static final TextKey COMMAND_VERSION_PRINT_A_PACK_VERSION = TextKey.of(
            "iris.modded.help.entry.command.version_2",
            "Print a pack version"
    );
    public static final TextKey COMMAND_REGIONS_CALCULATE_NEARBY_REGION_DISTRIBUTION = TextKey.of(
            "iris.modded.help.entry.command.regions",
            "Calculate nearby region distribution"
    );
    public static final TextKey COMMAND_OPEN_OPEN_A_TEMPORARY_STUDIO_DIMENSION_FOR_A_PACK = TextKey.of(
            "iris.modded.help.entry.command.open",
            "Open a temporary studio dimension for a pack"
    );
    public static final TextKey COMMAND_CLOSE_CLOSE_THE_OPEN_STUDIO_DIMENSION_AND_DISCARD_ITS_WORLD = TextKey.of(
            "iris.modded.help.entry.command.close",
            "Close the open studio dimension and discard its world"
    );
    public static final TextKey COMMAND_TPSTUDIO_TELEPORT_INTO_THE_OPEN_STUDIO_DIMENSION = TextKey.of(
            "iris.modded.help.entry.command.tpstudio",
            "Teleport into the open studio dimension"
    );
    public static final TextKey COMMAND_STATUS_SHOW_THE_OPEN_STUDIO_DIMENSION_AND_ITS_PACK = TextKey.of(
            "iris.modded.help.entry.command.status_studio",
            "Show the open studio dimension and its pack"
    );
    public static final TextKey COMMAND_NOISE_OPEN_THE_NOISE_EXPLORER_GUI_ON_THE_SERVER_DISPLAY = TextKey.of(
            "iris.modded.help.entry.command.noise",
            "Open the Noise Explorer GUI on the server display"
    );
    public static final TextKey COMMAND_MAP_OPEN_THE_VISION_MAP_GUI_ON_THE_SERVER_DISPLAY = TextKey.of(
            "iris.modded.help.entry.command.map",
            "Open the Vision map GUI on the server display"
    );
    public static final TextKey COMMAND_VSCODE_REGENERATE_THE_CODE_WORKSPACE_FOR_A_PACK_AND_OPEN_IT_IN_YOUR = TextKey.of(
            "iris.modded.help.entry.command.vscode",
            "Regenerate the .code-workspace for a pack and open it in your desktop editor"
    );
    public static final TextKey COMMAND_UPDATE_REGENERATE_THE_CODE_WORKSPACE_FOR_A_PACK = TextKey.of(
            "iris.modded.help.entry.command.update",
            "Regenerate the .code-workspace for a pack"
    );
    public static final TextKey COMMAND_IMPORTVANILLA_EXPLAIN_VANILLA_IMPORT_WORKFLOW = TextKey.of(
            "iris.modded.help.entry.command.importvanilla",
            "Explain vanilla import workflow"
    );
    public static final TextKey COMMAND_VALIDATE_VALIDATE_A_PACK_OR_EVERY_PACK = TextKey.of(
            "iris.modded.help.entry.command.validate",
            "Validate a pack or every pack"
    );
    public static final TextKey COMMAND_CLEANUP_PREVIEW_OR_QUARANTINE_UNUSED_RESOURCE_CANDIDATES = TextKey.of(
            "iris.modded.help.entry.command.cleanup",
            "Preview or quarantine unused-resource candidates"
    );
    public static final TextKey COMMAND_RESTORE_PREVIEW_OR_RESTORE_THE_LATEST_QUARANTINE = TextKey.of(
            "iris.modded.help.entry.command.restore",
            "Preview or restore the latest quarantine"
    );
    public static final TextKey COMMAND_STATUS_SHOW_CACHED_VALIDATION_STATUS = TextKey.of(
            "iris.modded.help.entry.command.status_validation",
            "Show cached validation status"
    );
    public static final TextKey COMMAND_COMPAT_SHOW_CONTENT_THAT_CANNOT_GENERATE_ON_THIS_MINECRAFT_VERSION = TextKey.of(
            "iris.modded.help.entry.command.compat_content",
            "Show content that cannot generate on this Minecraft version"
    );
    public static final TextKey COMMAND_ENABLE_CREATE_AND_INJECT_A_PERSISTENT_IRIS_DIMENSION_AT_RUNTIME_DOWNLOADS_THE_PACK = TextKey.of(
            "iris.modded.help.entry.command.enable",
            "Create and inject a persistent Iris dimension at runtime; refuses if the pack is missing, quote pack:dimensionKey to pick a specific pack dimension"
    );
    public static final TextKey COMMAND_REPLACE_OVERWORLD_INJECT_AN_IRIS_PRIMARY_WORLD_AND_ROUTE_PLAYERS_THERE_INSTEAD_OF_THE = TextKey.of(
            "iris.modded.help.entry.command.replace_overworld",
            "Inject an Iris primary world and route players there instead of the vanilla overworld"
    );
    public static final TextKey COMMAND_DISABLE_EVACUATE_AND_UNLOAD_AN_IRIS_DIMENSION_WORLD_DATA_ON_DISK_IS_KEPT = TextKey.of(
            "iris.modded.help.entry.command.disable",
            "Evacuate and unload an Iris dimension; world data on disk is kept for re-enabling"
    );
    public static final TextKey COMMAND_DELETE_DISABLE_AN_IRIS_DIMENSION_AND_WIPE_ITS_CHUNK_AND_MANTLE_DATA_FROM = TextKey.of(
            "iris.modded.help.entry.command.delete",
            "Disable an Iris dimension and wipe its chunk and mantle data from disk"
    );
    public static final TextKey COMMAND_LIST_LIST_LOADED_IRIS_DIMENSIONS = TextKey.of(
            "iris.modded.help.entry.command.list",
            "List loaded Iris dimensions"
    );
    public static final TextKey COMMAND_STATUS_SHOW_LOADED_IRIS_DIMENSIONS_AND_THE_CONFIGURED_PRIMARY_WORLD = TextKey.of(
            "iris.modded.help.entry.command.status_world",
            "Show loaded Iris dimensions and the configured primary world"
    );
    public static final TextKey COMMAND_STATUS_CHECK_LOADED_IRIS_DIMENSION_TYPE_OVERRIDES = TextKey.of(
            "iris.modded.help.entry.command.status_datapack",
            "Check loaded Iris dimension type overrides"
    );
    public static final TextKey COMMAND_INSTALL_INSTALL_DIMENSION_TYPE_OVERRIDES_FOR_LOADED_IRIS_DIMENSIONS = TextKey.of(
            "iris.modded.help.entry.command.install",
            "Install dimension type overrides for loaded Iris dimensions"
    );
    public static final TextKey COMMAND_LIST_LIST_CONFIGURED_AND_INSTALLED_DATAPACKS = TextKey.of(
            "iris.modded.help.entry.command.list_datapacks",
            "List configured and installed datapacks"
    );
    public static final TextKey COMMAND_INGEST_EXPLAIN_BUKKIT_DATAPACK_INGEST_WORKFLOW = TextKey.of(
            "iris.modded.help.entry.command.ingest",
            "Explain Bukkit datapack ingest workflow"
    );
    public static final TextKey COMMAND_REMOVE_EXPLAIN_DATAPACK_REMOVAL_WORKFLOW = TextKey.of(
            "iris.modded.help.entry.command.remove",
            "Explain datapack removal workflow"
    );
    public static final TextKey COMMAND_LIST_REGENERATE_STRUCTURE_INDEX_JSON = TextKey.of(
            "iris.modded.help.entry.command.list_structures",
            "Regenerate structure-index.json"
    );
    public static final TextKey COMMAND_INFO_RESOLVE_AN_IRIS_STRUCTURE_GRAPH_AND_REPORT_BOUNDS = TextKey.of(
            "iris.modded.help.entry.command.info_2",
            "Resolve an Iris structure graph and report bounds"
    );
    public static final TextKey COMMAND_PLACE_ASSEMBLE_AND_PLACE_AN_IRIS_STRUCTURE_AT_YOUR_LOCATION = TextKey.of(
            "iris.modded.help.entry.command.place",
            "Assemble and place an Iris structure at your location"
    );
    public static final TextKey COMMAND_IMPORT_EXPLAIN_BUKKIT_STRUCTURE_IMPORT_WORKFLOW = TextKey.of(
            "iris.modded.help.entry.command.import",
            "Explain Bukkit structure import workflow"
    );
    public static final TextKey COMMAND_CAPTURE_EXPLAIN_BUKKIT_STRUCTURE_CAPTURE_WORKFLOW = TextKey.of(
            "iris.modded.help.entry.command.capture",
            "Explain Bukkit structure capture workflow"
    );
    public static final TextKey COMMAND_VERIFY_REPORT_NATIVE_AND_IRIS_STRUCTURE_REACHABILITY_IN_THE_CURRENT_DIMENSION = TextKey.of(
            "iris.modded.help.entry.command.verify",
            "Report native and Iris structure reachability in the current dimension"
    );
    public static final TextKey COMMAND_NETWORK_LIST_NETWORK_INTERFACES_AND_THEIR_ADDRESSES = TextKey.of(
            "iris.modded.help.entry.command.network",
            "List network interfaces and their addresses"
    );
    public static final TextKey COMMAND_WHAT_HERE_INSPECT_CURRENT_IRIS_CONTEXT = TextKey.of(
            "iris.modded.help.entry.command.what.here",
            "Inspect the Iris biome, region, cave biome, surface and chunk at your position"
    );
    public static final TextKey COMMAND_WHAT_BIOME_INSPECT_CURRENT_BIOME = TextKey.of(
            "iris.modded.help.entry.command.what.biome",
            "Inspect the Iris biome, configured derivative and registered native biome"
    );
    public static final TextKey COMMAND_WHAT_REGION_INSPECT_CURRENT_REGION = TextKey.of(
            "iris.modded.help.entry.command.what.region",
            "Inspect the Iris region at your current chunk"
    );
    public static final TextKey COMMAND_WHAT_BLOCK_INSPECT_TARGET_BLOCK = TextKey.of(
            "iris.modded.help.entry.command.what.block",
            "Inspect the targeted block state, properties, object and block entity"
    );
    public static final TextKey COMMAND_WHAT_HAND_INSPECT_HELD_ITEM = TextKey.of(
            "iris.modded.help.entry.command.what.hand",
            "Inspect the held item and its default block state"
    );
    public static final TextKey COMMAND_WHAT_MARKERS_REVEAL_NEARBY_MARKERS = TextKey.of(
            "iris.modded.help.entry.command.what.markers",
            "Reveal nearby Iris Mantle markers with particles"
    );
    public static final TextKey COMMAND_HEIGHT_PRINT_WORLD_HEIGHT = TextKey.of(
            "iris.modded.help.entry.command.height",
            "Print the current dimension height range"
    );
    public static final TextKey COMMAND_WORLDS_LIST_WORLD_ACCESS = TextKey.of(
            "iris.modded.help.entry.command.worlds",
            "List loaded dimensions and identify the Iris-generated dimensions"
    );
    public static final TextKey COMMAND_MAINWORLD_CONFIGURE_PRIMARY_WORLD_PRESET = TextKey.of(
            "iris.modded.help.entry.command.mainworld",
            "Configure or clear the Iris primary-world preset used on restart"
    );
    public static final TextKey COMMAND_OBJECT_WE_BUKKIT_ONLY = TextKey.of(
            "iris.modded.help.entry.command.object.we",
            "Explain why WorldEdit selection import requires the Bukkit plugin"
    );
    public static final TextKey COMMAND_OBJECT_STUDIO_BUKKIT_ONLY = TextKey.of(
            "iris.modded.help.entry.command.object.studio",
            "Explain why the object studio world requires the Bukkit toolchain"
    );
    public static final TextKey COMMAND_OBJECT_CONVERT_BUKKIT_ONLY = TextKey.of(
            "iris.modded.help.entry.command.object.convert",
            "Explain why schematic conversion requires the Bukkit plugin"
    );
    public static final TextKey COMMAND_STUDIO_LOOT_BUKKIT_ONLY = TextKey.of(
            "iris.modded.help.entry.command.studio.loot",
            "Explain why the Bukkit loot simulation GUI is unavailable"
    );
    public static final TextKey COMMAND_STUDIO_PROFILE_BUKKIT_ONLY = TextKey.of(
            "iris.modded.help.entry.command.studio.profile",
            "Explain why Bukkit pack profiling is unavailable"
    );
    public static final TextKey COMMAND_STUDIO_SPAWN_BUKKIT_ONLY = TextKey.of(
            "iris.modded.help.entry.command.studio.spawn",
            "Explain why Bukkit Iris entity spawning is unavailable"
    );
    public static final TextKey COMMAND_STUDIO_OBJECTS_BUKKIT_ONLY = TextKey.of(
            "iris.modded.help.entry.command.studio.objects",
            "Explain why the Bukkit chunk object report is unavailable"
    );

    private static final List<MessageKey> KEYS = List.of(
            COMMAND_VERSION_PRINT_VERSION_INFORMATION,
            COMMAND_INFO_LIST_LOADED_IRIS_DIMENSIONS_AND_PACK_DETAILS,
            COMMAND_WHAT_INSPECT_THE_IRIS_BIOME_REGION_CAVE_BIOME_SURFACE_AND_CHUNK_AT_YOUR,
            GROUP_FIND_FIND_AND_TELEPORT_TO_IRIS_BIOMES_REGIONS_OBJECTS_IRIS_STRUCTURES_NATIVE_STRUCTURES,
            COMMAND_TP_TELEPORT_YOURSELF_OR_A_NAMED_PLAYER_INTO_A_LOADED_IRIS_DIMENSION,
            COMMAND_EVACUATE_TELEPORT_EVERY_PLAYER_OUT_OF_AN_IRIS_DIMENSION_TO_THE_PRIMARY_WORLD,
            COMMAND_SEED_PRINT_WORLD_AND_ENGINE_SEED_INFORMATION,
            COMMAND_DEBUG_TOGGLE_IRIS_DEBUG_LOGGING_AND_SAVE_SETTINGS_JSON,
            COMMAND_RELOAD_RELOAD_SETTINGS_JSON_ALSO_HOTLOADED_AUTOMATICALLY_EVERY_3S,
            COMMAND_DOWNLOAD_DOWNLOAD_A_PACK_PROJECT,
            COMMAND_METRICS_PRINT_GENERATION_METRICS_FOR_YOUR_CURRENT_IRIS_DIMENSION,
            COMMAND_REGEN_DELETE_AND_REGENERATE_NEARBY_CHUNKS_IN_PLACE,
            GROUP_PREGEN_PREGENERATE_AN_IRIS_DIMENSION,
            COMMAND_WAND_GET_AN_IRIS_OBJECT_WAND,
            GROUP_OBJECT_OBJECT_WAND_SAVE_PASTE_ANALYZE_AND_UNDO_TOOLS,
            GROUP_EDIT_OPEN_PACK_BIOME_REGION_AND_DIMENSION_JSON_FILES_IN_YOUR_DESKTOP_EDITOR,
            COMMAND_CREATE_CREATE_AND_INJECT_A_PERSISTENT_IRIS_DIMENSION_QUOTE_PACK_DIMENSIONKEY_TO_PICK,
            GROUP_STUDIO_PACK_PROJECT_CREATION_PACKAGING_AND_REPORTS,
            GROUP_PACK_PACK_VALIDATION_AND_MAINTENANCE,
            GROUP_WORLD_RUNTIME_IRIS_DIMENSION_CREATION_REMOVAL_AND_STATUS,
            GROUP_DATAPACK_WORLD_DATAPACK_INSTALL_AND_STATUS_HELPERS,
            GROUP_STRUCTURE_IRIS_STRUCTURE_INDEX_INFO_AND_PLACEMENT_TOOLS,
            COMMAND_GOLDENHASH_GENERATE_DETERMINISTIC_BLOCK_HASHES_FOR_PARITY_TESTING,
            GROUP_DEVELOPER_DEVELOPER_DIAGNOSTICS_NETWORK_INTERFACES_REGION_FILE_SCAN,
            COMMAND_BIOME_FIND_AN_IRIS_BIOME,
            COMMAND_REGION_FIND_AN_IRIS_REGION,
            COMMAND_OBJECT_FIND_AN_OBJECT_PLACEMENT,
            COMMAND_STRUCTURE_FIND_AN_IRIS_PLACED_OR_NATIVE_DATAPACK_STRUCTURE,
            COMMAND_POI_FIND_A_SUPPORTED_POINT_OF_INTEREST,
            COMMAND_BIOME_OPEN_A_BIOME_JSON_IN_YOUR_DESKTOP_EDITOR_NO_KEY_OPENS_THE,
            COMMAND_REGION_OPEN_A_REGION_JSON_IN_YOUR_DESKTOP_EDITOR_NO_KEY_OPENS_THE,
            COMMAND_DIMENSION_OPEN_THE_CURRENT_PACK_S_DIMENSION_JSON_IN_YOUR_DESKTOP_EDITOR,
            COMMAND_START_START_PREGENERATION_RADIUS_IN_BLOCKS_RESUMABLE_CHECKPOINT_CACHE_ON_BY_DEFAULT_CENTER,
            COMMAND_STOP_STOP_THE_ACTIVE_PREGENERATION_TASK,
            COMMAND_PAUSE_PAUSE_OR_RESUME_PREGENERATION,
            COMMAND_STATUS_SHOW_PREGENERATION_STATUS,
            COMMAND_WAND_GET_AN_IRIS_OBJECT_WAND_2,
            COMMAND_DUST_GET_DUST_THAT_REVEALS_OBJECT_PLACEMENTS,
            COMMAND_SAVE_SAVE_THE_SELECTED_WAND_VOLUME_AS_AN_OBJECT,
            COMMAND_PASTE_PASTE_AN_OBJECT_AT_YOUR_POSITION_OR_A_GIVEN_POSITION_OPTIONALLY_ROTATED,
            COMMAND_EXPAND_EXPAND_THE_WAND_SELECTION_IN_YOUR_LOOKING_DIRECTION,
            COMMAND_CONTRACT_CONTRACT_THE_WAND_SELECTION_IN_YOUR_LOOKING_DIRECTION,
            COMMAND_SHIFT_SHIFT_THE_WAND_SELECTION_IN_YOUR_LOOKING_DIRECTION,
            COMMAND_POSITION1_SET_SELECTION_POINT_1,
            COMMAND_POSITION2_SET_SELECTION_POINT_2,
            COMMAND_X_Y_AUTOSELECT_UP_AND_OUT,
            COMMAND_X_Y_AUTOSELECT_UP_DOWN_AND_OUT,
            COMMAND_ANALYZE_SHOW_OBJECT_COMPOSITION,
            COMMAND_SHRINK_SHRINK_AN_OBJECT_TO_ITS_MINIMUM_SIZE,
            COMMAND_PLAUSIBILIZE_GROW_BRANCHES_SO_TREE_LEAVES_SURVIVE_VANILLA_DECAY,
            COMMAND_UNDO_UNDO_PASTED_OBJECTS,
            COMMAND_CREATE_CREATE_A_NEW_PACK_PROJECT,
            COMMAND_PACKAGE_PACKAGE_A_DIMENSION_INTO_A_COMPRESSED_FORMAT,
            COMMAND_VERSION_PRINT_A_PACK_VERSION,
            COMMAND_REGIONS_CALCULATE_NEARBY_REGION_DISTRIBUTION,
            COMMAND_OPEN_OPEN_A_TEMPORARY_STUDIO_DIMENSION_FOR_A_PACK,
            COMMAND_CLOSE_CLOSE_THE_OPEN_STUDIO_DIMENSION_AND_DISCARD_ITS_WORLD,
            COMMAND_TPSTUDIO_TELEPORT_INTO_THE_OPEN_STUDIO_DIMENSION,
            COMMAND_STATUS_SHOW_THE_OPEN_STUDIO_DIMENSION_AND_ITS_PACK,
            COMMAND_NOISE_OPEN_THE_NOISE_EXPLORER_GUI_ON_THE_SERVER_DISPLAY,
            COMMAND_MAP_OPEN_THE_VISION_MAP_GUI_ON_THE_SERVER_DISPLAY,
            COMMAND_VSCODE_REGENERATE_THE_CODE_WORKSPACE_FOR_A_PACK_AND_OPEN_IT_IN_YOUR,
            COMMAND_UPDATE_REGENERATE_THE_CODE_WORKSPACE_FOR_A_PACK,
            COMMAND_IMPORTVANILLA_EXPLAIN_VANILLA_IMPORT_WORKFLOW,
            COMMAND_VALIDATE_VALIDATE_A_PACK_OR_EVERY_PACK,
            COMMAND_CLEANUP_PREVIEW_OR_QUARANTINE_UNUSED_RESOURCE_CANDIDATES,
            COMMAND_RESTORE_PREVIEW_OR_RESTORE_THE_LATEST_QUARANTINE,
            COMMAND_STATUS_SHOW_CACHED_VALIDATION_STATUS,
            COMMAND_COMPAT_SHOW_CONTENT_THAT_CANNOT_GENERATE_ON_THIS_MINECRAFT_VERSION,
            COMMAND_ENABLE_CREATE_AND_INJECT_A_PERSISTENT_IRIS_DIMENSION_AT_RUNTIME_DOWNLOADS_THE_PACK,
            COMMAND_REPLACE_OVERWORLD_INJECT_AN_IRIS_PRIMARY_WORLD_AND_ROUTE_PLAYERS_THERE_INSTEAD_OF_THE,
            COMMAND_DISABLE_EVACUATE_AND_UNLOAD_AN_IRIS_DIMENSION_WORLD_DATA_ON_DISK_IS_KEPT,
            COMMAND_DELETE_DISABLE_AN_IRIS_DIMENSION_AND_WIPE_ITS_CHUNK_AND_MANTLE_DATA_FROM,
            COMMAND_LIST_LIST_LOADED_IRIS_DIMENSIONS,
            COMMAND_STATUS_SHOW_LOADED_IRIS_DIMENSIONS_AND_THE_CONFIGURED_PRIMARY_WORLD,
            COMMAND_STATUS_CHECK_LOADED_IRIS_DIMENSION_TYPE_OVERRIDES,
            COMMAND_INSTALL_INSTALL_DIMENSION_TYPE_OVERRIDES_FOR_LOADED_IRIS_DIMENSIONS,
            COMMAND_LIST_LIST_CONFIGURED_AND_INSTALLED_DATAPACKS,
            COMMAND_INGEST_EXPLAIN_BUKKIT_DATAPACK_INGEST_WORKFLOW,
            COMMAND_REMOVE_EXPLAIN_DATAPACK_REMOVAL_WORKFLOW,
            COMMAND_LIST_REGENERATE_STRUCTURE_INDEX_JSON,
            COMMAND_INFO_RESOLVE_AN_IRIS_STRUCTURE_GRAPH_AND_REPORT_BOUNDS,
            COMMAND_PLACE_ASSEMBLE_AND_PLACE_AN_IRIS_STRUCTURE_AT_YOUR_LOCATION,
            COMMAND_IMPORT_EXPLAIN_BUKKIT_STRUCTURE_IMPORT_WORKFLOW,
            COMMAND_CAPTURE_EXPLAIN_BUKKIT_STRUCTURE_CAPTURE_WORKFLOW,
            COMMAND_VERIFY_REPORT_NATIVE_AND_IRIS_STRUCTURE_REACHABILITY_IN_THE_CURRENT_DIMENSION,
            COMMAND_NETWORK_LIST_NETWORK_INTERFACES_AND_THEIR_ADDRESSES,
            COMMAND_WHAT_HERE_INSPECT_CURRENT_IRIS_CONTEXT,
            COMMAND_WHAT_BIOME_INSPECT_CURRENT_BIOME,
            COMMAND_WHAT_REGION_INSPECT_CURRENT_REGION,
            COMMAND_WHAT_BLOCK_INSPECT_TARGET_BLOCK,
            COMMAND_WHAT_HAND_INSPECT_HELD_ITEM,
            COMMAND_WHAT_MARKERS_REVEAL_NEARBY_MARKERS,
            COMMAND_HEIGHT_PRINT_WORLD_HEIGHT,
            COMMAND_WORLDS_LIST_WORLD_ACCESS,
            COMMAND_MAINWORLD_CONFIGURE_PRIMARY_WORLD_PRESET,
            COMMAND_OBJECT_WE_BUKKIT_ONLY,
            COMMAND_OBJECT_STUDIO_BUKKIT_ONLY,
            COMMAND_OBJECT_CONVERT_BUKKIT_ONLY,
            COMMAND_STUDIO_LOOT_BUKKIT_ONLY,
            COMMAND_STUDIO_PROFILE_BUKKIT_ONLY,
            COMMAND_STUDIO_SPAWN_BUKKIT_ONLY,
            COMMAND_STUDIO_OBJECTS_BUKKIT_ONLY
    );

    private ModdedHelpMessages() {
    }

    public static List<MessageKey> keys() {
        return KEYS;
    }
}

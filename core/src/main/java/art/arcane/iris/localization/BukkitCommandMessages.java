package art.arcane.iris.localization;

import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;

public final class BukkitCommandMessages {
    public static final TextKey COMMAND_DATAPACK_STARTING_DATAPACK_INGEST = TextKey.of(
            "iris.bukkit.commanddatapack.starting_datapack_ingest",
            C.GRAY + "Starting datapack ingest..."
    );
    public static final TextKey COMMAND_DATAPACK_CONFIGURED_DATAPACK_IMPORTS = TextKey.of(
            "iris.bukkit.commanddatapack.configured_datapack_imports",
            C.GREEN + "Configured datapack imports: " + C.WHITE + "{value}"
    );
    public static final TextKey COMMAND_DATAPACK_MESSAGE = TextKey.of(
            "iris.bukkit.commanddatapack.message",
            C.GRAY + "  - " + C.WHITE + "{url}"
    );
    public static final TextKey COMMAND_DATAPACK_INSTALLED_DATAPACKS = TextKey.of(
            "iris.bukkit.commanddatapack.installed_datapacks",
            C.GREEN + "Installed datapacks: " + C.WHITE + "{value}"
    );
    public static final TextKey COMMAND_DATAPACK_MESSAGE_2 = TextKey.of(
            "iris.bukkit.commanddatapack.message_2",
            C.GRAY + "  - " + C.WHITE + "{value}" + C.GRAY + " " + "{value2}"
    );
    public static final TextKey COMMAND_DATAPACK_ADD_MODRINTH_URLS_DIMENSION_S_DATAPACKIMPORTS_LIST_THEN_RUN_IRIS = TextKey.of(
            "iris.bukkit.commanddatapack.add_modrinth_urls_dimension_s_datapackimports_list_then_run_iris",
            C.YELLOW + "Add source URLs to a dimension's 'datapackImports' list or use the local import folder, then run /iris datapack ingest."
    );
    public static final TextKey COMMAND_DEVELOPER_GENHASH_STARTED_CHUNKS = TextKey.of(
            "iris.bukkit.commanddeveloper.genhash_started_chunks",
            C.GREEN + "genhash started: " + "{value}" + " chunks..."
    );
    public static final TextKey COMMAND_DEVELOPER_GENHASH_FAILED_AT_CHUNK = TextKey.of(
            "iris.bukkit.commanddeveloper.genhash_failed_at_chunk",
            C.RED + "genhash failed at chunk " + "{rx}" + "," + "{rz}" + ": " + "{value}"
    );
    public static final TextKey COMMAND_DEVELOPER_GENHASH_GLOBAL_CHUNKS_SOLID = TextKey.of(
            "iris.bukkit.commanddeveloper.genhash_global_chunks_solid",
            C.GREEN + "genhash global=" + C.GOLD + "{value}" + C.GREEN + " chunks=" + "{value2}" + " solid=" + "{solidBlocks}" + " in " + "{value3}"
    );
    public static final TextKey COMMAND_FIND_NOT_IRIS_WORLD = TextKey.of(
            "iris.bukkit.commandfind.not_iris_world",
            C.GOLD + "Not in an Iris World!"
    );
    public static final TextKey COMMAND_FIND_UNKNOWN_STRUCTURE = TextKey.of(
            "iris.bukkit.commandfind.unknown_structure",
            C.RED + "Unknown structure: " + "{structureKey}"
    );
    public static final TextKey COMMAND_FIND_RUN_THIS_GAME_TELEPORT_STRUCTURE = TextKey.of(
            "iris.bukkit.commandfind.run_this_game_teleport_structure",
            C.GOLD + "Run this in-game to teleport to a structure."
    );
    public static final TextKey COMMAND_FIND_LOCATING = TextKey.of(
            "iris.bukkit.commandfind.locating",
            C.GRAY + "Locating " + "{structureKey}" + "..."
    );
    public static final TextKey COMMAND_FIND_RUN_THIS_GAME_TELEPORT_STRUCTURE_2 = TextKey.of(
            "iris.bukkit.commandfind.run_this_game_teleport_structure_2",
            C.GOLD + "Run this in-game to teleport to a structure."
    );
    public static final TextKey COMMAND_FIND_LOCATING_2 = TextKey.of(
            "iris.bukkit.commandfind.locating_2",
            C.GRAY + "Locating " + "{structure}" + "..."
    );
    public static final TextKey COMMAND_FIND_TELEPORTED = TextKey.of(
            "iris.bukkit.commandfind.teleported",
            C.GREEN + "Teleported to " + "{structure}" + " @ " + "{value}" + ", " + "{y}" + ", " + "{value2}"
    );
    public static final TextKey COMMAND_IRIS_SUCCESSFULLY_REMOVED_WORLD_FOLDER = TextKey.of(
            "iris.bukkit.commandiris.successfully_removed_world_folder",
            C.GREEN + "Successfully removed world folder"
    );
    public static final TextKey COMMAND_IRIS_SUCCESSFULLY_REMOVED_WORLD_FOLDER_2 = TextKey.of(
            "iris.bukkit.commandiris.successfully_removed_world_folder_2",
            C.GREEN + "Successfully removed world folder"
    );
    public static final TextKey COMMAND_IRIS_FAILED_REMOVE_WORLD_FOLDER = TextKey.of(
            "iris.bukkit.commandiris.failed_remove_world_folder",
            C.RED + "Failed to remove world folder"
    );
    public static final TextKey COMMAND_OBJECT_NO_PACKS_WITH_OBJECTS_WERE_FOUND_ON_THIS_SERVER = TextKey.of(
            "iris.bukkit.commandobject.no_packs_with_objects_were_found_on_this_server",
            C.RED + "No packs with objects were found on this server."
    );
    public static final TextKey COMMAND_OBJECT_NO_OBJECTS_PLACE_ACROSS_SELECTED_PACK_S = TextKey.of(
            "iris.bukkit.commandobject.no_objects_place_across_selected_pack_s",
            C.RED + "No objects to place across the selected pack(s)."
    );
    public static final TextKey COMMAND_OBJECT_OPENING_OBJECT_STUDIO_OBJECTS = TextKey.of(
            "iris.bukkit.commandobject.opening_object_studio_objects",
            C.GREEN + "Opening Object Studio for " + "{scope}" + " (" + "{totalObjects}" + " objects)"
    );
    public static final TextKey COMMAND_OBJECT_FAILED_OPEN_OBJECT_STUDIO = TextKey.of(
            "iris.bukkit.commandobject.failed_open_object_studio",
            C.RED + "Failed to open object studio: " + "{value}"
    );
    public static final TextKey COMMAND_PACK_YOU_MUST_SPECIFY_PACK_NAME = TextKey.of(
            "iris.bukkit.commandpack.you_must_specify_pack_name",
            C.RED + "You must specify a pack name."
    );
    public static final TextKey COMMAND_PACK_PACK_NOT_FOUND_UNDER_PACKS = TextKey.of(
            "iris.bukkit.commandpack.pack_not_found_under_packs",
            C.RED + "Pack '" + "{pack}" + "' not found under packs/."
    );
    public static final TextKey COMMAND_PACK_MESSAGE = TextKey.of(
            "iris.bukkit.commandpack.message",
            C.GRAY + "  - " + "{label}" + ": " + "{value}"
    );
    public static final TextKey COMMAND_PACK_MORE = TextKey.of(
            "iris.bukkit.commandpack.more",
            C.GRAY + "  ... and " + "{value}" + " more."
    );
    public static final TextKey COMMAND_STRUCTURE_VERIFYING_STRUCTURES_FROM_WITHIN_CHUNKS = TextKey.of(
            "iris.bukkit.commandstructure.verifying_structures_from_within_chunks",
            C.GREEN + "Verifying structures in " + C.WHITE + "{value}" + C.GREEN + " from " + "{value2}" + "," + "{value3}" + " within " + "{searchRadius}" + " chunks..."
    );
    public static final TextKey COMMAND_STUDIO_IMPORTING_VANILLA_CONTENT_INTO = TextKey.of(
            "iris.bukkit.commandstudio.importing_vanilla_content_into",
            C.GREEN + "Importing vanilla content into " + C.WHITE + "{value}" + C.GREEN + "..."
    );
    public static final TextKey COMMAND_STUDIO_IMPORTVANILLA_COMPLETE_OBJECTS_STRUCTURES_WRITTEN_FAILED = TextKey.of(
            "iris.bukkit.commandstudio.importvanilla_complete_objects_structures_written_failed",
            C.GREEN + "importvanilla complete: " + C.WHITE + "{imported}" + C.GREEN + " objects/structures written, " + C.WHITE + "{failed}" + C.GREEN + " failed."
    );
    public static final TextKey COMMAND_STUDIO_TREES_OBJECTS_ARE_UNDER_OBJECTS_VANILLA_REFERENCE_THEM_FROM_BIOME = TextKey.of(
            "iris.bukkit.commandstudio.trees_objects_are_under_objects_vanilla_reference_them_from_biome",
            C.GRAY + "Trees/objects are under objects/vanilla/...; reference them from biome object placements."
    );
    public static final TextKey COMMAND_STUDIO_NO_OPEN_STUDIO_PROJECTS = TextKey.of(
            "iris.bukkit.commandstudio.no_open_studio_projects",
            C.RED + "No open studio projects."
    );
    public static final TextKey COMMAND_STUDIO_CLOSING_STUDIO = TextKey.of(
            "iris.bukkit.commandstudio.closing_studio",
            C.YELLOW + "Closing studio..."
    );
    public static final TextKey COMMAND_STUDIO_STUDIO_CLOSE_FAILED = TextKey.of(
            "iris.bukkit.commandstudio.studio_close_failed",
            C.RED + "Studio close failed: " + "{value}"
    );
    public static final TextKey COMMAND_STUDIO_STUDIO_CLOSE_FAILED_2 = TextKey.of(
            "iris.bukkit.commandstudio.studio_close_failed_2",
            C.RED + "Studio close failed: " + "{value}"
    );
    public static final TextKey COMMAND_STUDIO_STUDIO_CLOSED_REMAINING_WORLD_FAMILY_CLEANUP_WAS_QUEUED_STARTUP_FALLBACK = TextKey.of(
            "iris.bukkit.commandstudio.studio_closed_remaining_world_family_cleanup_was_queued_startup_fallback",
            C.YELLOW + "Studio closed. Remaining world-family cleanup was queued for startup fallback."
    );
    public static final TextKey COMMAND_STUDIO_STUDIO_CLOSED = TextKey.of(
            "iris.bukkit.commandstudio.studio_closed",
            C.GREEN + "Studio closed."
    );
    public static final TextKey COMMAND_STUDIO_YOU_MUST_BE_STUDIO_WORLD_TOGGLE_DEBUG_SCOREBOARD = TextKey.of(
            "iris.bukkit.commandstudio.you_must_be_studio_world_toggle_debug_scoreboard",
            C.RED + "You must be in a Studio world to toggle the debug scoreboard."
    );
    public static final TextKey COMMAND_STUDIO_STUDIO_DEBUG_SCOREBOARD = TextKey.of(
            "iris.bukkit.commandstudio.studio_debug_scoreboard",
            "{value}" + "Studio debug scoreboard " + "{value2}"
    );
    public static final TextKey COMMAND_STUDIO_COULD_NOT_UPDATE_STUDIO_DEBUG_SCOREBOARD_RIGHT_NOW = TextKey.of(
            "iris.bukkit.commandstudio.could_not_update_studio_debug_scoreboard_right_now",
            C.RED + "Could not update the Studio debug scoreboard right now."
    );
    public static final TextKey COMMAND_STUDIO_OPENED_INVENTORY = TextKey.of(
            "iris.bukkit.commandstudio.opened_inventory",
            C.GREEN + "Opened inventory!"
    );
    public static final TextKey COMMAND_STUDIO_GENERATING_DATA = TextKey.of(
            "iris.bukkit.commandstudio.generating_data",
            C.GRAY + "Generating data..."
    );
    public static final TextKey COMMAND_STUDIO_DONE = TextKey.of(
            "iris.bukkit.commandstudio.done",
            C.GREEN + "Done!"
    );
    public static final TextKey COMMAND_STUDIO_MESSAGE = TextKey.of(
            "iris.bukkit.commandstudio.message",
            C.GREEN + "{k}" + ": " + "{value}" + " / " + "{value2}" + "%"
    );
    public static final TextKey COMMAND_S_V_C_YOU_LACK_PERMISSION = TextKey.of(
            "iris.bukkit.commandsvc.you_lack_permission",
            "You lack the Permission '" + "{ROOTPERMISSION}" + "'"
    );
    public static final TextKey IRIS_ENGINE_STATUS_MESSAGE = TextKey.of(
            "iris.bukkit.irisenginestatus.message",
            C.DARK_PURPLE + "-------------------------"
    );
    public static final TextKey IRIS_ENGINE_STATUS_STATUS = TextKey.of(
            "iris.bukkit.irisenginestatus.status",
            C.DARK_PURPLE + "Status:"
    );
    public static final TextKey IRIS_ENGINE_STATUS_SERVICE = TextKey.of(
            "iris.bukkit.irisenginestatus.service",
            C.DARK_PURPLE + "- Service: " + C.LIGHT_PURPLE + "{value}"
    );
    public static final TextKey IRIS_ENGINE_STATUS_METRICS = TextKey.of(
            "iris.bukkit.irisenginestatus.metrics",
            C.DARK_PURPLE + "- Metrics: " + C.LIGHT_PURPLE + "{value}"
    );
    public static final TextKey IRIS_ENGINE_STATUS_MAINTENANCE_PERIOD = TextKey.of(
            "iris.bukkit.irisenginestatus.maintenance_period",
            C.DARK_PURPLE + "- Maintenance Period: " + C.LIGHT_PURPLE + "{value}"
    );
    public static final TextKey IRIS_ENGINE_STATUS_WORKER_PARALLELISM = TextKey.of(
            "iris.bukkit.irisenginestatus.worker_parallelism",
            C.DARK_PURPLE + "- Worker Parallelism: " + C.LIGHT_PURPLE + "{value}"
    );
    public static final TextKey IRIS_ENGINE_STATUS_ACTIVE_WORLD_TASKS = TextKey.of(
            "iris.bukkit.irisenginestatus.active_world_tasks",
            C.DARK_PURPLE + "- Active World Tasks: " + C.LIGHT_PURPLE + "{value}"
    );
    public static final TextKey IRIS_ENGINE_STATUS_TECTONIC_PLATES = TextKey.of(
            "iris.bukkit.irisenginestatus.tectonic_plates",
            C.DARK_PURPLE + "Tectonic Plates:"
    );
    public static final TextKey IRIS_ENGINE_STATUS_CONFIGURED_RETENTION = TextKey.of(
            "iris.bukkit.irisenginestatus.configured_retention",
            C.DARK_PURPLE + "- Configured Retention: " + C.LIGHT_PURPLE + "{value}"
    );
    public static final TextKey IRIS_ENGINE_STATUS_HEAP_USAGE = TextKey.of(
            "iris.bukkit.irisenginestatus.heap_usage",
            C.DARK_PURPLE + "- Heap Usage: " + C.LIGHT_PURPLE + "{value}"
    );
    public static final TextKey IRIS_ENGINE_STATUS_RESIDENT = TextKey.of(
            "iris.bukkit.irisenginestatus.resident",
            C.DARK_PURPLE + "- Resident: " + C.LIGHT_PURPLE + "{value}"
    );
    public static final TextKey IRIS_ENGINE_STATUS_QUEUED = TextKey.of(
            "iris.bukkit.irisenginestatus.queued",
            C.DARK_PURPLE + "- Queued: " + C.LIGHT_PURPLE + "{value}"
    );
    public static final TextKey IRIS_ENGINE_STATUS_AVERAGE_IDLE_DURATION = TextKey.of(
            "iris.bukkit.irisenginestatus.average_idle_duration",
            C.DARK_PURPLE + "- Average Idle Duration: " + C.LIGHT_PURPLE + "{value}"
    );
    public static final TextKey IRIS_ENGINE_STATUS_MAX_IDLE_DURATION = TextKey.of(
            "iris.bukkit.irisenginestatus.max_idle_duration",
            C.DARK_PURPLE + "- Max Idle Duration: " + C.LIGHT_PURPLE + "{value}"
    );
    public static final TextKey IRIS_ENGINE_STATUS_MIN_IDLE_DURATION = TextKey.of(
            "iris.bukkit.irisenginestatus.min_idle_duration",
            C.DARK_PURPLE + "- Min Idle Duration: " + C.LIGHT_PURPLE + "{value}"
    );
    public static final TextKey IRIS_ENGINE_STATUS_CACHES = TextKey.of(
            "iris.bukkit.irisenginestatus.caches",
            C.DARK_PURPLE + "Caches:"
    );
    public static final TextKey IRIS_ENGINE_STATUS_RESOURCE = TextKey.of(
            "iris.bukkit.irisenginestatus.resource",
            C.DARK_PURPLE + "- Resource: " + C.LIGHT_PURPLE + "{value}" + " (" + "{value2}" + ")"
    );
    public static final TextKey IRIS_ENGINE_STATUS_2D_STREAM = TextKey.of(
            "iris.bukkit.irisenginestatus.2d_stream",
            C.DARK_PURPLE + "- 2D Stream: " + C.LIGHT_PURPLE + "{value}" + " (" + "{value2}" + ")"
    );
    public static final TextKey IRIS_ENGINE_STATUS_3D_STREAM = TextKey.of(
            "iris.bukkit.irisenginestatus.3d_stream",
            C.DARK_PURPLE + "- 3D Stream: " + C.LIGHT_PURPLE + "{value}" + " (" + "{value2}" + ")"
    );
    public static final TextKey IRIS_ENGINE_STATUS_OTHER = TextKey.of(
            "iris.bukkit.irisenginestatus.other",
            C.DARK_PURPLE + "- Other: " + C.LIGHT_PURPLE + "{value}" + " (" + "{value2}" + ")"
    );
    public static final TextKey IRIS_ENGINE_STATUS_OTHER_2 = TextKey.of(
            "iris.bukkit.irisenginestatus.other_2",
            C.DARK_PURPLE + "Other:"
    );
    public static final TextKey IRIS_ENGINE_STATUS_IRIS_WORLDS = TextKey.of(
            "iris.bukkit.irisenginestatus.iris_worlds",
            C.DARK_PURPLE + "- Iris Worlds: " + C.LIGHT_PURPLE + "{value}"
    );
    public static final TextKey IRIS_ENGINE_STATUS_LOADED_CHUNKS = TextKey.of(
            "iris.bukkit.irisenginestatus.loaded_chunks",
            C.DARK_PURPLE + "- Loaded Chunks: " + C.LIGHT_PURPLE + "{value}"
    );
    public static final TextKey IRIS_ENGINE_STATUS_MESSAGE_2 = TextKey.of(
            "iris.bukkit.irisenginestatus.message_2",
            C.DARK_PURPLE + "-------------------------"
    );

    private static final List<MessageKey> KEYS = List.of(
            COMMAND_DATAPACK_STARTING_DATAPACK_INGEST,
            COMMAND_DATAPACK_CONFIGURED_DATAPACK_IMPORTS,
            COMMAND_DATAPACK_MESSAGE,
            COMMAND_DATAPACK_INSTALLED_DATAPACKS,
            COMMAND_DATAPACK_MESSAGE_2,
            COMMAND_DATAPACK_ADD_MODRINTH_URLS_DIMENSION_S_DATAPACKIMPORTS_LIST_THEN_RUN_IRIS,
            COMMAND_DEVELOPER_GENHASH_STARTED_CHUNKS,
            COMMAND_DEVELOPER_GENHASH_FAILED_AT_CHUNK,
            COMMAND_DEVELOPER_GENHASH_GLOBAL_CHUNKS_SOLID,
            COMMAND_FIND_NOT_IRIS_WORLD,
            COMMAND_FIND_UNKNOWN_STRUCTURE,
            COMMAND_FIND_RUN_THIS_GAME_TELEPORT_STRUCTURE,
            COMMAND_FIND_LOCATING,
            COMMAND_FIND_RUN_THIS_GAME_TELEPORT_STRUCTURE_2,
            COMMAND_FIND_LOCATING_2,
            COMMAND_FIND_TELEPORTED,
            COMMAND_IRIS_SUCCESSFULLY_REMOVED_WORLD_FOLDER,
            COMMAND_IRIS_SUCCESSFULLY_REMOVED_WORLD_FOLDER_2,
            COMMAND_IRIS_FAILED_REMOVE_WORLD_FOLDER,
            COMMAND_OBJECT_NO_PACKS_WITH_OBJECTS_WERE_FOUND_ON_THIS_SERVER,
            COMMAND_OBJECT_NO_OBJECTS_PLACE_ACROSS_SELECTED_PACK_S,
            COMMAND_OBJECT_OPENING_OBJECT_STUDIO_OBJECTS,
            COMMAND_OBJECT_FAILED_OPEN_OBJECT_STUDIO,
            COMMAND_PACK_YOU_MUST_SPECIFY_PACK_NAME,
            COMMAND_PACK_PACK_NOT_FOUND_UNDER_PACKS,
            COMMAND_PACK_MESSAGE,
            COMMAND_PACK_MORE,
            COMMAND_STRUCTURE_VERIFYING_STRUCTURES_FROM_WITHIN_CHUNKS,
            COMMAND_STUDIO_IMPORTING_VANILLA_CONTENT_INTO,
            COMMAND_STUDIO_IMPORTVANILLA_COMPLETE_OBJECTS_STRUCTURES_WRITTEN_FAILED,
            COMMAND_STUDIO_TREES_OBJECTS_ARE_UNDER_OBJECTS_VANILLA_REFERENCE_THEM_FROM_BIOME,
            COMMAND_STUDIO_NO_OPEN_STUDIO_PROJECTS,
            COMMAND_STUDIO_CLOSING_STUDIO,
            COMMAND_STUDIO_STUDIO_CLOSE_FAILED,
            COMMAND_STUDIO_STUDIO_CLOSE_FAILED_2,
            COMMAND_STUDIO_STUDIO_CLOSED_REMAINING_WORLD_FAMILY_CLEANUP_WAS_QUEUED_STARTUP_FALLBACK,
            COMMAND_STUDIO_STUDIO_CLOSED,
            COMMAND_STUDIO_YOU_MUST_BE_STUDIO_WORLD_TOGGLE_DEBUG_SCOREBOARD,
            COMMAND_STUDIO_STUDIO_DEBUG_SCOREBOARD,
            COMMAND_STUDIO_COULD_NOT_UPDATE_STUDIO_DEBUG_SCOREBOARD_RIGHT_NOW,
            COMMAND_STUDIO_OPENED_INVENTORY,
            COMMAND_STUDIO_GENERATING_DATA,
            COMMAND_STUDIO_DONE,
            COMMAND_STUDIO_MESSAGE,
            COMMAND_S_V_C_YOU_LACK_PERMISSION,
            IRIS_ENGINE_STATUS_MESSAGE,
            IRIS_ENGINE_STATUS_STATUS,
            IRIS_ENGINE_STATUS_SERVICE,
            IRIS_ENGINE_STATUS_METRICS,
            IRIS_ENGINE_STATUS_MAINTENANCE_PERIOD,
            IRIS_ENGINE_STATUS_WORKER_PARALLELISM,
            IRIS_ENGINE_STATUS_ACTIVE_WORLD_TASKS,
            IRIS_ENGINE_STATUS_TECTONIC_PLATES,
            IRIS_ENGINE_STATUS_CONFIGURED_RETENTION,
            IRIS_ENGINE_STATUS_HEAP_USAGE,
            IRIS_ENGINE_STATUS_RESIDENT,
            IRIS_ENGINE_STATUS_QUEUED,
            IRIS_ENGINE_STATUS_AVERAGE_IDLE_DURATION,
            IRIS_ENGINE_STATUS_MAX_IDLE_DURATION,
            IRIS_ENGINE_STATUS_MIN_IDLE_DURATION,
            IRIS_ENGINE_STATUS_CACHES,
            IRIS_ENGINE_STATUS_RESOURCE,
            IRIS_ENGINE_STATUS_2D_STREAM,
            IRIS_ENGINE_STATUS_3D_STREAM,
            IRIS_ENGINE_STATUS_OTHER,
            IRIS_ENGINE_STATUS_OTHER_2,
            IRIS_ENGINE_STATUS_IRIS_WORLDS,
            IRIS_ENGINE_STATUS_LOADED_CHUNKS,
            IRIS_ENGINE_STATUS_MESSAGE_2
    );

    private BukkitCommandMessages() {
    }

    public static List<MessageKey> keys() {
        return KEYS;
    }
}

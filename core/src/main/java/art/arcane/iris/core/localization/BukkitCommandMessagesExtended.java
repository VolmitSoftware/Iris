package art.arcane.iris.core.localization;

import art.arcane.iris.util.common.format.C;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;

public final class BukkitCommandMessagesExtended {
    public static final TextKey COMMAND_DEVELOPER_WORLD_IS_NULL = TextKey.of(
            "iris.bukkit.commanddeveloper.world_is_null",
            C.RED + "World is null."
    );
    public static final TextKey COMMAND_DEVELOPER_TARGET_IRIS_WORLD_BEFORE_READING_MANTLE_DUMP = TextKey.of(
            "iris.bukkit.commanddeveloper.target_iris_world_before_reading_mantle_dump",
            C.RED + "Target an Iris world before reading a mantle dump."
    );
    public static final TextKey COMMAND_DEVELOPER_UPGRADING = TextKey.of(
            "iris.bukkit.commanddeveloper.upgrading",
            C.GREEN + "Upgrading to " + "{value}" + "..."
    );
    public static final TextKey COMMAND_DEVELOPER_DONE_UPGRADING_YOU_CAN_NOW_UPDATE_YOUR_SERVER_VERSION = TextKey.of(
            "iris.bukkit.commanddeveloper.done_upgrading_you_can_now_update_your_server_version",
            C.GREEN + "Done upgrading! You can now update your server version to " + "{value}"
    );
    public static final TextKey COMMAND_DEVELOPER_RADIUS_MUST_BE_0_GREATER = TextKey.of(
            "iris.bukkit.commanddeveloper.radius_must_be_0_greater",
            C.RED + "Radius must be 0 or greater."
    );
    public static final TextKey COMMAND_DEVELOPER_THIS_IS_NOT_IRIS_WORLD = TextKey.of(
            "iris.bukkit.commanddeveloper.this_is_not_iris_world",
            C.RED + "This is not an Iris world."
    );
    public static final TextKey COMMAND_DEVELOPER_ENGINE_ACCESS_THIS_WORLD_IS_NULL = TextKey.of(
            "iris.bukkit.commanddeveloper.engine_access_this_world_is_null",
            C.RED + "The engine access for this world is null."
    );
    public static final TextKey COMMAND_DEVELOPER_DELETE_STARTED_CHUNK_S_AROUND_CLEARING_BLOCKS_AIR = TextKey.of(
            "iris.bukkit.commanddeveloper.delete_started_chunk_s_around_clearing_blocks_air",
            C.GREEN + "Delete started: " + C.GOLD + "{chunks}" + C.GREEN + " chunk(s) around " + C.GOLD + "{centerX}" + "," + "{centerZ}" + C.GREEN + ". Clearing blocks to air."
    );
    public static final TextKey COMMAND_DEVELOPER_RADIUS_MUST_BE_0_GREATER_2 = TextKey.of(
            "iris.bukkit.commanddeveloper.radius_must_be_0_greater_2",
            C.RED + "Radius must be 0 or greater."
    );
    public static final TextKey COMMAND_DEVELOPER_YOU_MUST_BE_IRIS_WORLD_USE_REGEN = TextKey.of(
            "iris.bukkit.commanddeveloper.you_must_be_iris_world_use_regen",
            C.RED + "You must be in an Iris world to use regen."
    );
    public static final TextKey COMMAND_DEVELOPER_ENGINE_ACCESS_THIS_WORLD_IS_NULL_GENERATE_NEARBY_CHUNKS_FIRST = TextKey.of(
            "iris.bukkit.commanddeveloper.engine_access_this_world_is_null_generate_nearby_chunks_first",
            C.RED + "The engine access for this world is null. Generate nearby chunks first."
    );
    public static final TextKey COMMAND_DEVELOPER_REGEN_STARTED_CHUNK_S_AROUND_DELETING_REGENERATING_PLACE = TextKey.of(
            "iris.bukkit.commanddeveloper.regen_started_chunk_s_around_deleting_regenerating_place",
            C.GREEN + "Regen started: " + C.GOLD + "{chunks}" + C.GREEN + " chunk(s) around " + C.GOLD + "{centerX}" + "," + "{centerZ}" + C.GREEN + ". Deleting and regenerating in place."
    );
    public static final TextKey COMMAND_DEVELOPER_RADIUS_MUST_BE_0_GREATER_3 = TextKey.of(
            "iris.bukkit.commanddeveloper.radius_must_be_0_greater_3",
            C.RED + "Radius must be 0 or greater."
    );
    public static final TextKey COMMAND_DEVELOPER_TARGET_MUST_BE_IRIS_WORLD = TextKey.of(
            "iris.bukkit.commanddeveloper.target_must_be_iris_world",
            C.RED + "Target must be an Iris world."
    );
    public static final TextKey COMMAND_DEVELOPER_ENGINE_ACCESS_THIS_WORLD_IS_NULL_2 = TextKey.of(
            "iris.bukkit.commanddeveloper.engine_access_this_world_is_null_2",
            C.RED + "The engine access for this world is null."
    );
    public static final TextKey COMMAND_DEVELOPER_GOLDENHASH_STARTED_CHUNK_S_AROUND_BUFFERS_WORLD_UNTOUCHED = TextKey.of(
            "iris.bukkit.commanddeveloper.goldenhash_started_chunk_s_around_buffers_world_untouched",
            C.GREEN + "GoldenHash started: " + C.GOLD + "{chunks}" + C.GREEN + " chunk(s) around " + C.GOLD + "{centerX}" + "," + "{centerZ}" + C.GREEN + " in buffers (world untouched)."
    );
    public static final TextKey COMMAND_EDIT_PLAYERS_ONLY = TextKey.of(
            "iris.bukkit.commandedit.players_only",
            C.RED + "Players only!"
    );
    public static final TextKey COMMAND_EDIT_NO_STUDIO_WORLD_IS_OPEN = TextKey.of(
            "iris.bukkit.commandedit.no_studio_world_is_open",
            C.RED + "No studio world is open!"
    );
    public static final TextKey COMMAND_EDIT_YOU_MUST_BE_STUDIO_WORLD = TextKey.of(
            "iris.bukkit.commandedit.you_must_be_studio_world",
            C.RED + "You must be in a studio world!"
    );
    public static final TextKey COMMAND_EDIT_CANNOT_OPEN_FILES_HEADLESS_ENVIRONMENTS = TextKey.of(
            "iris.bukkit.commandedit.cannot_open_files_headless_environments",
            C.RED + "Cannot open files in headless environments!"
    );
    public static final TextKey COMMAND_EDIT_DESKTOP_IS_NOT_SUPPORTED_BY_THIS_ENVIRONMENT = TextKey.of(
            "iris.bukkit.commandedit.desktop_is_not_supported_by_this_environment",
            C.RED + "Desktop is not supported by this environment!"
    );
    public static final TextKey COMMAND_EDIT_CANNOT_FIND_FILE_PERHAPS_IT_WAS_NOT_LOADED_DIRECTLY_FROM = TextKey.of(
            "iris.bukkit.commandedit.cannot_find_file_perhaps_it_was_not_loaded_directly_from",
            C.GOLD + "Cannot find the file; Perhaps it was not loaded directly from a file?"
    );
    public static final TextKey COMMAND_EDIT_OPENING_VSCODE = TextKey.of(
            "iris.bukkit.commandedit.opening_vscode",
            C.GREEN + "Opening " + "{value}" + " " + "{value2}" + " in VSCode! "
    );
    public static final TextKey COMMAND_EDIT_CANT_FIND_FILE_REGISTRANT_DOES_NOT_EXIST = TextKey.of(
            "iris.bukkit.commandedit.cant_find_file_registrant_does_not_exist",
            C.RED + "Cant find the file. Or registrant does not exist"
    );
    public static final TextKey COMMAND_EDIT_CANNOT_FIND_FILE_PERHAPS_IT_WAS_NOT_LOADED_DIRECTLY_FROM_2 = TextKey.of(
            "iris.bukkit.commandedit.cannot_find_file_perhaps_it_was_not_loaded_directly_from_2",
            C.GOLD + "Cannot find the file; Perhaps it was not loaded directly from a file?"
    );
    public static final TextKey COMMAND_EDIT_OPENING_VSCODE_2 = TextKey.of(
            "iris.bukkit.commandedit.opening_vscode_2",
            C.GREEN + "Opening " + "{value}" + " " + "{value2}" + " in VSCode! "
    );
    public static final TextKey COMMAND_EDIT_CANT_FIND_FILE_REGISTRANT_DOES_NOT_EXIST_2 = TextKey.of(
            "iris.bukkit.commandedit.cant_find_file_registrant_does_not_exist_2",
            C.RED + "Cant find the file. Or registrant does not exist"
    );
    public static final TextKey COMMAND_EDIT_CANNOT_FIND_FILE_PERHAPS_IT_WAS_NOT_LOADED_DIRECTLY_FROM_3 = TextKey.of(
            "iris.bukkit.commandedit.cannot_find_file_perhaps_it_was_not_loaded_directly_from_3",
            C.GOLD + "Cannot find the file; Perhaps it was not loaded directly from a file?"
    );
    public static final TextKey COMMAND_EDIT_OPENING_VSCODE_3 = TextKey.of(
            "iris.bukkit.commandedit.opening_vscode_3",
            C.GREEN + "Opening " + "{value}" + " " + "{value2}" + " in VSCode! "
    );
    public static final TextKey COMMAND_EDIT_CANT_FIND_FILE_REGISTRANT_DOES_NOT_EXIST_3 = TextKey.of(
            "iris.bukkit.commandedit.cant_find_file_registrant_does_not_exist_3",
            C.RED + "Cant find the file. Or registrant does not exist"
    );
    public static final TextKey COMMAND_FIND_NOT_IRIS_WORLD = TextKey.of(
            "iris.bukkit.commandfind.not_iris_world_5",
            C.GOLD + "Not in an Iris World!"
    );
    public static final TextKey COMMAND_FIND_NOT_IRIS_WORLD_2 = TextKey.of(
            "iris.bukkit.commandfind.not_iris_world_2",
            C.GOLD + "Not in an Iris World!"
    );
    public static final TextKey COMMAND_FIND_NOT_IRIS_WORLD_3 = TextKey.of(
            "iris.bukkit.commandfind.not_iris_world_3",
            C.GOLD + "Not in an Iris World!"
    );
    public static final TextKey COMMAND_FIND_NOT_IRIS_WORLD_4 = TextKey.of(
            "iris.bukkit.commandfind.not_iris_world_4",
            C.GOLD + "Not in an Iris World!"
    );
    public static final TextKey COMMAND_FIND_OBJECT_STUDIO_TELEPORTING = TextKey.of(
            "iris.bukkit.commandfind.object_studio_teleporting",
            C.GREEN + "Object Studio: teleporting to " + "{object}"
    );
    public static final TextKey COMMAND_FIND_IS_NOT_CONFIGURED_ANY_REGION_BIOME_OBJECT_PLACEMENTS = TextKey.of(
            "iris.bukkit.commandfind.is_not_configured_any_region_biome_object_placements",
            C.RED + "{object}" + " is not configured in any region/biome object placements."
    );
    public static final TextKey COMMAND_IRIS_YOU_CANNOT_USE_WORLD_NAME_IRIS_CREATING_WORLDS_AS_IRIS = TextKey.of(
            "iris.bukkit.commandiris.you_cannot_use_world_name_iris_creating_worlds_as_iris",
            C.RED + "You cannot use the world name \"iris\" for creating worlds as Iris uses this directory for studio worlds."
    );
    public static final TextKey COMMAND_IRIS_MAY_WE_SUGGEST_NAME_IRISWORLD_INSTEAD = TextKey.of(
            "iris.bukkit.commandiris.may_we_suggest_name_irisworld_instead",
            C.RED + "May we suggest the name \"IrisWorld\" instead?"
    );
    public static final TextKey COMMAND_IRIS_YOU_CANNOT_USE_WORLD_NAME_BENCHMARK_CREATING_WORLDS_AS_IRIS = TextKey.of(
            "iris.bukkit.commandiris.you_cannot_use_world_name_benchmark_creating_worlds_as_iris",
            C.RED + "You cannot use the world name \"benchmark\" for creating worlds as Iris uses this directory for Benchmarking Packs."
    );
    public static final TextKey COMMAND_IRIS_MAY_WE_SUGGEST_NAME_IRISWORLD_INSTEAD_2 = TextKey.of(
            "iris.bukkit.commandiris.may_we_suggest_name_irisworld_instead_2",
            C.RED + "May we suggest the name \"IrisWorld\" instead?"
    );
    public static final TextKey COMMAND_IRIS_THAT_FOLDER_ALREADY_EXISTS = TextKey.of(
            "iris.bukkit.commandiris.that_folder_already_exists",
            C.RED + "That folder already exists!"
    );
    public static final TextKey COMMAND_IRIS_TRY_ONE_OVERWORLD_VANILLA_FLAT_THEEND = TextKey.of(
            "iris.bukkit.commandiris.try_one_overworld_vanilla_flat_theend",
            C.YELLOW + "Try one of: overworld, vanilla, flat, theend"
    );
    public static final TextKey COMMAND_IRIS_DIMENSION_NOT_FOUND = TextKey.of(
            "iris.bukkit.commandiris.dimension_not_found",
            C.RED + "Could not find dimension " + C.WHITE + "{dimension}" + C.RED + "."
    );
    public static final TextKey COMMAND_IRIS_INSTALL_PACK_AND_RESTART = TextKey.of(
            "iris.bukkit.commandiris.install_pack_and_restart",
            C.YELLOW + "Install it with " + C.AQUA + "{command}" + C.YELLOW + " and restart the server."
    );
    public static final TextKey COMMAND_IRIS_EXCEPTION_RAISED_DURING_CREATION_SEE_CONSOLE_MORE_DETAILS = TextKey.of(
            "iris.bukkit.commandiris.exception_raised_during_creation_see_console_more_details",
            C.RED + "Exception raised during creation. See the console for more details."
    );
    public static final TextKey COMMAND_IRIS_SUCCESSFULLY_CREATED_YOUR_WORLD = TextKey.of(
            "iris.bukkit.commandiris.successfully_created_your_world",
            C.GREEN + "Successfully created your world!"
    );
    public static final TextKey COMMAND_IRIS_SPECIFIED_PLAYER_DOES_NOT_EXIST = TextKey.of(
            "iris.bukkit.commandiris.specified_player_does_not_exist",
            C.RED + "The specified player does not exist."
    );
    public static final TextKey COMMAND_IRIS_TO = TextKey.of(
            "iris.bukkit.commandiris.to",
            C.GREEN + "" + "{value}" + " to " + "{value2}"
    );
    public static final TextKey COMMAND_IRIS_TOTAL_HEIGHT = TextKey.of(
            "iris.bukkit.commandiris.total_height",
            C.GREEN + "Total Height: " + "{value}"
    );
    public static final TextKey COMMAND_IRIS_IRIS_WORLDS = TextKey.of(
            "iris.bukkit.commandiris.iris_worlds",
            C.BLUE + "Iris Worlds: "
    );
    public static final TextKey COMMAND_IRIS_MESSAGE = TextKey.of(
            "iris.bukkit.commandiris.message",
            C.IRIS + "- " + "{value}"
    );
    public static final TextKey COMMAND_IRIS_BUKKIT_WORLDS = TextKey.of(
            "iris.bukkit.commandiris.bukkit_worlds",
            C.GOLD + "Bukkit Worlds: "
    );
    public static final TextKey COMMAND_IRIS_MESSAGE_2 = TextKey.of(
            "iris.bukkit.commandiris.message_2",
            C.GRAY + "- " + "{value}"
    );
    public static final TextKey COMMAND_IRIS_THIS_IS_NOT_IRIS_WORLD_IRIS_WORLDS = TextKey.of(
            "iris.bukkit.commandiris.this_is_not_iris_world_iris_worlds",
            C.RED + "This is not an Iris world. Iris worlds: " + "{value}"
    );
    public static final TextKey COMMAND_IRIS_REMOVING_WORLD = TextKey.of(
            "iris.bukkit.commandiris.removing_world",
            C.GREEN + "Removing world: " + "{value}"
    );
    public static final TextKey COMMAND_IRIS_FAILED_EVACUATE_WORLD = TextKey.of(
            "iris.bukkit.commandiris.failed_evacuate_world",
            C.RED + "Failed to evacuate world: " + "{value}"
    );
    public static final TextKey COMMAND_IRIS_FAILED_UNLOAD_WORLD = TextKey.of(
            "iris.bukkit.commandiris.failed_unload_world",
            C.RED + "Failed to unload world: " + "{value}"
    );
    public static final TextKey COMMAND_IRIS_SUCCESSFULLY_REMOVED_FROM_BUKKIT_YML = TextKey.of(
            "iris.bukkit.commandiris.successfully_removed_from_bukkit_yml",
            C.GREEN + "Successfully removed " + "{value}" + " from bukkit.yml"
    );
    public static final TextKey COMMAND_IRIS_LOOKS_LIKE_WORLD_WAS_ALREADY_REMOVED_FROM_BUKKIT_YML = TextKey.of(
            "iris.bukkit.commandiris.looks_like_world_was_already_removed_from_bukkit_yml",
            C.YELLOW + "Looks like the world was already removed from bukkit.yml"
    );
    public static final TextKey COMMAND_IRIS_FAILED_SAVE_BUKKIT_YML_BECAUSE = TextKey.of(
            "iris.bukkit.commandiris.failed_save_bukkit_yml_because",
            C.RED + "Failed to save bukkit.yml because of " + "{value}"
    );
    public static final TextKey COMMAND_IRIS_SET_DEBUG = TextKey.of(
            "iris.bukkit.commandiris.set_debug",
            C.GREEN + "Set debug to: " + "{to}"
    );
    public static final TextKey COMMAND_IRIS_YOU_MUST_BE_IRIS_WORLD = TextKey.of(
            "iris.bukkit.commandiris.you_must_be_iris_world",
            C.RED + "You must be in an Iris world"
    );
    public static final TextKey COMMAND_IRIS_SENDING_METRICS = TextKey.of(
            "iris.bukkit.commandiris.sending_metrics",
            C.GREEN + "Sending metrics..."
    );
    public static final TextKey COMMAND_IRIS_THIS_IS_NOT_IRIS_WORLD_IRIS_WORLDS_2 = TextKey.of(
            "iris.bukkit.commandiris.this_is_not_iris_world_iris_worlds_2",
            C.RED + "This is not an Iris world. Iris worlds: " + "{value}"
    );
    public static final TextKey COMMAND_IRIS_UNLOADING_WORLD = TextKey.of(
            "iris.bukkit.commandiris.unloading_world",
            C.GREEN + "Unloading world: " + "{value}"
    );
    public static final TextKey COMMAND_IRIS_WORLD_UNLOADED_SUCCESSFULLY = TextKey.of(
            "iris.bukkit.commandiris.world_unloaded_successfully",
            C.GREEN + "World unloaded successfully."
    );
    public static final TextKey COMMAND_IRIS_FAILED_UNLOAD_WORLD_2 = TextKey.of(
            "iris.bukkit.commandiris.failed_unload_world_2",
            C.RED + "Failed to unload the world."
    );
    public static final TextKey COMMAND_IRIS_FAILED_UNLOAD_WORLD_3 = TextKey.of(
            "iris.bukkit.commandiris.failed_unload_world_3",
            C.RED + "Failed to unload the world: " + "{value}"
    );
    public static final TextKey COMMAND_IRIS_DOESNT_EXIST_ON_SERVER = TextKey.of(
            "iris.bukkit.commandiris.doesnt_exist_on_server",
            C.YELLOW + "{logicalWorldName}" + " Doesnt exist on the server."
    );
    public static final TextKey COMMAND_IRIS_GENERATOR = TextKey.of(
            "iris.bukkit.commandiris.generator",
            C.BLUE + "Generator: " + "{dimension}"
    );
    public static final TextKey COMMAND_IRIS_IS_NOT_IRIS_WORLD = TextKey.of(
            "iris.bukkit.commandiris.is_not_iris_world",
            C.GOLD + "{logicalWorldName}" + " is not an iris world."
    );
    public static final TextKey COMMAND_IRIS_COULD_NOT_DETERMINE_IRIS_DIMENSION = TextKey.of(
            "iris.bukkit.commandiris.could_not_determine_iris_dimension",
            C.RED + "Could not determine Iris dimension for " + "{logicalWorldName}" + "."
    );
    public static final TextKey COMMAND_IRIS_LOADING_WORLD = TextKey.of(
            "iris.bukkit.commandiris.loading_world",
            C.GREEN + "Loading world: " + "{logicalWorldName}"
    );
    public static final TextKey COMMAND_IRIS_LOADED_SUCCESSFULLY = TextKey.of(
            "iris.bukkit.commandiris.loaded_successfully",
            C.GREEN + "{logicalWorldName}" + " loaded successfully."
    );
    public static final TextKey COMMAND_IRIS_THIS_IS_NOT_IRIS_WORLD_IRIS_WORLDS_3 = TextKey.of(
            "iris.bukkit.commandiris.this_is_not_iris_world_iris_worlds_3",
            C.RED + "This is not an Iris world. Iris worlds: " + "{value}"
    );
    public static final TextKey COMMAND_IRIS_EVACUATING_WORLD = TextKey.of(
            "iris.bukkit.commandiris.evacuating_world",
            C.GREEN + "Evacuating world" + "{value}"
    );
    public static final TextKey COMMAND_OBJECT_OBJECT_SIZE = TextKey.of(
            "iris.bukkit.commandobject.object_size",
            "Object Size: " + "{value}" + " * " + "{value2}" + " * " + "{value3}" + ""
    );
    public static final TextKey COMMAND_OBJECT_BLOCKS_USED = TextKey.of(
            "iris.bukkit.commandobject.blocks_used",
            "Blocks Used: " + "{value}"
    );
    public static final TextKey COMMAND_OBJECT_BLOCKS_OBJECT = TextKey.of(
            "iris.bukkit.commandobject.blocks_object",
            "== Blocks in object =="
    );
    public static final TextKey COMMAND_OBJECT_OTHER_BLOCK_TYPES = TextKey.of(
            "iris.bukkit.commandobject.other_block_types",
            "  + " + "{value}" + " other block types"
    );
    public static final TextKey COMMAND_OBJECT_CURRENT_OBJECT_SIZE = TextKey.of(
            "iris.bukkit.commandobject.current_object_size",
            "Current Object Size: " + "{value}" + " * " + "{value2}" + " * " + "{value3}"
    );
    public static final TextKey COMMAND_OBJECT_NEW_OBJECT_SIZE = TextKey.of(
            "iris.bukkit.commandobject.new_object_size",
            "New Object Size: " + "{value}" + " * " + "{value2}" + " * " + "{value3}"
    );
    public static final TextKey COMMAND_OBJECT_FAILED_SAVE_OBJECT = TextKey.of(
            "iris.bukkit.commandobject.failed_save_object",
            "Failed to save object " + "{value}" + ": " + "{value2}"
    );
    public static final TextKey COMMAND_OBJECT_NO_OBJECTS_MATCHED = TextKey.of(
            "iris.bukkit.commandobject.no_objects_matched",
            C.RED + "No objects matched: " + "{target}"
    );
    public static final TextKey COMMAND_OBJECT_PLAUSIBILIZE_REACH_QUEUED_OBJECT_S = TextKey.of(
            "iris.bukkit.commandobject.plausibilize_reach_queued_object_s",
            C.IRIS + "Plausibilize [reach=" + "{reach}" + "{value}" + "] queued " + "{value2}" + " object(s)"
    );
    public static final TextKey COMMAND_OBJECT_HOLD_YOUR_WAND = TextKey.of(
            "iris.bukkit.commandobject.hold_your_wand",
            "Hold your wand."
    );
    public static final TextKey COMMAND_OBJECT_NO_AREA_SELECTED = TextKey.of(
            "iris.bukkit.commandobject.no_area_selected",
            "No area selected."
    );
    public static final TextKey COMMAND_OBJECT_READY_YOUR_WAND = TextKey.of(
            "iris.bukkit.commandobject.ready_your_wand",
            "Ready your Wand."
    );
    public static final TextKey COMMAND_OBJECT_READY_YOUR_WAND_2 = TextKey.of(
            "iris.bukkit.commandobject.ready_your_wand_2",
            "Ready your Wand."
    );
    public static final TextKey COMMAND_OBJECT_INDICATED_SCALE_EXCEEDS_MAXIMUM_DOWNSCALED_MAXIMUM = TextKey.of(
            "iris.bukkit.commandobject.indicated_scale_exceeds_maximum_downscaled_maximum",
            C.YELLOW + "Indicated scale exceeds maximum. Downscaled to maximum: " + "{maxScale}"
    );
    public static final TextKey COMMAND_OBJECT_UPDATED_WAND_OBJECTS_IOB = TextKey.of(
            "iris.bukkit.commandobject.updated_wand_objects_iob",
            "Updated wand for " + "objects/" + "{value}" + ".iob "
    );
    public static final TextKey COMMAND_OBJECT_GIVEN_NEW_WAND_OBJECTS_IOB = TextKey.of(
            "iris.bukkit.commandobject.given_new_wand_objects_iob",
            "Given new wand for " + "objects/" + "{value}" + ".iob "
    );
    public static final TextKey COMMAND_OBJECT_UPDATED_WAND_OBJECTS_IOB_2 = TextKey.of(
            "iris.bukkit.commandobject.updated_wand_objects_iob_2",
            "Updated wand for " + "objects/" + "{value}" + ".iob "
    );
    public static final TextKey COMMAND_OBJECT_PLACED = TextKey.of(
            "iris.bukkit.commandobject.placed",
            C.IRIS + "Placed " + "{object}"
    );
    public static final TextKey COMMAND_OBJECT_YOU_NEED_HOLD_YOUR_WAND = TextKey.of(
            "iris.bukkit.commandobject.you_need_hold_your_wand",
            C.YELLOW + "You need to hold your wand!"
    );
    public static final TextKey COMMAND_OBJECT_FILE_ALREADY_EXISTS_SET_OVERWRITE_TRUE_OVERWRITE_IT = TextKey.of(
            "iris.bukkit.commandobject.file_already_exists_set_overwrite_true_overwrite_it",
            C.RED + "File already exists. Set overwrite=true to overwrite it."
    );
    public static final TextKey COMMAND_OBJECT_FAILED_SAVE_OBJECT_BECAUSE_IOEXCEPTION = TextKey.of(
            "iris.bukkit.commandobject.failed_save_object_because_ioexception",
            C.RED + "Failed to save object because of an IOException: " + "{value}"
    );
    public static final TextKey COMMAND_OBJECT_SUCCESSFULLY_OBJECT_SAVED_OBJECTS = TextKey.of(
            "iris.bukkit.commandobject.successfully_object_saved_objects",
            C.GREEN + "Successfully object to saved: " + "{value}" + "/objects/" + "{name}"
    );
    public static final TextKey COMMAND_OBJECT_HOLD_YOUR_WAND_2 = TextKey.of(
            "iris.bukkit.commandobject.hold_your_wand_2",
            "Hold your wand."
    );
    public static final TextKey COMMAND_OBJECT_NO_AREA_SELECTED_2 = TextKey.of(
            "iris.bukkit.commandobject.no_area_selected_2",
            "No area selected."
    );
    public static final TextKey COMMAND_OBJECT_REVERTED_PASTES = TextKey.of(
            "iris.bukkit.commandobject.reverted_pastes",
            C.BLUE + "Reverted " + "{actualReverts}" + C.BLUE + " pastes!"
    );
    public static final TextKey COMMAND_OBJECT_YOU_CAN_T_GET_WORLDEDIT_SELECTION_WITHOUT_WORLDEDIT_YOU_KNOW = TextKey.of(
            "iris.bukkit.commandobject.you_can_t_get_worldedit_selection_without_worldedit_you_know",
            C.RED + "You can't get a WorldEdit selection without WorldEdit, you know."
    );
    public static final TextKey COMMAND_OBJECT_YOU_DON_T_HAVE_WORLDEDIT_SELECTION_THIS_WORLD = TextKey.of(
            "iris.bukkit.commandobject.you_don_t_have_worldedit_selection_this_world",
            C.RED + "You don't have a WorldEdit selection in this world."
    );
    public static final TextKey COMMAND_OBJECT_FRESH_WAND_WITH_YOUR_CURRENT_WORLDEDIT_SELECTION_ON_IT = TextKey.of(
            "iris.bukkit.commandobject.fresh_wand_with_your_current_worldedit_selection_on_it",
            C.GREEN + "A fresh wand with your current WorldEdit selection on it!"
    );
    public static final TextKey COMMAND_OBJECT_POOF_GOOD_LUCK_BUILDING = TextKey.of(
            "iris.bukkit.commandobject.poof_good_luck_building",
            C.GREEN + "Poof! Good luck building!"
    );
    public static final TextKey COMMAND_OBJECT_HOLD_YOUR_WAND_3 = TextKey.of(
            "iris.bukkit.commandobject.hold_your_wand_3",
            C.YELLOW + "Hold your wand!"
    );
    public static final TextKey COMMAND_OBJECT_NO_AREA_SELECTED_3 = TextKey.of(
            "iris.bukkit.commandobject.no_area_selected_3",
            "No area selected."
    );
    public static final TextKey COMMAND_OBJECT_AUTO_SELECT_COMPLETE = TextKey.of(
            "iris.bukkit.commandobject.auto_select_complete",
            C.GREEN + "Auto-select complete!"
    );
    public static final TextKey COMMAND_OBJECT_HOLD_YOUR_WAND_4 = TextKey.of(
            "iris.bukkit.commandobject.hold_your_wand_4",
            C.YELLOW + "Hold your wand!"
    );
    public static final TextKey COMMAND_OBJECT_NO_AREA_SELECTED_4 = TextKey.of(
            "iris.bukkit.commandobject.no_area_selected_4",
            "No area selected."
    );
    public static final TextKey COMMAND_OBJECT_AUTO_SELECT_COMPLETE_2 = TextKey.of(
            "iris.bukkit.commandobject.auto_select_complete_2",
            C.GREEN + "Auto-select complete!"
    );
    public static final TextKey COMMAND_PREGEN_PREGEN_RADIUS_MUST_BE_GREATER_THAN_ZERO_BLOCKS = TextKey.of(
            "iris.bukkit.commandpregen.pregen_radius_must_be_greater_than_zero_blocks",
            C.RED + "Pregen radius must be greater than zero blocks."
    );
    public static final TextKey COMMAND_STUDIO_REGIONS_RADIUS_OUT_OF_RANGE = TextKey.of(
            "iris.bukkit.commandstudio.regions_radius_out_of_range",
            C.RED + "Radius must be between 1 and 2048 chunks."
    );
    public static final TextKey COMMAND_STUDIO_REGIONS_SCAN_FAILED = TextKey.of(
            "iris.bukkit.commandstudio.regions_scan_failed",
            C.RED + "Region scan failed: {value}"
    );
    public static final TextKey COMMAND_PREGEN_STRICT_SERIAL_PREGENERATION_REQUIRES_PAPER_PAPER_COMPATIBLE_SERVER = TextKey.of(
            "iris.bukkit.commandpregen.strict_serial_pregeneration_requires_paper_paper_compatible_server",
            C.RED + "Strict serial pregeneration requires Paper or a Paper-compatible server."
    );
    public static final TextKey COMMAND_PREGEN_ENGINE_ACCESS_THIS_WORLD_IS_NULL = TextKey.of(
            "iris.bukkit.commandpregen.engine_access_this_world_is_null",
            C.RED + "The engine access for this world is null!"
    );
    public static final TextKey COMMAND_PREGEN_PLEASE_MAKE_SURE_WORLD_IS_LOADED_ENGINE_IS_INITIALIZED_GENERATE = TextKey.of(
            "iris.bukkit.commandpregen.please_make_sure_world_is_loaded_engine_is_initialized_generate",
            C.RED + "Please make sure the world is loaded & the engine is initialized. Generate a new chunk, for example."
    );
    public static final TextKey COMMAND_PREGEN_FAILED_START_PREGENERATION_SEE_CONSOLE_DETAILS = TextKey.of(
            "iris.bukkit.commandpregen.failed_start_pregeneration_see_console_details",
            C.RED + "Failed to start pregeneration. See console for details."
    );
    public static final TextKey COMMAND_PREGEN_NO_ACTIVE_PREGENERATION_TASKS_STOP = TextKey.of(
            "iris.bukkit.commandpregen.no_active_pregeneration_tasks_stop",
            C.YELLOW + "No active pregeneration tasks to stop"
    );
    public static final TextKey COMMAND_PREGEN_PAUSED_UNPAUSED_PREGENERATION_TASK_NOW = TextKey.of(
            "iris.bukkit.commandpregen.paused_unpaused_pregeneration_task_now",
            C.GREEN + "Paused/unpaused pregeneration task, now: " + "{value}" + "."
    );
    public static final TextKey COMMAND_PREGEN_NO_ACTIVE_PREGENERATION_TASKS_PAUSE_UNPAUSE = TextKey.of(
            "iris.bukkit.commandpregen.no_active_pregeneration_tasks_pause_unpause",
            C.YELLOW + "No active pregeneration tasks to pause/unpause."
    );
    public static final TextKey COMMAND_PREGEN_NO_ACTIVE_PREGENERATION_TASK = TextKey.of(
            "iris.bukkit.commandpregen.no_active_pregeneration_task",
            C.YELLOW + "No active pregeneration task."
    );
    public static final TextKey COMMAND_PREGEN_PREGEN = TextKey.of(
            "iris.bukkit.commandpregen.pregen",
            C.GREEN + "Pregen " + C.GOLD + "{world}" + C.GREEN + ": " + C.GOLD + "{value}" + "/" + "{value2}" + C.GREEN + " (" + C.GOLD + "{value3}" + "%" + C.GREEN + ")" + "{value4}"
    );
    public static final TextKey COMMAND_PREGEN_SPEED_S_ETA_ELAPSED_METHOD = TextKey.of(
            "iris.bukkit.commandpregen.speed_s_eta_elapsed_method",
            C.GREEN + "Rates: " + C.GOLD + "overall {overall}/s, 10s {tenSecond}/s, 30s {thirtySecond}/s, 60s {sixtySecond}/s"
                    + C.GREEN + " ETA: " + C.GOLD + "{eta}" + C.GREEN + " Elapsed: " + C.GOLD + "{elapsed}"
                    + C.GREEN + " Method: " + C.GOLD + "{method}" + "{failures}"
    );
    public static final TextKey COMMAND_STRUCTURE_COULD_NOT_RESOLVE_PACK_DIMENSION = TextKey.of(
            "iris.bukkit.commandstructure.could_not_resolve_pack_dimension",
            C.RED + "Could not resolve the pack for dimension " + "{value}"
    );
    public static final TextKey COMMAND_STRUCTURE_WROTE_STRUCTURE_INDEX = TextKey.of(
            "iris.bukkit.commandstructure.wrote_structure_index",
            C.GREEN + "Wrote structure index: " + C.WHITE + "{value}"
    );
    public static final TextKey COMMAND_STRUCTURE_COULD_NOT_RESOLVE_PACK_DIMENSION_2 = TextKey.of(
            "iris.bukkit.commandstructure.could_not_resolve_pack_dimension_2",
            C.RED + "Could not resolve the pack for dimension " + "{value}"
    );
    public static final TextKey COMMAND_STRUCTURE_IMPORTING_ALL_VANILLA_DATAPACK_STRUCTURES_INTO_OVERWRITE = TextKey.of(
            "iris.bukkit.commandstructure.importing_all_vanilla_datapack_structures_into_overwrite",
            C.GREEN + "Importing all vanilla & datapack structures into " + C.WHITE + "{value}" + C.GREEN + " (overwrite)..."
    );
    public static final TextKey COMMAND_STRUCTURE_IMPORT_COMPLETE_STRUCTURES_OBJECTS_WRITTEN_FAILED = TextKey.of(
            "iris.bukkit.commandstructure.import_complete_structures_objects_written_failed",
            C.GREEN + "Import complete: " + C.WHITE + "{imported}" + C.GREEN + " structures/objects written, " + C.WHITE + "{failed}" + C.GREEN + " failed."
    );
    public static final TextKey COMMAND_STRUCTURE_REFERENCE_THEM_FROM_BIOME_REGION_DIMENSION_STRUCTURES_LIST_RUN_IRIS = TextKey.of(
            "iris.bukkit.commandstructure.reference_them_from_biome_region_dimension_structures_list_run_iris",
            C.GRAY + "Reference them from a biome/region/dimension 'structures' list, or run /iris structure list " + "{value}" + " to refresh the index. Regenerate chunks for changes to take effect."
    );
    public static final TextKey COMMAND_STRUCTURE_COULD_NOT_RESOLVE_PACK_DIMENSION_3 = TextKey.of(
            "iris.bukkit.commandstructure.could_not_resolve_pack_dimension_3",
            C.RED + "Could not resolve the pack for dimension " + "{value}"
    );
    public static final TextKey COMMAND_STRUCTURE_CAPTURED_STRUCTURES_PLACE_THEM_FROM_STRUCTURES_LIST_REGENERATE_CHUNKS_DELETE = TextKey.of(
            "iris.bukkit.commandstructure.captured_structures_place_them_from_structures_list_regenerate_chunks_delete",
            C.GRAY + "Captured " + "{value}" + " structures. Place them from a 'structures' list and regenerate chunks. Delete a structures/*.json to re-capture it."
    );
    public static final TextKey COMMAND_STRUCTURE_NO_LOADED_IRIS_WORLD_FOUND_JOIN_CREATE_ONE_FIRST_SEARCH = TextKey.of(
            "iris.bukkit.commandstructure.no_loaded_iris_world_found_join_create_one_first_search",
            C.RED + "No loaded Iris world found for " + "{value}" + ". Join or create one first (the search runs against a live world)."
    );
    public static final TextKey COMMAND_STRUCTURE_SELECTED_IRIS_WORLD_HAS_NO_ACTIVE_GENERATOR_ENGINE = TextKey.of(
            "iris.bukkit.commandstructure.selected_iris_world_has_no_active_generator_engine",
            C.RED + "The selected Iris world has no active generator engine."
    );
    public static final TextKey COMMAND_STRUCTURE_COULD_NOT_RESOLVE_PACK_DIMENSION_4 = TextKey.of(
            "iris.bukkit.commandstructure.could_not_resolve_pack_dimension_4",
            C.RED + "Could not resolve the pack for dimension " + "{value}"
    );
    public static final TextKey COMMAND_STRUCTURE_NO_IRIS_STRUCTURE_THIS_PACK = TextKey.of(
            "iris.bukkit.commandstructure.no_iris_structure_this_pack",
            C.RED + "No iris structure '" + "{structure}" + "' in this pack"
    );
    public static final TextKey COMMAND_STRUCTURE_STRUCTURE_ASSEMBLED_0_PIECES_CHECK_STARTPOOL = TextKey.of(
            "iris.bukkit.commandstructure.structure_assembled_0_pieces_check_startpool",
            C.RED + "Structure '" + "{structure}" + "' assembled 0 pieces (check startPool '" + "{value}" + "')"
    );
    public static final TextKey COMMAND_STRUCTURE_STRUCTURE_PIECES_FOOTPRINT_X_BLOCKS_SAMPLE_SEED_1234 = TextKey.of(
            "iris.bukkit.commandstructure.structure_pieces_footprint_x_blocks_sample_seed_1234",
            C.GREEN + "Structure '" + "{structure}" + "': " + C.WHITE + "{value}" + C.GREEN + " pieces, footprint " + C.WHITE + "{value2}" + "x" + "{value3}" + C.GREEN + " blocks (sample seed 1234)"
    );
    public static final TextKey COMMAND_STRUCTURE_COULD_NOT_RESOLVE_PACK_DIMENSION_5 = TextKey.of(
            "iris.bukkit.commandstructure.could_not_resolve_pack_dimension_5",
            C.RED + "Could not resolve the pack for dimension " + "{value}"
    );
    public static final TextKey COMMAND_STRUCTURE_NO_IRIS_STRUCTURE_THIS_PACK_2 = TextKey.of(
            "iris.bukkit.commandstructure.no_iris_structure_this_pack_2",
            C.RED + "No iris structure '" + "{structure}" + "' in this pack"
    );
    public static final TextKey COMMAND_STRUCTURE_STRUCTURE_ASSEMBLED_0_PIECES = TextKey.of(
            "iris.bukkit.commandstructure.structure_assembled_0_pieces",
            C.RED + "Structure '" + "{structure}" + "' assembled 0 pieces"
    );
    public static final TextKey COMMAND_STRUCTURE_PLACED_PIECES_AT_YOUR_LOCATION = TextKey.of(
            "iris.bukkit.commandstructure.placed_pieces_at_your_location",
            C.GREEN + "Placed '" + "{structure}" + "' (" + "{value}" + " pieces, "
                    + "{value2}" + " block changes) at your location."
    );
    public static final TextKey COMMAND_STRUCTURE_PLACEMENT_CHANGED_NO_BLOCKS = TextKey.of(
            "iris.bukkit.commandstructure.placement_changed_no_blocks",
            C.RED + "Structure '" + "{structure}" + "' assembled " + "{value}"
                    + " pieces but changed 0 blocks at your location. Check that the selected variants contain "
                    + "non-air blocks and that the placement is above the world's minimum height."
    );
    public static final TextKey COMMAND_STUDIO_OPENING_STUDIO_PACK_SEED = TextKey.of(
            "iris.bukkit.commandstudio.opening_studio_pack_seed",
            C.GREEN + "Opening studio for the \"" + "{value}" + "\" pack (seed: " + "{seed}" + ")"
    );
    public static final TextKey COMMAND_STUDIO_PROVIDE_DIMENSION_PACK_IRIS_STD_IMPORTVANILLA_PACK_DIMENSION = TextKey.of(
            "iris.bukkit.commandstudio.provide_dimension_pack_iris_std_importvanilla_pack_dimension",
            C.RED + "Provide a dimension pack: /iris std importvanilla pack=<dimension>"
    );
    public static final TextKey COMMAND_STUDIO_COULD_NOT_RESOLVE_PACK_DIMENSION = TextKey.of(
            "iris.bukkit.commandstudio.could_not_resolve_pack_dimension",
            C.RED + "Could not resolve the pack for dimension " + "{value}"
    );
    public static final TextKey COMMAND_STUDIO_OPENING_VSCODE_PACK = TextKey.of(
            "iris.bukkit.commandstudio.opening_vscode_pack",
            C.GREEN + "Opening VSCode for the \"" + "{value}" + "\" pack"
    );
    public static final TextKey COMMAND_STUDIO_PACK_HAS_VERSION = TextKey.of(
            "iris.bukkit.commandstudio.pack_has_version",
            C.GREEN + "The \"" + "{value}" + "\" pack has version: " + "{value2}"
    );
    public static final TextKey COMMAND_STUDIO_OPENING_NOISE_EXPLORER = TextKey.of(
            "iris.bukkit.commandstudio.opening_noise_explorer",
            C.GREEN + "Opening Noise Explorer!"
    );
    public static final TextKey COMMAND_STUDIO_OPENING_IMAGE_MAP_STUDIO = TextKey.of(
            "iris.bukkit.commandstudio.opening_image_map_studio",
            C.GREEN + "Opening the Image Map Studio."
    );
    public static final TextKey COMMAND_STUDIO_CANNOT_ADD_ITEMS_VIRTUAL_INVENTORY_BECAUSE = TextKey.of(
            "iris.bukkit.commandstudio.cannot_add_items_virtual_inventory_because",
            C.RED + "Cannot add items to virtual inventory because of: " + "{value}"
    );
    public static final TextKey COMMAND_STUDIO_OPENING_INVENTORY_NOW = TextKey.of(
            "iris.bukkit.commandstudio.opening_inventory_now",
            C.GREEN + "Opening inventory now!"
    );
    public static final TextKey COMMAND_STUDIO_ONLY_WORKS_IRIS_WORLD = TextKey.of(
            "iris.bukkit.commandstudio.only_works_iris_world",
            C.RED + "Only works in an Iris world!"
    );
    public static final TextKey COMMAND_STUDIO_YOU_NEED_BE_SPECIFY_IRIS_GENERATED_WORLD = TextKey.of(
            "iris.bukkit.commandstudio.you_need_be_specify_iris_generated_world",
            C.RED + "You need to be in or specify an Iris-generated world!"
    );
    public static final TextKey COMMAND_STUDIO_OPENING_MAP = TextKey.of(
            "iris.bukkit.commandstudio.opening_map",
            C.GREEN + "Opening map!"
    );
    public static final TextKey COMMAND_STUDIO_CALCULATING_PERFORMANCE_METRICS_NOISE_GENERATORS = TextKey.of(
            "iris.bukkit.commandstudio.calculating_performance_metrics_noise_generators",
            "Calculating Performance Metrics for Noise generators"
    );
    public static final TextKey COMMAND_STUDIO_CALCULATING_INTERPOLATOR_TIMINGS = TextKey.of(
            "iris.bukkit.commandstudio.calculating_interpolator_timings",
            "Calculating Interpolator Timings..."
    );
    public static final TextKey COMMAND_STUDIO_PROCESSING_GENERATOR_SCORES = TextKey.of(
            "iris.bukkit.commandstudio.processing_generator_scores",
            "Processing Generator Scores: "
    );
    public static final TextKey COMMAND_STUDIO_SCORE = TextKey.of(
            "iris.bukkit.commandstudio.score",
            "Score: " + "{value}"
    );
    public static final TextKey COMMAND_STUDIO_DONE = TextKey.of(
            "iris.bukkit.commandstudio.done_with_result",
            C.GREEN + "Done! " + "{value}"
    );
    public static final TextKey COMMAND_STUDIO_YOU_HAVE_BE_IRIS_WORLD_SPAWN_ENTITIES_PROPERLY_TRYING_SPAWN = TextKey.of(
            "iris.bukkit.commandstudio.you_have_be_iris_world_spawn_entities_properly_trying_spawn",
            C.RED + "You have to be in an Iris world to spawn entities properly. Trying to spawn the best we can do."
    );
    public static final TextKey COMMAND_STUDIO_NO_STUDIO_WORLD_IS_OPEN = TextKey.of(
            "iris.bukkit.commandstudio.no_studio_world_is_open",
            C.RED + "No studio world is open!"
    );
    public static final TextKey COMMAND_STUDIO_YOU_ARE_ALREADY_STUDIO_WORLD = TextKey.of(
            "iris.bukkit.commandstudio.you_are_already_studio_world",
            C.RED + "You are already in a studio world!"
    );
    public static final TextKey COMMAND_STUDIO_SENDING_YOU_STUDIO_WORLD = TextKey.of(
            "iris.bukkit.commandstudio.sending_you_studio_world",
            C.GREEN + "Sending you to the studio world!"
    );
    public static final TextKey COMMAND_STUDIO_UPDATING_CODE_WORKSPACE = TextKey.of(
            "iris.bukkit.commandstudio.updating_code_workspace",
            C.GOLD + "Updating Code Workspace for " + "{value}" + "..."
    );
    public static final TextKey COMMAND_STUDIO_UPDATED_CODE_WORKSPACE = TextKey.of(
            "iris.bukkit.commandstudio.updated_code_workspace",
            C.GREEN + "Updated Code Workspace for " + "{value}"
    );
    public static final TextKey COMMAND_STUDIO_INVALID_PROJECT_TRY_DELETING_CODE_WORKSPACE_FILE_TRY_AGAIN = TextKey.of(
            "iris.bukkit.commandstudio.invalid_project_try_deleting_code_workspace_file_try_again",
            C.RED + "Invalid project: " + "{value}" + ". Try deleting the code-workspace file and try again."
    );
    public static final TextKey COMMAND_STUDIO_YOU_MUST_BE_IRIS_WORLD = TextKey.of(
            "iris.bukkit.commandstudio.you_must_be_iris_world",
            C.RED + "You must be in an Iris world"
    );
    public static final TextKey COMMAND_STUDIO_YOU_MUST_BE_IRIS_WORLD_2 = TextKey.of(
            "iris.bukkit.commandstudio.you_must_be_iris_world_2",
            "You must be in an iris world."
    );
    public static final TextKey COMMAND_STUDIO_CAPTURING_IGENDATA_FROM_NEARBY_CHUNKS = TextKey.of(
            "iris.bukkit.commandstudio.capturing_igendata_from_nearby_chunks",
            "Capturing IGenData from " + "{value}" + " nearby chunks."
    );
    public static final TextKey COMMAND_STUDIO_REPORTED = TextKey.of(
            "iris.bukkit.commandstudio.reported",
            "Reported to: " + "{value}"
    );
    public static final TextKey COMMAND_STUDIO_YOU_MUST_HAVE_SERVER_LAUNCHED_GUIS_ENABLED_SETTINGS = TextKey.of(
            "iris.bukkit.commandstudio.you_must_have_server_launched_guis_enabled_settings",
            C.RED + "You must have server launched GUIs enabled in the settings!"
    );
    public static final TextKey COMMAND_STUDIO_PLAYERS_ONLY = TextKey.of(
            "iris.bukkit.commandstudio.players_only",
            C.RED + "Players only!"
    );
    public static final TextKey COMMAND_STUDIO_NO_STUDIO_WORLD_IS_OPEN_2 = TextKey.of(
            "iris.bukkit.commandstudio.no_studio_world_is_open_2",
            C.RED + "No studio world is open!"
    );
    public static final TextKey COMMAND_STUDIO_YOU_MUST_BE_STUDIO_WORLD = TextKey.of(
            "iris.bukkit.commandstudio.you_must_be_studio_world",
            C.RED + "You must be in a studio world!"
    );
    public static final TextKey COMMAND_WHAT_MATERIAL = TextKey.of(
            "iris.bukkit.commandwhat.material",
            "Material: " + C.GREEN + "{value}"
    );
    public static final TextKey COMMAND_WHAT_FULL = TextKey.of(
            "iris.bukkit.commandwhat.full",
            "Full: " + C.WHITE + "{value}"
    );
    public static final TextKey COMMAND_WHAT_PLEASE_HOLD_BLOCK_ITEM = TextKey.of(
            "iris.bukkit.commandwhat.please_hold_block_item",
            "Please hold a block/item"
    );
    public static final TextKey COMMAND_WHAT_MATERIAL_2 = TextKey.of(
            "iris.bukkit.commandwhat.material_2",
            "Material: " + C.GREEN + "{value}"
    );
    public static final TextKey COMMAND_WHAT_PLEASE_HOLD_BLOCK_ITEM_2 = TextKey.of(
            "iris.bukkit.commandwhat.please_hold_block_item_2",
            "Please hold a block/item"
    );
    public static final TextKey COMMAND_WHAT_IBIOME = TextKey.of(
            "iris.bukkit.commandwhat.ibiome",
            "IBiome: " + "{value}" + " (" + "{value2}" + ")"
    );
    public static final TextKey COMMAND_WHAT_NON_IRIS_BIOME = TextKey.of(
            "iris.bukkit.commandwhat.non_iris_biome",
            "Non-Iris Biome: " + "{value}"
    );
    public static final TextKey COMMAND_WHAT_DATA_PACK_BIOME_ID = TextKey.of(
            "iris.bukkit.commandwhat.data_pack_biome_id",
            "Data Pack Biome: " + "{value}" + " (ID: " + "{value2}" + ")"
    );
    public static final TextKey COMMAND_WHAT_IREGION = TextKey.of(
            "iris.bukkit.commandwhat.iregion",
            "IRegion: " + "{value}" + " (" + "{value2}" + ")"
    );
    public static final TextKey COMMAND_WHAT_IRIS_WORLDS_ONLY = TextKey.of(
            "iris.bukkit.commandwhat.iris_worlds_only",
            C.IRIS + "Iris worlds only."
    );
    public static final TextKey COMMAND_WHAT_PLEASE_LOOK_AT_ANY_BLOCK_NOT_AT_SKY = TextKey.of(
            "iris.bukkit.commandwhat.please_look_at_any_block_not_at_sky",
            "Please look at any block, not at the sky"
    );
    public static final TextKey COMMAND_WHAT_MATERIAL_3 = TextKey.of(
            "iris.bukkit.commandwhat.material_3",
            "Material: " + C.GREEN + "{value}"
    );
    public static final TextKey COMMAND_WHAT_FULL_2 = TextKey.of(
            "iris.bukkit.commandwhat.full_2",
            "Full: " + C.WHITE + "{value}"
    );
    public static final TextKey COMMAND_WHAT_STORAGE_BLOCK_LOOT_CAPABLE = TextKey.of(
            "iris.bukkit.commandwhat.storage_block_loot_capable",
            C.YELLOW + "* Storage Block (Loot Capable)"
    );
    public static final TextKey COMMAND_WHAT_LIT_BLOCK_LIGHT_CAPABLE = TextKey.of(
            "iris.bukkit.commandwhat.lit_block_light_capable",
            C.YELLOW + "* Lit Block (Light Capable)"
    );
    public static final TextKey COMMAND_WHAT_FOLIAGE_BLOCK = TextKey.of(
            "iris.bukkit.commandwhat.foliage_block",
            C.YELLOW + "* Foliage Block"
    );
    public static final TextKey COMMAND_WHAT_DECORANT_BLOCK = TextKey.of(
            "iris.bukkit.commandwhat.decorant_block",
            C.YELLOW + "* Decorant Block"
    );
    public static final TextKey COMMAND_WHAT_FLUID_BLOCK = TextKey.of(
            "iris.bukkit.commandwhat.fluid_block",
            C.YELLOW + "* Fluid Block"
    );
    public static final TextKey COMMAND_WHAT_PLANTABLE_FOLIAGE_BLOCK = TextKey.of(
            "iris.bukkit.commandwhat.plantable_foliage_block",
            C.YELLOW + "* Plantable Foliage Block"
    );
    public static final TextKey COMMAND_WHAT_SOLID_BLOCK = TextKey.of(
            "iris.bukkit.commandwhat.solid_block",
            C.YELLOW + "* Solid Block"
    );
    public static final TextKey COMMAND_WHAT_FOUND_NEARBY_MARKERS = TextKey.of(
            "iris.bukkit.commandwhat.found_nearby_markers",
            "Found " + "{value}" + " Nearby Markers (" + "{marker}" + ")"
    );
    public static final TextKey COMMAND_WHAT_IRIS_WORLDS_ONLY_2 = TextKey.of(
            "iris.bukkit.commandwhat.iris_worlds_only_2",
            C.IRIS + "Iris worlds only."
    );

    private static final List<MessageKey> KEYS = List.of(
            COMMAND_DEVELOPER_WORLD_IS_NULL,
            COMMAND_DEVELOPER_TARGET_IRIS_WORLD_BEFORE_READING_MANTLE_DUMP,
            COMMAND_DEVELOPER_UPGRADING,
            COMMAND_DEVELOPER_DONE_UPGRADING_YOU_CAN_NOW_UPDATE_YOUR_SERVER_VERSION,
            COMMAND_DEVELOPER_RADIUS_MUST_BE_0_GREATER,
            COMMAND_DEVELOPER_THIS_IS_NOT_IRIS_WORLD,
            COMMAND_DEVELOPER_ENGINE_ACCESS_THIS_WORLD_IS_NULL,
            COMMAND_DEVELOPER_DELETE_STARTED_CHUNK_S_AROUND_CLEARING_BLOCKS_AIR,
            COMMAND_DEVELOPER_RADIUS_MUST_BE_0_GREATER_2,
            COMMAND_DEVELOPER_YOU_MUST_BE_IRIS_WORLD_USE_REGEN,
            COMMAND_DEVELOPER_ENGINE_ACCESS_THIS_WORLD_IS_NULL_GENERATE_NEARBY_CHUNKS_FIRST,
            COMMAND_DEVELOPER_REGEN_STARTED_CHUNK_S_AROUND_DELETING_REGENERATING_PLACE,
            COMMAND_DEVELOPER_RADIUS_MUST_BE_0_GREATER_3,
            COMMAND_DEVELOPER_TARGET_MUST_BE_IRIS_WORLD,
            COMMAND_DEVELOPER_ENGINE_ACCESS_THIS_WORLD_IS_NULL_2,
            COMMAND_DEVELOPER_GOLDENHASH_STARTED_CHUNK_S_AROUND_BUFFERS_WORLD_UNTOUCHED,
            COMMAND_EDIT_PLAYERS_ONLY,
            COMMAND_EDIT_NO_STUDIO_WORLD_IS_OPEN,
            COMMAND_EDIT_YOU_MUST_BE_STUDIO_WORLD,
            COMMAND_EDIT_CANNOT_OPEN_FILES_HEADLESS_ENVIRONMENTS,
            COMMAND_EDIT_DESKTOP_IS_NOT_SUPPORTED_BY_THIS_ENVIRONMENT,
            COMMAND_EDIT_CANNOT_FIND_FILE_PERHAPS_IT_WAS_NOT_LOADED_DIRECTLY_FROM,
            COMMAND_EDIT_OPENING_VSCODE,
            COMMAND_EDIT_CANT_FIND_FILE_REGISTRANT_DOES_NOT_EXIST,
            COMMAND_EDIT_CANNOT_FIND_FILE_PERHAPS_IT_WAS_NOT_LOADED_DIRECTLY_FROM_2,
            COMMAND_EDIT_OPENING_VSCODE_2,
            COMMAND_EDIT_CANT_FIND_FILE_REGISTRANT_DOES_NOT_EXIST_2,
            COMMAND_EDIT_CANNOT_FIND_FILE_PERHAPS_IT_WAS_NOT_LOADED_DIRECTLY_FROM_3,
            COMMAND_EDIT_OPENING_VSCODE_3,
            COMMAND_EDIT_CANT_FIND_FILE_REGISTRANT_DOES_NOT_EXIST_3,
            COMMAND_FIND_NOT_IRIS_WORLD,
            COMMAND_FIND_NOT_IRIS_WORLD_2,
            COMMAND_FIND_NOT_IRIS_WORLD_3,
            COMMAND_FIND_NOT_IRIS_WORLD_4,
            COMMAND_FIND_OBJECT_STUDIO_TELEPORTING,
            COMMAND_FIND_IS_NOT_CONFIGURED_ANY_REGION_BIOME_OBJECT_PLACEMENTS,
            COMMAND_IRIS_YOU_CANNOT_USE_WORLD_NAME_IRIS_CREATING_WORLDS_AS_IRIS,
            COMMAND_IRIS_MAY_WE_SUGGEST_NAME_IRISWORLD_INSTEAD,
            COMMAND_IRIS_YOU_CANNOT_USE_WORLD_NAME_BENCHMARK_CREATING_WORLDS_AS_IRIS,
            COMMAND_IRIS_MAY_WE_SUGGEST_NAME_IRISWORLD_INSTEAD_2,
            COMMAND_IRIS_THAT_FOLDER_ALREADY_EXISTS,
            COMMAND_IRIS_TRY_ONE_OVERWORLD_VANILLA_FLAT_THEEND,
            COMMAND_IRIS_DIMENSION_NOT_FOUND,
            COMMAND_IRIS_INSTALL_PACK_AND_RESTART,
            COMMAND_IRIS_EXCEPTION_RAISED_DURING_CREATION_SEE_CONSOLE_MORE_DETAILS,
            COMMAND_IRIS_SUCCESSFULLY_CREATED_YOUR_WORLD,
            COMMAND_IRIS_SPECIFIED_PLAYER_DOES_NOT_EXIST,
            COMMAND_IRIS_TO,
            COMMAND_IRIS_TOTAL_HEIGHT,
            COMMAND_IRIS_IRIS_WORLDS,
            COMMAND_IRIS_MESSAGE,
            COMMAND_IRIS_BUKKIT_WORLDS,
            COMMAND_IRIS_MESSAGE_2,
            COMMAND_IRIS_THIS_IS_NOT_IRIS_WORLD_IRIS_WORLDS,
            COMMAND_IRIS_REMOVING_WORLD,
            COMMAND_IRIS_FAILED_EVACUATE_WORLD,
            COMMAND_IRIS_FAILED_UNLOAD_WORLD,
            COMMAND_IRIS_SUCCESSFULLY_REMOVED_FROM_BUKKIT_YML,
            COMMAND_IRIS_LOOKS_LIKE_WORLD_WAS_ALREADY_REMOVED_FROM_BUKKIT_YML,
            COMMAND_IRIS_FAILED_SAVE_BUKKIT_YML_BECAUSE,
            COMMAND_IRIS_SET_DEBUG,
            COMMAND_IRIS_YOU_MUST_BE_IRIS_WORLD,
            COMMAND_IRIS_SENDING_METRICS,
            COMMAND_IRIS_THIS_IS_NOT_IRIS_WORLD_IRIS_WORLDS_2,
            COMMAND_IRIS_UNLOADING_WORLD,
            COMMAND_IRIS_WORLD_UNLOADED_SUCCESSFULLY,
            COMMAND_IRIS_FAILED_UNLOAD_WORLD_2,
            COMMAND_IRIS_FAILED_UNLOAD_WORLD_3,
            COMMAND_IRIS_DOESNT_EXIST_ON_SERVER,
            COMMAND_IRIS_GENERATOR,
            COMMAND_IRIS_IS_NOT_IRIS_WORLD,
            COMMAND_IRIS_COULD_NOT_DETERMINE_IRIS_DIMENSION,
            COMMAND_IRIS_LOADING_WORLD,
            COMMAND_IRIS_LOADED_SUCCESSFULLY,
            COMMAND_IRIS_THIS_IS_NOT_IRIS_WORLD_IRIS_WORLDS_3,
            COMMAND_IRIS_EVACUATING_WORLD,
            COMMAND_OBJECT_OBJECT_SIZE,
            COMMAND_OBJECT_BLOCKS_USED,
            COMMAND_OBJECT_BLOCKS_OBJECT,
            COMMAND_OBJECT_OTHER_BLOCK_TYPES,
            COMMAND_OBJECT_CURRENT_OBJECT_SIZE,
            COMMAND_OBJECT_NEW_OBJECT_SIZE,
            COMMAND_OBJECT_FAILED_SAVE_OBJECT,
            COMMAND_OBJECT_NO_OBJECTS_MATCHED,
            COMMAND_OBJECT_PLAUSIBILIZE_REACH_QUEUED_OBJECT_S,
            COMMAND_OBJECT_HOLD_YOUR_WAND,
            COMMAND_OBJECT_NO_AREA_SELECTED,
            COMMAND_OBJECT_READY_YOUR_WAND,
            COMMAND_OBJECT_READY_YOUR_WAND_2,
            COMMAND_OBJECT_INDICATED_SCALE_EXCEEDS_MAXIMUM_DOWNSCALED_MAXIMUM,
            COMMAND_OBJECT_UPDATED_WAND_OBJECTS_IOB,
            COMMAND_OBJECT_GIVEN_NEW_WAND_OBJECTS_IOB,
            COMMAND_OBJECT_UPDATED_WAND_OBJECTS_IOB_2,
            COMMAND_OBJECT_PLACED,
            COMMAND_OBJECT_YOU_NEED_HOLD_YOUR_WAND,
            COMMAND_OBJECT_FILE_ALREADY_EXISTS_SET_OVERWRITE_TRUE_OVERWRITE_IT,
            COMMAND_OBJECT_FAILED_SAVE_OBJECT_BECAUSE_IOEXCEPTION,
            COMMAND_OBJECT_SUCCESSFULLY_OBJECT_SAVED_OBJECTS,
            COMMAND_OBJECT_HOLD_YOUR_WAND_2,
            COMMAND_OBJECT_NO_AREA_SELECTED_2,
            COMMAND_OBJECT_REVERTED_PASTES,
            COMMAND_OBJECT_YOU_CAN_T_GET_WORLDEDIT_SELECTION_WITHOUT_WORLDEDIT_YOU_KNOW,
            COMMAND_OBJECT_YOU_DON_T_HAVE_WORLDEDIT_SELECTION_THIS_WORLD,
            COMMAND_OBJECT_FRESH_WAND_WITH_YOUR_CURRENT_WORLDEDIT_SELECTION_ON_IT,
            COMMAND_OBJECT_POOF_GOOD_LUCK_BUILDING,
            COMMAND_OBJECT_HOLD_YOUR_WAND_3,
            COMMAND_OBJECT_NO_AREA_SELECTED_3,
            COMMAND_OBJECT_AUTO_SELECT_COMPLETE,
            COMMAND_OBJECT_HOLD_YOUR_WAND_4,
            COMMAND_OBJECT_NO_AREA_SELECTED_4,
            COMMAND_OBJECT_AUTO_SELECT_COMPLETE_2,
            COMMAND_PREGEN_PREGEN_RADIUS_MUST_BE_GREATER_THAN_ZERO_BLOCKS,
            COMMAND_STUDIO_REGIONS_RADIUS_OUT_OF_RANGE,
            COMMAND_STUDIO_REGIONS_SCAN_FAILED,
            COMMAND_PREGEN_STRICT_SERIAL_PREGENERATION_REQUIRES_PAPER_PAPER_COMPATIBLE_SERVER,
            COMMAND_PREGEN_ENGINE_ACCESS_THIS_WORLD_IS_NULL,
            COMMAND_PREGEN_PLEASE_MAKE_SURE_WORLD_IS_LOADED_ENGINE_IS_INITIALIZED_GENERATE,
            COMMAND_PREGEN_FAILED_START_PREGENERATION_SEE_CONSOLE_DETAILS,
            COMMAND_PREGEN_NO_ACTIVE_PREGENERATION_TASKS_STOP,
            COMMAND_PREGEN_PAUSED_UNPAUSED_PREGENERATION_TASK_NOW,
            COMMAND_PREGEN_NO_ACTIVE_PREGENERATION_TASKS_PAUSE_UNPAUSE,
            COMMAND_PREGEN_NO_ACTIVE_PREGENERATION_TASK,
            COMMAND_PREGEN_PREGEN,
            COMMAND_PREGEN_SPEED_S_ETA_ELAPSED_METHOD,
            COMMAND_STRUCTURE_COULD_NOT_RESOLVE_PACK_DIMENSION,
            COMMAND_STRUCTURE_WROTE_STRUCTURE_INDEX,
            COMMAND_STRUCTURE_COULD_NOT_RESOLVE_PACK_DIMENSION_2,
            COMMAND_STRUCTURE_IMPORTING_ALL_VANILLA_DATAPACK_STRUCTURES_INTO_OVERWRITE,
            COMMAND_STRUCTURE_IMPORT_COMPLETE_STRUCTURES_OBJECTS_WRITTEN_FAILED,
            COMMAND_STRUCTURE_REFERENCE_THEM_FROM_BIOME_REGION_DIMENSION_STRUCTURES_LIST_RUN_IRIS,
            COMMAND_STRUCTURE_COULD_NOT_RESOLVE_PACK_DIMENSION_3,
            COMMAND_STRUCTURE_CAPTURED_STRUCTURES_PLACE_THEM_FROM_STRUCTURES_LIST_REGENERATE_CHUNKS_DELETE,
            COMMAND_STRUCTURE_NO_LOADED_IRIS_WORLD_FOUND_JOIN_CREATE_ONE_FIRST_SEARCH,
            COMMAND_STRUCTURE_SELECTED_IRIS_WORLD_HAS_NO_ACTIVE_GENERATOR_ENGINE,
            COMMAND_STRUCTURE_COULD_NOT_RESOLVE_PACK_DIMENSION_4,
            COMMAND_STRUCTURE_NO_IRIS_STRUCTURE_THIS_PACK,
            COMMAND_STRUCTURE_STRUCTURE_ASSEMBLED_0_PIECES_CHECK_STARTPOOL,
            COMMAND_STRUCTURE_STRUCTURE_PIECES_FOOTPRINT_X_BLOCKS_SAMPLE_SEED_1234,
            COMMAND_STRUCTURE_COULD_NOT_RESOLVE_PACK_DIMENSION_5,
            COMMAND_STRUCTURE_NO_IRIS_STRUCTURE_THIS_PACK_2,
            COMMAND_STRUCTURE_STRUCTURE_ASSEMBLED_0_PIECES,
            COMMAND_STRUCTURE_PLACED_PIECES_AT_YOUR_LOCATION,
            COMMAND_STRUCTURE_PLACEMENT_CHANGED_NO_BLOCKS,
            COMMAND_STUDIO_OPENING_STUDIO_PACK_SEED,
            COMMAND_STUDIO_PROVIDE_DIMENSION_PACK_IRIS_STD_IMPORTVANILLA_PACK_DIMENSION,
            COMMAND_STUDIO_COULD_NOT_RESOLVE_PACK_DIMENSION,
            COMMAND_STUDIO_OPENING_VSCODE_PACK,
            COMMAND_STUDIO_PACK_HAS_VERSION,
            COMMAND_STUDIO_OPENING_NOISE_EXPLORER,
            COMMAND_STUDIO_OPENING_IMAGE_MAP_STUDIO,
            COMMAND_STUDIO_CANNOT_ADD_ITEMS_VIRTUAL_INVENTORY_BECAUSE,
            COMMAND_STUDIO_OPENING_INVENTORY_NOW,
            COMMAND_STUDIO_ONLY_WORKS_IRIS_WORLD,
            COMMAND_STUDIO_YOU_NEED_BE_SPECIFY_IRIS_GENERATED_WORLD,
            COMMAND_STUDIO_OPENING_MAP,
            COMMAND_STUDIO_CALCULATING_PERFORMANCE_METRICS_NOISE_GENERATORS,
            COMMAND_STUDIO_CALCULATING_INTERPOLATOR_TIMINGS,
            COMMAND_STUDIO_PROCESSING_GENERATOR_SCORES,
            COMMAND_STUDIO_SCORE,
            COMMAND_STUDIO_DONE,
            COMMAND_STUDIO_YOU_HAVE_BE_IRIS_WORLD_SPAWN_ENTITIES_PROPERLY_TRYING_SPAWN,
            COMMAND_STUDIO_NO_STUDIO_WORLD_IS_OPEN,
            COMMAND_STUDIO_YOU_ARE_ALREADY_STUDIO_WORLD,
            COMMAND_STUDIO_SENDING_YOU_STUDIO_WORLD,
            COMMAND_STUDIO_UPDATING_CODE_WORKSPACE,
            COMMAND_STUDIO_UPDATED_CODE_WORKSPACE,
            COMMAND_STUDIO_INVALID_PROJECT_TRY_DELETING_CODE_WORKSPACE_FILE_TRY_AGAIN,
            COMMAND_STUDIO_YOU_MUST_BE_IRIS_WORLD,
            COMMAND_STUDIO_YOU_MUST_BE_IRIS_WORLD_2,
            COMMAND_STUDIO_CAPTURING_IGENDATA_FROM_NEARBY_CHUNKS,
            COMMAND_STUDIO_REPORTED,
            COMMAND_STUDIO_YOU_MUST_HAVE_SERVER_LAUNCHED_GUIS_ENABLED_SETTINGS,
            COMMAND_STUDIO_PLAYERS_ONLY,
            COMMAND_STUDIO_NO_STUDIO_WORLD_IS_OPEN_2,
            COMMAND_STUDIO_YOU_MUST_BE_STUDIO_WORLD,
            COMMAND_WHAT_MATERIAL,
            COMMAND_WHAT_FULL,
            COMMAND_WHAT_PLEASE_HOLD_BLOCK_ITEM,
            COMMAND_WHAT_MATERIAL_2,
            COMMAND_WHAT_PLEASE_HOLD_BLOCK_ITEM_2,
            COMMAND_WHAT_IBIOME,
            COMMAND_WHAT_NON_IRIS_BIOME,
            COMMAND_WHAT_DATA_PACK_BIOME_ID,
            COMMAND_WHAT_IREGION,
            COMMAND_WHAT_IRIS_WORLDS_ONLY,
            COMMAND_WHAT_PLEASE_LOOK_AT_ANY_BLOCK_NOT_AT_SKY,
            COMMAND_WHAT_MATERIAL_3,
            COMMAND_WHAT_FULL_2,
            COMMAND_WHAT_STORAGE_BLOCK_LOOT_CAPABLE,
            COMMAND_WHAT_LIT_BLOCK_LIGHT_CAPABLE,
            COMMAND_WHAT_FOLIAGE_BLOCK,
            COMMAND_WHAT_DECORANT_BLOCK,
            COMMAND_WHAT_FLUID_BLOCK,
            COMMAND_WHAT_PLANTABLE_FOLIAGE_BLOCK,
            COMMAND_WHAT_SOLID_BLOCK,
            COMMAND_WHAT_FOUND_NEARBY_MARKERS,
            COMMAND_WHAT_IRIS_WORLDS_ONLY_2
    );

    private BukkitCommandMessagesExtended() {
    }

    public static List<MessageKey> keys() {
        return KEYS;
    }
}

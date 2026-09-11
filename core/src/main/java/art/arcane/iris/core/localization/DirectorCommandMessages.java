package art.arcane.iris.core.localization;

import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;

public final class DirectorCommandMessages {
    public static final TextKey DEBUG_DESCRIPTION = TextKey.of("iris.director.commanddebug.description", "Iris diagnostic tools");
    public static final TextKey DEBUG_DUMP_DESCRIPTION = TextKey.of("iris.director.commandiris.director.debugdump", "Create and optionally upload a diagnostic report");
    public static final TextKey DEBUG_DUMP_UPLOAD = TextKey.of("iris.director.commandiris.param.debugdump_upload", "Upload the report to mclo.gs");
    public static final TextKey COMMAND_DATAPACK_DIRECTOR_DOWNLOAD_MANAGE_EXTERNAL_DATAPACK_IMPORTS_MODRINTH = TextKey.of(
            "iris.director.commanddatapack.director.download_manage_external_datapack_imports_modrinth",
            "Download & manage external datapack imports"
    );
    public static final TextKey COMMAND_DATAPACK_DIRECTOR_DOWNLOAD_UPDATE_EVERY_DATAPACK_LISTED_PACK_DIMENSION_S_DATAPACKIMPORTS_INSTALL_IT_INTO = TextKey.of(
            "iris.director.commanddatapack.director.download_update_every_datapack_listed_pack_dimension_s_datapackimports_install_it_into",
            "Download/update every datapack listed in a pack dimension's 'datapackImports' and install it into the world so its structures register like vanilla"
    );
    public static final TextKey COMMAND_DATAPACK_PARAM_RESTART_SERVER_WHEN_NEW_DATAPACKS_ARE_INSTALLED_REQUIRED_NEW_STRUCTURES_REGISTER_GENERATE = TextKey.of(
            "iris.director.commanddatapack.param.restart_server_when_new_datapacks_are_installed_required_new_structures_register_generate",
            "Restart the server when new datapacks are installed (required for new structures to register and generate)"
    );
    public static final TextKey COMMAND_DATAPACK_DIRECTOR_LIST_CONFIGURED_DATAPACK_IMPORTS_THEIR_INSTALLED_VERSIONS = TextKey.of(
            "iris.director.commanddatapack.director.list_configured_datapack_imports_their_installed_versions",
            "List configured datapack imports and their installed versions"
    );
    public static final TextKey COMMAND_DATAPACK_DIRECTOR_REMOVE_INSTALLED_DATAPACK_BY_ID_ALSO_DELETE_ITS_URL_FROM_DATAPACKIMPORTS_KEEP = TextKey.of(
            "iris.director.commanddatapack.director.remove_installed_datapack_by_id_also_delete_its_url_from_datapackimports_keep",
            "Remove an installed datapack by id (also delete its URL from datapackImports to keep it gone)"
    );
    public static final TextKey COMMAND_DATAPACK_PARAM_DATAPACK_ID_FOLDER_NAME_SHOWN_BY_IRIS_DATAPACK_LIST = TextKey.of(
            "iris.director.commanddatapack.param.datapack_id_folder_name_shown_by_iris_datapack_list",
            "The datapack id (folder name) shown by /iris datapack list"
    );
    public static final TextKey COMMAND_DEVELOPER_DIRECTOR_IRIS_WORLD_MANAGER = TextKey.of(
            "iris.director.commanddeveloper.director.iris_world_manager",
            "Iris World Manager"
    );
    public static final TextKey COMMAND_DEVELOPER_DIRECTOR_GET_LOADED_TECTONICPLATES_COUNT = TextKey.of(
            "iris.director.commanddeveloper.director.get_loaded_tectonicplates_count",
            "Get Loaded TectonicPlates Count"
    );
    public static final TextKey COMMAND_DEVELOPER_DIRECTOR_HASH_GENERATED_BLOCK_OUTPUT_FIXED_AREA_DETERMINISM_IDENTITY_TESTING = TextKey.of(
            "iris.director.commanddeveloper.director.hash_generated_block_output_fixed_area_determinism_identity_testing",
            "Hash generated block output of a fixed area for determinism/identity testing"
    );
    public static final TextKey COMMAND_DEVELOPER_PARAM_WORLD_HASH = TextKey.of(
            "iris.director.commanddeveloper.param.world_hash",
            "The world to hash"
    );
    public static final TextKey COMMAND_DEVELOPER_PARAM_RADIUS_CHUNKS_AROUND_CENTER = TextKey.of(
            "iris.director.commanddeveloper.param.radius_chunks_around_center",
            "Radius in chunks around the center"
    );
    public static final TextKey COMMAND_DEVELOPER_PARAM_CENTER_CHUNK_X = TextKey.of(
            "iris.director.commanddeveloper.param.center_chunk_x",
            "Center chunk X"
    );
    public static final TextKey COMMAND_DEVELOPER_PARAM_CENTER_CHUNK_Z = TextKey.of(
            "iris.director.commanddeveloper.param.center_chunk_z",
            "Center chunk Z"
    );
    public static final TextKey COMMAND_DEVELOPER_DIRECTOR_STAGE_WORLD_GENERATION_UPDATE = TextKey.of(
            "iris.director.commanddeveloper.director.stage_world_generation_update",
            "Stage a world generation update"
    );
    public static final TextKey COMMAND_DEVELOPER_PARAM_WORLD_UPDATE = TextKey.of(
            "iris.director.commanddeveloper.param.world_update",
            "The world to update"
    );
    public static final TextKey COMMAND_DEVELOPER_PARAM_PACK_INSTALL_INTO_WORLD = TextKey.of(
            "iris.director.commanddeveloper.param.pack_install_into_world",
            "The pack to install into the world"
    );
    public static final TextKey COMMAND_DEVELOPER_PARAM_MAKE_SURE_MAKE_BACKUP_READ_WARNINGS_FIRST = TextKey.of(
            "iris.director.commanddeveloper.param.make_sure_make_backup_read_warnings_first",
            "Make sure to make a backup & read the warnings first!"
    );
    public static final TextKey COMMAND_DEVELOPER_DIRECTOR_TEST = TextKey.of(
            "iris.director.commanddeveloper.director.test",
            "Test"
    );
    public static final TextKey COMMAND_DEVELOPER_PARAM_DUMP_WHOLE_TECTONIC_PLATE_INSTEAD_SINGLE_SECTION = TextKey.of(
            "iris.director.commanddeveloper.param.dump_whole_tectonic_plate_instead_single_section",
            "Dump the whole tectonic plate instead of a single section"
    );
    public static final TextKey COMMAND_DEVELOPER_PARAM_DUMP_FILE_ID_UNDER_PLUGINS_IRIS_DUMP_PV_ID = TextKey.of(
            "iris.director.commanddeveloper.param.dump_file_id_under_plugins_iris_dump_pv_id",
            "The dump file id under plugins/Iris/dump (pv.<id>.*)"
    );
    public static final TextKey COMMAND_DEVELOPER_DIRECTOR_TEST_2 = TextKey.of(
            "iris.director.commanddeveloper.director.test_2",
            "Test"
    );
    public static final TextKey COMMAND_DEVELOPER_PARAM_PACK_BENCH = TextKey.of(
            "iris.director.commanddeveloper.param.pack_bench",
            "The pack to bench"
    );
    public static final TextKey COMMAND_DEVELOPER_PARAM_RADIUS_REGIONS = TextKey.of(
            "iris.director.commanddeveloper.param.radius_regions",
            "Radius in regions"
    );
    public static final TextKey COMMAND_DEVELOPER_PARAM_OPEN_GUI_WHILE_BENCHMARKING = TextKey.of(
            "iris.director.commanddeveloper.param.open_gui_while_benchmarking",
            "Open GUI while benchmarking"
    );
    public static final TextKey COMMAND_DEVELOPER_DIRECTOR_UPGRADE_ANOTHER_MINECRAFT_VERSION = TextKey.of(
            "iris.director.commanddeveloper.director.upgrade_another_minecraft_version",
            "Upgrade to another Minecraft version"
    );
    public static final TextKey COMMAND_DEVELOPER_PARAM_VERSION_UPGRADE = TextKey.of(
            "iris.director.commanddeveloper.param.version_upgrade",
            "The version to upgrade to"
    );
    public static final TextKey COMMAND_DEVELOPER_DIRECTOR_TEST_3 = TextKey.of(
            "iris.director.commanddeveloper.director.test_3",
            "test"
    );
    public static final TextKey COMMAND_DEVELOPER_PARAM_WORLD_FOLDER_SCAN_MCA_REGION_FILES = TextKey.of(
            "iris.director.commanddeveloper.param.world_folder_scan_mca_region_files",
            "The world folder to scan for .mca region files"
    );
    public static final TextKey COMMAND_DEVELOPER_DIRECTOR_DELETE_NEARBY_CHUNK_BLOCKS_REGEN_TESTING = TextKey.of(
            "iris.director.commanddeveloper.director.delete_nearby_chunk_blocks_regen_testing",
            "Delete nearby chunk blocks for regen testing"
    );
    public static final TextKey COMMAND_DEVELOPER_PARAM_RADIUS_CHUNKS_AROUND_YOUR_CURRENT_CHUNK = TextKey.of(
            "iris.director.commanddeveloper.param.radius_chunks_around_your_current_chunk",
            "Radius in chunks around your current chunk"
    );
    public static final TextKey COMMAND_DEVELOPER_DIRECTOR_TEST_4 = TextKey.of(
            "iris.director.commanddeveloper.director.test_4",
            "Test"
    );
    public static final TextKey COMMAND_DEVELOPER_DIRECTOR_DELETE_REGENERATE_NEARBY_CHUNKS_PLACE_USING_IRIS_GENERATION = TextKey.of(
            "iris.director.commanddeveloper.director.delete_regenerate_nearby_chunks_place_using_iris_generation",
            "Delete and regenerate nearby chunks in place using Iris generation"
    );
    public static final TextKey COMMAND_DEVELOPER_PARAM_RADIUS_NEARBY_CHUNKS = TextKey.of(
            "iris.director.commanddeveloper.param.radius_nearby_chunks",
            "The radius of nearby chunks"
    );
    public static final TextKey COMMAND_DEVELOPER_DIRECTOR_GENERATE_CHUNKS_INTO_BUFFERS_NO_WORLD_WRITES_HASH_BLOCKS_BIOMES_CAPTURES_GOLDEN = TextKey.of(
            "iris.director.commanddeveloper.director.generate_chunks_into_buffers_no_world_writes_hash_blocks_biomes_captures_golden",
            "Generate chunks into buffers (no world writes) and hash blocks+biomes; captures a golden file or verifies against an existing one. Deletes the world's entire mantle - use on disposable test worlds."
    );
    public static final TextKey COMMAND_DEVELOPER_PARAM_WORLD_SCAN = TextKey.of(
            "iris.director.commanddeveloper.param.world_scan",
            "The world to scan"
    );
    public static final TextKey COMMAND_DEVELOPER_PARAM_RADIUS_CHUNKS_AROUND_CENTER_2 = TextKey.of(
            "iris.director.commanddeveloper.param.radius_chunks_around_center_2",
            "Radius in chunks around the center"
    );
    public static final TextKey COMMAND_DEVELOPER_PARAM_CENTER_CHUNK_X_2 = TextKey.of(
            "iris.director.commanddeveloper.param.center_chunk_x_2",
            "Center chunk X"
    );
    public static final TextKey COMMAND_DEVELOPER_PARAM_CENTER_CHUNK_Z_2 = TextKey.of(
            "iris.director.commanddeveloper.param.center_chunk_z_2",
            "Center chunk Z"
    );
    public static final TextKey COMMAND_DEVELOPER_PARAM_DELETE_MANTLE_DATA_SCAN_AREA_FIRST_FULL_REGENERATION_FROM_SCRATCH = TextKey.of(
            "iris.director.commanddeveloper.param.delete_mantle_data_scan_area_first_full_regeneration_from_scratch",
            "Delete the world's entire mantle folder first for full regeneration from scratch"
    );
    public static final TextKey COMMAND_DEVELOPER_PARAM_CONCURRENT_CHUNK_GENERATIONS_1_STRICTLY_SERIAL_ORDER_DEPENDENCE_TESTING = TextKey.of(
            "iris.director.commanddeveloper.param.concurrent_chunk_generations_1_strictly_serial_order_dependence_testing",
            "Concurrent chunk generations; 1 = strictly serial for order-dependence testing"
    );
    public static final TextKey COMMAND_DEVELOPER_PARAM_ALSO_DUMP_FULL_PER_CHUNK_NON_AIR_BLOCKSTATES_OFFLINE_DIFFING = TextKey.of(
            "iris.director.commanddeveloper.param.also_dump_full_per_chunk_non_air_blockstates_offline_diffing",
            "Also dump full per-chunk non-air blockstates for offline diffing"
    );
    public static final TextKey COMMAND_EDIT_DIRECTOR_EDIT_SOMETHING = TextKey.of(
            "iris.director.commandedit.director.edit_something",
            "Edit something"
    );
    public static final TextKey COMMAND_EDIT_DIRECTOR_EDIT_BIOME_YOU_SPECIFIED = TextKey.of(
            "iris.director.commandedit.director.edit_biome_you_specified",
            "Edit the biome you specified"
    );
    public static final TextKey COMMAND_EDIT_PARAM_BIOME_EDIT = TextKey.of(
            "iris.director.commandedit.param.biome_edit",
            "The biome to edit"
    );
    public static final TextKey COMMAND_EDIT_DIRECTOR_EDIT_REGION_YOU_SPECIFIED = TextKey.of(
            "iris.director.commandedit.director.edit_region_you_specified",
            "Edit the region you specified"
    );
    public static final TextKey COMMAND_EDIT_PARAM_REGION_EDIT = TextKey.of(
            "iris.director.commandedit.param.region_edit",
            "The region to edit"
    );
    public static final TextKey COMMAND_EDIT_DIRECTOR_EDIT_DIMENSION_YOU_SPECIFIED = TextKey.of(
            "iris.director.commandedit.director.edit_dimension_you_specified",
            "Edit the dimension you specified"
    );
    public static final TextKey COMMAND_EDIT_PARAM_DIMENSION_EDIT = TextKey.of(
            "iris.director.commandedit.param.dimension_edit",
            "The dimension to edit"
    );
    public static final TextKey COMMAND_FIND_DIRECTOR_IRIS_FIND_COMMANDS = TextKey.of(
            "iris.director.commandfind.director.iris_find_commands",
            "Iris Find commands"
    );
    public static final TextKey COMMAND_FIND_DIRECTOR_FIND_BIOME = TextKey.of(
            "iris.director.commandfind.director.find_biome",
            "Find a biome"
    );
    public static final TextKey COMMAND_FIND_PARAM_BIOME_LOOK = TextKey.of(
            "iris.director.commandfind.param.biome_look",
            "The biome to look for"
    );
    public static final TextKey COMMAND_FIND_PARAM_SHOULD_YOU_BE_TELEPORTED = TextKey.of(
            "iris.director.commandfind.param.should_you_be_teleported",
            "Should you be teleported"
    );
    public static final TextKey COMMAND_FIND_DIRECTOR_FIND_REGION = TextKey.of(
            "iris.director.commandfind.director.find_region",
            "Find a region"
    );
    public static final TextKey COMMAND_FIND_PARAM_REGION_LOOK = TextKey.of(
            "iris.director.commandfind.param.region_look",
            "The region to look for"
    );
    public static final TextKey COMMAND_FIND_PARAM_SHOULD_YOU_BE_TELEPORTED_2 = TextKey.of(
            "iris.director.commandfind.param.should_you_be_teleported_2",
            "Should you be teleported"
    );
    public static final TextKey COMMAND_FIND_DIRECTOR_FIND_POINT_INTEREST = TextKey.of(
            "iris.director.commandfind.director.find_point_interest",
            "Find a point of interest."
    );
    public static final TextKey COMMAND_FIND_PARAM_TYPE_POI_LOOK = TextKey.of(
            "iris.director.commandfind.param.type_poi_look",
            "The type of PoI to look for."
    );
    public static final TextKey COMMAND_FIND_PARAM_SHOULD_YOU_BE_TELEPORTED_3 = TextKey.of(
            "iris.director.commandfind.param.should_you_be_teleported_3",
            "Should you be teleported"
    );
    public static final TextKey COMMAND_FIND_DIRECTOR_FIND_STRUCTURE_VANILLA_KEY_LIKE_MINECRAFT_VILLAGE_PLAINS_MINECRAFT_STRONGHOLD_IMPORTED_IRIS = TextKey.of(
            "iris.director.commandfind.director.find_structure_vanilla_key_like_minecraft_village_plains_minecraft_stronghold_imported_iris",
            "Find a structure (a vanilla key like minecraft:village_plains or minecraft:stronghold, or an imported iris structure key)"
    );
    public static final TextKey COMMAND_FIND_PARAM_STRUCTURE_LOOK_E_G_MINECRAFT_VILLAGE_PLAINS_MINECRAFT_STRONGHOLD_MINECRAFT_ANCIENT_CITY = TextKey.of(
            "iris.director.commandfind.param.structure_look_e_g_minecraft_village_plains_minecraft_stronghold_minecraft_ancient_city",
            "The structure to look for (e.g. minecraft:village_plains, minecraft:stronghold, minecraft_ancient_city)"
    );
    public static final TextKey COMMAND_FIND_DIRECTOR_FIND_OBJECT = TextKey.of(
            "iris.director.commandfind.director.find_object",
            "Find an object"
    );
    public static final TextKey COMMAND_FIND_PARAM_OBJECT_LOOK = TextKey.of(
            "iris.director.commandfind.param.object_look",
            "The object to look for"
    );
    public static final TextKey COMMAND_FIND_PARAM_SHOULD_YOU_BE_TELEPORTED_4 = TextKey.of(
            "iris.director.commandfind.param.should_you_be_teleported_4",
            "Should you be teleported"
    );
    public static final TextKey COMMAND_IRIS_DIRECTOR_BASIC_COMMAND = TextKey.of(
            "iris.director.commandiris.director.basic_command",
            "Basic Command"
    );
    public static final TextKey COMMAND_IRIS_DIRECTOR_CREATE_NEW_WORLD = TextKey.of(
            "iris.director.commandiris.director.create_new_world",
            "Create a new world"
    );
    public static final TextKey COMMAND_IRIS_PARAM_NAME_WORLD_CREATE = TextKey.of(
            "iris.director.commandiris.param.name_world_create",
            "The name of the world to create"
    );
    public static final TextKey COMMAND_IRIS_PARAM_DIMENSION_PACK_CREATE_WORLD_WITH = TextKey.of(
            "iris.director.commandiris.param.dimension_pack_create_world_with",
            "The dimension/pack to create the world with"
    );
    public static final TextKey COMMAND_IRIS_PARAM_SEED_GENERATE_WORLD_WITH = TextKey.of(
            "iris.director.commandiris.param.seed_generate_world_with",
            "The seed to generate the world with"
    );
    public static final TextKey COMMAND_IRIS_PARAM_REPLACE_EXACT_EXISTING_WORLD_SLOT_NEXT_RESTART = TextKey.of(
            "iris.director.commandiris.param.replace_exact_existing_world_slot_next_restart",
            "Replace the exact existing world slot on the next restart"
    );
    public static final TextKey COMMAND_IRIS_DIRECTOR_TELEPORT_ANOTHER_WORLD = TextKey.of(
            "iris.director.commandiris.director.teleport_another_world",
            "Teleport to another world"
    );
    public static final TextKey COMMAND_IRIS_PARAM_WORLD_TELEPORT = TextKey.of(
            "iris.director.commandiris.param.world_teleport",
            "World to teleport to"
    );
    public static final TextKey COMMAND_IRIS_PARAM_PLAYER_TELEPORT = TextKey.of(
            "iris.director.commandiris.param.player_teleport",
            "Player to teleport"
    );
    public static final TextKey COMMAND_IRIS_DIRECTOR_PRINT_VERSION_INFORMATION = TextKey.of(
            "iris.director.commandiris.director.print_version_information",
            "Print version information"
    );
    public static final TextKey COMMAND_IRIS_DIRECTOR_BENCHMARK_PACK = TextKey.of(
            "iris.director.commandiris.director.benchmark_pack",
            "Benchmark a pack"
    );
    public static final TextKey COMMAND_IRIS_PARAM_DIMENSION_BENCHMARK = TextKey.of(
            "iris.director.commandiris.param.dimension_benchmark",
            "Dimension to benchmark"
    );
    public static final TextKey COMMAND_IRIS_DIRECTOR_PRINT_WORLD_HEIGHT_INFORMATION = TextKey.of(
            "iris.director.commandiris.director.print_world_height_information",
            "Print world height information"
    );
    public static final TextKey COMMAND_IRIS_DIRECTOR_CHECK_ACCESS_ALL_WORLDS = TextKey.of(
            "iris.director.commandiris.director.check_access_all_worlds",
            "Check access of all worlds."
    );
    public static final TextKey COMMAND_IRIS_DIRECTOR_REMOVE_IRIS_WORLD = TextKey.of(
            "iris.director.commandiris.director.remove_iris_world",
            "Remove an Iris world"
    );
    public static final TextKey COMMAND_IRIS_PARAM_WORLD_REMOVE = TextKey.of(
            "iris.director.commandiris.param.world_remove",
            "The world to remove"
    );
    public static final TextKey COMMAND_IRIS_PARAM_WHETHER_ALSO_REMOVE_FOLDER_IF_SET_FALSE_JUST_DOES_NOT_LOAD_WORLD = TextKey.of(
            "iris.director.commandiris.param.whether_also_remove_folder_if_set_false_just_does_not_load_world",
            "Whether to also remove the folder (if set to false, just does not load the world)"
    );
    public static final TextKey COMMAND_IRIS_DIRECTOR_TOGGLE_DEBUG = TextKey.of(
            "iris.director.commandiris.director.toggle_debug",
            "Toggle debug"
    );
    public static final TextKey COMMAND_IRIS_DIRECTOR_DOWNLOAD_PROJECT = TextKey.of(
            "iris.director.commandiris.director.download_project",
            "Download a project."
    );
    public static final TextKey COMMAND_IRIS_PARAM_PACK_DOWNLOAD = TextKey.of(
            "iris.director.commandiris.param.pack_download",
            "The pack to download"
    );
    public static final TextKey COMMAND_IRIS_DIRECTOR_GET_METRICS_YOUR_WORLD = TextKey.of(
            "iris.director.commandiris.director.get_metrics_your_world",
            "Get metrics for your world"
    );
    public static final TextKey COMMAND_IRIS_DIRECTOR_RELOAD_CONFIGURATION_FILE_THIS_IS_ALSO_DONE_AUTOMATICALLY = TextKey.of(
            "iris.director.commandiris.director.reload_configuration_file_this_is_also_done_automatically",
            "Reload configuration file (this is also done automatically)"
    );
    public static final TextKey COMMAND_IRIS_DIRECTOR_UNLOAD_IRIS_WORLD = TextKey.of(
            "iris.director.commandiris.director.unload_iris_world",
            "Unload an Iris World"
    );
    public static final TextKey COMMAND_IRIS_PARAM_WORLD_UNLOAD = TextKey.of(
            "iris.director.commandiris.param.world_unload",
            "The world to unload"
    );
    public static final TextKey COMMAND_IRIS_DIRECTOR_LOAD_IRIS_WORLD = TextKey.of(
            "iris.director.commandiris.director.load_iris_world",
            "Load an Iris World"
    );
    public static final TextKey COMMAND_IRIS_PARAM_NAME_WORLD_LOAD = TextKey.of(
            "iris.director.commandiris.param.name_world_load",
            "The name of the world to load"
    );
    public static final TextKey COMMAND_IRIS_DIRECTOR_EVACUATE_IRIS_WORLD = TextKey.of(
            "iris.director.commandiris.director.evacuate_iris_world",
            "Evacuate an iris world"
    );
    public static final TextKey COMMAND_IRIS_PARAM_EVACUATE_WORLD = TextKey.of(
            "iris.director.commandiris.param.evacuate_world",
            "Evacuate the world"
    );
    public static final TextKey COMMAND_OBJECT_DIRECTOR_IRIS_OBJECT_MANIPULATION = TextKey.of(
            "iris.director.commandobject.director.iris_object_manipulation",
            "Iris object manipulation"
    );
    public static final TextKey COMMAND_OBJECT_DIRECTOR_OPEN_OBJECT_STUDIO_WORLD_GRID_EVERY_OBJECT_DIMENSION_OPTIONAL_DEFAULTS_ALL_PACKS = TextKey.of(
            "iris.director.commandobject.director.open_object_studio_world_grid_every_object_dimension_optional_defaults_all_packs",
            "Open an object studio world (grid of every object; dimension optional, defaults to all packs)"
    );
    public static final TextKey COMMAND_OBJECT_PARAM_OPTIONAL_DIMENSION_WHOSE_OBJECT_PACK_LAY_OUT_OMIT_AGGREGATE_OBJECTS_FROM_EVERY = TextKey.of(
            "iris.director.commandobject.param.optional_dimension_whose_object_pack_lay_out_omit_aggregate_objects_from_every",
            "Optional dimension whose object pack to lay out; omit to aggregate objects from every pack"
    );
    public static final TextKey COMMAND_OBJECT_PARAM_SEED_GENERATE_STUDIO_WITH = TextKey.of(
            "iris.director.commandobject.param.seed_generate_studio_with",
            "The seed to generate the studio with"
    );
    public static final TextKey COMMAND_OBJECT_DIRECTOR_CHECK_COMPOSITION_OBJECT = TextKey.of(
            "iris.director.commandobject.director.check_composition_object",
            "Check the composition of an object"
    );
    public static final TextKey COMMAND_OBJECT_PARAM_OBJECT_ANALYZE = TextKey.of(
            "iris.director.commandobject.param.object_analyze",
            "The object to analyze"
    );
    public static final TextKey COMMAND_OBJECT_DIRECTOR_SHRINK_OBJECT_ITS_MINIMUM_SIZE = TextKey.of(
            "iris.director.commandobject.director.shrink_object_its_minimum_size",
            "Shrink an object to its minimum size"
    );
    public static final TextKey COMMAND_OBJECT_PARAM_OBJECT_SHRINK = TextKey.of(
            "iris.director.commandobject.param.object_shrink",
            "The object to shrink"
    );
    public static final TextKey COMMAND_OBJECT_DIRECTOR_GROW_ORGANIC_BRANCHES_THROUGH_CANOPY_SO_EVERY_LEAF_SURVIVES_VANILLA_DECAY = TextKey.of(
            "iris.director.commandobject.director.grow_organic_branches_through_canopy_so_every_leaf_survives_vanilla_decay",
            "Grow organic branches through the canopy so every leaf survives vanilla decay"
    );
    public static final TextKey COMMAND_OBJECT_PARAM_OBJECT_KEY_PREFIX_TREES_FILESYSTEM_PATH = TextKey.of(
            "iris.director.commandobject.param.object_key_prefix_trees_filesystem_path",
            "Object key, prefix (trees/), or filesystem path"
    );
    public static final TextKey COMMAND_OBJECT_PARAM_DRYRUN_TRUE_ANALYZES_ONLY_WRITES_NOTHING = TextKey.of(
            "iris.director.commandobject.param.dryrun_true_analyzes_only_writes_nothing",
            "dryrun=true analyzes only, writes nothing"
    );
    public static final TextKey COMMAND_OBJECT_PARAM_REACH_N_MAX_BRANCH_LENGTH_BLOCKS_FROM_EXISTING_WOOD_FARTHER_LEAF_CLUSTERS = TextKey.of(
            "iris.director.commandobject.param.reach_n_max_branch_length_blocks_from_existing_wood_farther_leaf_clusters",
            "reach=N max branch length in blocks from existing wood; farther leaf clusters are pinned persistent instead. reach=0 grows unlimited"
    );
    public static final TextKey COMMAND_OBJECT_DIRECTOR_CONVERT_SCHEM_FILES_CONVERT_FOLDER_IOB_FILES = TextKey.of(
            "iris.director.commandobject.director.convert_schem_files_convert_folder_iob_files",
            "Convert .schem files in the 'convert' folder to .iob files."
    );
    public static final TextKey COMMAND_OBJECT_DIRECTOR_GET_POWDER_THAT_REVEALS_OBJECTS = TextKey.of(
            "iris.director.commandobject.director.get_powder_that_reveals_objects",
            "Get a powder that reveals objects"
    );
    public static final TextKey COMMAND_OBJECT_DIRECTOR_CONTRACT_SELECTION_BASED_ON_YOUR_LOOKING_DIRECTION = TextKey.of(
            "iris.director.commandobject.director.contract_selection_based_on_your_looking_direction",
            "Contract a selection based on your looking direction"
    );
    public static final TextKey COMMAND_OBJECT_PARAM_AMOUNT_INSET_BY = TextKey.of(
            "iris.director.commandobject.param.amount_inset_by",
            "The amount to inset by"
    );
    public static final TextKey COMMAND_OBJECT_DIRECTOR_SET_POINT_1_LOOK = TextKey.of(
            "iris.director.commandobject.director.set_point_1_look",
            "Set point 1 to look"
    );
    public static final TextKey COMMAND_OBJECT_PARAM_WHETHER_USE_YOUR_CURRENT_POSITION_WHERE_YOU_LOOK = TextKey.of(
            "iris.director.commandobject.param.whether_use_your_current_position_where_you_look",
            "Whether to use your current position, or where you look"
    );
    public static final TextKey COMMAND_OBJECT_DIRECTOR_SET_POINT_2_LOOK = TextKey.of(
            "iris.director.commandobject.director.set_point_2_look",
            "Set point 2 to look"
    );
    public static final TextKey COMMAND_OBJECT_PARAM_WHETHER_USE_YOUR_CURRENT_POSITION_WHERE_YOU_LOOK_2 = TextKey.of(
            "iris.director.commandobject.param.whether_use_your_current_position_where_you_look_2",
            "Whether to use your current position, or where you look"
    );
    public static final TextKey COMMAND_OBJECT_DIRECTOR_PASTE_OBJECT = TextKey.of(
            "iris.director.commandobject.director.paste_object",
            "Paste an object"
    );
    public static final TextKey COMMAND_OBJECT_PARAM_OBJECT_PASTE = TextKey.of(
            "iris.director.commandobject.param.object_paste",
            "The object to paste"
    );
    public static final TextKey COMMAND_OBJECT_PARAM_WHETHER_NOT_EDIT_OBJECT_NEED_HOLD_WAND = TextKey.of(
            "iris.director.commandobject.param.whether_not_edit_object_need_hold_wand",
            "Whether or not to edit the object (need to hold wand)"
    );
    public static final TextKey COMMAND_OBJECT_PARAM_AMOUNT_DEGREES_ROTATE_BY = TextKey.of(
            "iris.director.commandobject.param.amount_degrees_rotate_by",
            "The amount of degrees to rotate by"
    );
    public static final TextKey COMMAND_OBJECT_PARAM_FACTOR_BY_WHICH_SCALE_OBJECT_PLACEMENT = TextKey.of(
            "iris.director.commandobject.param.factor_by_which_scale_object_placement",
            "The factor by which to scale the object placement"
    );
    public static final TextKey COMMAND_OBJECT_PARAM_SCALE_INTERPOLATOR_USE = TextKey.of(
            "iris.director.commandobject.param.scale_interpolator_use",
            "The scale interpolator to use"
    );
    public static final TextKey COMMAND_OBJECT_DIRECTOR_SAVE_OBJECT = TextKey.of(
            "iris.director.commandobject.director.save_object",
            "Save an object"
    );
    public static final TextKey COMMAND_OBJECT_PARAM_DIMENSION_STORE_OBJECT = TextKey.of(
            "iris.director.commandobject.param.dimension_store_object",
            "The dimension to store the object in"
    );
    public static final TextKey COMMAND_OBJECT_PARAM_FILE_STORE_IT_CAN_USE_SUBFOLDERS = TextKey.of(
            "iris.director.commandobject.param.file_store_it_can_use_subfolders",
            "The file to store it in, can use / for subfolders"
    );
    public static final TextKey COMMAND_OBJECT_PARAM_OVERWRITE_EXISTING_OBJECT_FILES = TextKey.of(
            "iris.director.commandobject.param.overwrite_existing_object_files",
            "Overwrite existing object files"
    );
    public static final TextKey COMMAND_OBJECT_PARAM_USE_LEGACY_TILESTATE_SERIALIZATION_IF_POSSIBLE = TextKey.of(
            "iris.director.commandobject.param.use_legacy_tilestate_serialization_if_possible",
            "Use legacy TileState serialization if possible"
    );
    public static final TextKey COMMAND_OBJECT_DIRECTOR_SHIFT_SELECTION_YOUR_LOOKING_DIRECTION = TextKey.of(
            "iris.director.commandobject.director.shift_selection_your_looking_direction",
            "Shift a selection in your looking direction"
    );
    public static final TextKey COMMAND_OBJECT_PARAM_AMOUNT_SHIFT_BY = TextKey.of(
            "iris.director.commandobject.param.amount_shift_by",
            "The amount to shift by"
    );
    public static final TextKey COMMAND_OBJECT_DIRECTOR_UNDO_NUMBER_PASTES = TextKey.of(
            "iris.director.commandobject.director.undo_number_pastes",
            "Undo a number of pastes"
    );
    public static final TextKey COMMAND_OBJECT_PARAM_AMOUNT_PASTES_UNDO = TextKey.of(
            "iris.director.commandobject.param.amount_pastes_undo",
            "The amount of pastes to undo"
    );
    public static final TextKey COMMAND_OBJECT_DIRECTOR_GETS_OBJECT_WAND_GRABS_CURRENT_WORLDEDIT_SELECTION = TextKey.of(
            "iris.director.commandobject.director.gets_object_wand_grabs_current_worldedit_selection",
            "Gets an object wand and grabs the current WorldEdit selection."
    );
    public static final TextKey COMMAND_OBJECT_DIRECTOR_GET_OBJECT_WAND = TextKey.of(
            "iris.director.commandobject.director.get_object_wand",
            "Get an object wand"
    );
    public static final TextKey COMMAND_OBJECT_DIRECTOR_AUTOSELECT_UP_DOWN_OUT = TextKey.of(
            "iris.director.commandobject.director.autoselect_up_down_out",
            "Autoselect up, down & out"
    );
    public static final TextKey COMMAND_OBJECT_DIRECTOR_AUTOSELECT_UP_OUT = TextKey.of(
            "iris.director.commandobject.director.autoselect_up_out",
            "Autoselect up & out"
    );
    public static final TextKey COMMAND_PACK_DIRECTOR_PACK_VALIDATION_MAINTENANCE = TextKey.of(
            "iris.director.commandpack.director.pack_validation_maintenance",
            "Pack validation and maintenance"
    );
    public static final TextKey COMMAND_PACK_DIRECTOR_VALIDATE_PACK_ALL_PACKS_RE_PUBLISH_RESULTS = TextKey.of(
            "iris.director.commandpack.director.validate_pack_all_packs_re_publish_results",
            "Validate a pack (or all packs) and re-publish results"
    );
    public static final TextKey COMMAND_PACK_PARAM_PACK_FOLDER_NAME_VALIDATE_LEAVE_EMPTY_ALL = TextKey.of(
            "iris.director.commandpack.param.pack_folder_name_validate_leave_empty_all",
            "The pack folder name to validate (leave empty for all)"
    );
    public static final TextKey COMMAND_PACK_DIRECTOR_PREVIEW_APPLY_UNUSED_RESOURCE_CLEANUP = TextKey.of(
            "iris.director.commandpack.director.preview_apply_unused_resource_cleanup",
            "Preview or apply unused-resource cleanup"
    );
    public static final TextKey COMMAND_PACK_PARAM_PACK_FOLDER_NAME_CLEAN = TextKey.of(
            "iris.director.commandpack.param.pack_folder_name_clean",
            "The pack folder name to clean"
    );
    public static final TextKey COMMAND_PACK_PARAM_PREVIEW_APPLY = TextKey.of(
            "iris.director.commandpack.param.preview_apply",
            "preview or apply"
    );
    public static final TextKey COMMAND_PACK_DIRECTOR_PREVIEW_APPLY_RESTORATION_LATEST_QUARANTINE = TextKey.of(
            "iris.director.commandpack.director.preview_apply_restoration_latest_quarantine",
            "Preview or apply restoration of the latest quarantine"
    );
    public static final TextKey COMMAND_PACK_PARAM_PACK_FOLDER_NAME_RESTORE = TextKey.of(
            "iris.director.commandpack.param.pack_folder_name_restore",
            "The pack folder name to restore"
    );
    public static final TextKey COMMAND_PACK_PARAM_PREVIEW_APPLY_2 = TextKey.of(
            "iris.director.commandpack.param.preview_apply_2",
            "preview or apply"
    );
    public static final TextKey COMMAND_PACK_DIRECTOR_SHOW_CACHED_VALIDATION_STATUS_PACK = TextKey.of(
            "iris.director.commandpack.director.show_cached_validation_status_pack",
            "Show cached validation status for a pack"
    );
    public static final TextKey COMMAND_PACK_PARAM_PACK_FOLDER_NAME = TextKey.of(
            "iris.director.commandpack.param.pack_folder_name",
            "The pack folder name"
    );
    public static final TextKey COMMAND_PACK_DIRECTOR_SHOW_CONTENT_CANNOT_GENERATE_THIS_MINECRAFT_VERSION = TextKey.of(
            "iris.director.commandpack.director.show_content_cannot_generate_this_minecraft_version",
            "Show pack content that cannot generate on this Minecraft version"
    );
    public static final TextKey COMMAND_PACK_PARAM_PACK_FOLDER_NAME_COMPAT = TextKey.of(
            "iris.director.commandpack.param.pack_folder_name_compat",
            "The pack folder name to report on"
    );
    public static final TextKey COMMAND_PREGEN_DIRECTOR_PREGENERATE_YOUR_IRIS_WORLDS = TextKey.of(
            "iris.director.commandpregen.director.pregenerate_your_iris_worlds",
            "Pregenerate your Iris worlds!"
    );
    public static final TextKey COMMAND_PREGEN_DIRECTOR_PREGENERATE_WORLD = TextKey.of(
            "iris.director.commandpregen.director.pregenerate_world",
            "Pregenerate a world"
    );
    public static final TextKey COMMAND_PREGEN_PARAM_RADIUS_PREGEN_BLOCKS = TextKey.of(
            "iris.director.commandpregen.param.radius_pregen_blocks",
            "The radius of the pregen in blocks"
    );
    public static final TextKey COMMAND_PREGEN_PARAM_WORLD_PREGEN = TextKey.of(
            "iris.director.commandpregen.param.world_pregen",
            "The world to pregen"
    );
    public static final TextKey COMMAND_PREGEN_PARAM_CENTER_LOCATION_PREGEN_USE_ME_YOUR_CURRENT_LOCATION = TextKey.of(
            "iris.director.commandpregen.param.center_location_pregen_use_me_your_current_location",
            "The center location of the pregen. Use \"me\" for your current location"
    );
    public static final TextKey COMMAND_PREGEN_PARAM_OPEN_IRIS_PREGEN_GUI = TextKey.of(
            "iris.director.commandpregen.param.open_iris_pregen_gui",
            "Open the Iris pregen gui"
    );
    public static final TextKey COMMAND_PREGEN_PARAM_GENERATE_ONLY_ONE_CHUNK_AT_TIME = TextKey.of(
            "iris.director.commandpregen.param.generate_only_one_chunk_at_time",
            "Generate only one chunk at a time"
    );
    public static final TextKey COMMAND_PREGEN_DIRECTOR_STOP_ACTIVE_PREGENERATION_TASK = TextKey.of(
            "iris.director.commandpregen.director.stop_active_pregeneration_task",
            "Stop the active pregeneration task"
    );
    public static final TextKey COMMAND_PREGEN_DIRECTOR_PAUSE_CONTINUE_ACTIVE_PREGENERATION_TASK = TextKey.of(
            "iris.director.commandpregen.director.pause_continue_active_pregeneration_task",
            "Pause / continue the active pregeneration task"
    );
    public static final TextKey COMMAND_PREGEN_DIRECTOR_SHOW_ACTIVE_PREGENERATION_STATUS = TextKey.of(
            "iris.director.commandpregen.director.show_active_pregeneration_status",
            "Show the active pregeneration status"
    );
    public static final TextKey COMMAND_STRUCTURE_DIRECTOR_IRIS_STRUCTURE_TOOLS_INDEX_IMPORT_INFO = TextKey.of(
            "iris.director.commandstructure.director.iris_structure_tools_index_import_info",
            "Iris structure tools (index, import, info)"
    );
    public static final TextKey COMMAND_STRUCTURE_DIRECTOR_REGENERATE_STRUCTURE_INDEX_JSON_LISTING_ALL_VANILLA_DATAPACK_IRIS_STRUCTURES = TextKey.of(
            "iris.director.commandstructure.director.regenerate_structure_index_json_listing_all_vanilla_datapack_iris_structures",
            "Regenerate structure-index.json listing all vanilla, datapack & iris structures"
    );
    public static final TextKey COMMAND_STRUCTURE_PARAM_DIMENSION_WHOSE_PACK_INDEX = TextKey.of(
            "iris.director.commandstructure.param.dimension_whose_pack_index",
            "The dimension whose pack to index"
    );
    public static final TextKey COMMAND_STRUCTURE_DIRECTOR_IMPORT_EVERY_STRUCTURE_VANILLA_INGESTED_DATAPACKS_INTO_THIS_PACK_AS_EDITABLE_IRIS = TextKey.of(
            "iris.director.commandstructure.director.import_every_structure_vanilla_ingested_datapacks_into_this_pack_as_editable_iris",
            "Import EVERY structure - vanilla AND ingested datapacks - into this pack as editable Iris resources, always overwriting. Rebuilds jigsaw structures (villages, outposts, datapack jigsaws) as editable pool/piece graphs, imports every structure template NBT as an object, and assembles the multi-template structures (shipwrecks, ruined portals, ocean ruins, nether fossils). Run after ingesting a datapack and restarting. Regenerate chunks or use a fresh world for the imported copies to place."
    );
    public static final TextKey COMMAND_STRUCTURE_PARAM_DIMENSION_WHOSE_PACK_IMPORT_INTO = TextKey.of(
            "iris.director.commandstructure.param.dimension_whose_pack_import_into",
            "The dimension whose pack to import into"
    );
    public static final TextKey COMMAND_STRUCTURE_DIRECTOR_CAPTURE_CODE_GENERATED_STRUCTURES_THAT_HAVE_NO_NBT_TEMPLATE_SWAMP_HUTS_IGLOOS = TextKey.of(
            "iris.director.commandstructure.director.capture_code_generated_structures_that_have_no_nbt_template_swamp_huts_igloos",
            "Capture code-generated structures that have no NBT template (swamp huts, igloos, etc.) into editable Iris objects by generating each one in a throwaway scratch world and reading back its blocks. Skips structures that already import as a structure, structures wider/taller than the capture cap (strongholds, mansions, monuments stay vanilla), and anything that will not generate in a flat overworld. Each captured structure becomes a single-piece Iris structure you can place from a biome/region/dimension 'structures' list. Runs automatically as the last pass of /iris structure import."
    );
    public static final TextKey COMMAND_STRUCTURE_PARAM_DIMENSION_WHOSE_PACK_CAPTURE_INTO = TextKey.of(
            "iris.director.commandstructure.param.dimension_whose_pack_capture_into",
            "The dimension whose pack to capture into"
    );
    public static final TextKey COMMAND_STRUCTURE_DIRECTOR_VERIFY_NATIVE_STRUCTURE_ELIGIBILITY_LOCATE_IRIS_PLACED_STRUCTURES_WITHOUT_RUNNING_BLOCKING_NATIVE = TextKey.of(
            "iris.director.commandstructure.director.verify_native_structure_eligibility_locate_iris_placed_structures_without_running_blocking_native",
            "Verify native structure eligibility and locate Iris-placed structures without running blocking native searches."
    );
    public static final TextKey COMMAND_STRUCTURE_PARAM_DIMENSION_VERIFY = TextKey.of(
            "iris.director.commandstructure.param.dimension_verify",
            "The dimension to verify"
    );
    public static final TextKey COMMAND_STRUCTURE_PARAM_SEARCH_RADIUS_CHUNKS_AROUND_ORIGIN_LARGER_IS_MUCH_SLOWER = TextKey.of(
            "iris.director.commandstructure.param.search_radius_chunks_around_origin_larger_is_much_slower",
            "Search radius in chunks around the origin (larger is much slower)"
    );
    public static final TextKey COMMAND_STRUCTURE_DIRECTOR_RESOLVE_IRIS_STRUCTURE_S_JIGSAW_GRAPH_REPORT_PIECE_COUNT_BOUNDS = TextKey.of(
            "iris.director.commandstructure.director.resolve_iris_structure_s_jigsaw_graph_report_piece_count_bounds",
            "Resolve an iris structure's jigsaw graph and report piece count & bounds"
    );
    public static final TextKey COMMAND_STRUCTURE_PARAM_DIMENSION_WHOSE_PACK_HOLDS_STRUCTURE = TextKey.of(
            "iris.director.commandstructure.param.dimension_whose_pack_holds_structure",
            "The dimension whose pack holds the structure"
    );
    public static final TextKey COMMAND_STRUCTURE_PARAM_IRIS_STRUCTURE_KEY_INSPECT = TextKey.of(
            "iris.director.commandstructure.param.iris_structure_key_inspect",
            "The iris structure key to inspect"
    );
    public static final TextKey COMMAND_STRUCTURE_DIRECTOR_ASSEMBLE_PLACE_IRIS_STRUCTURE_AT_YOUR_LOCATION_STUDIO_TESTING = TextKey.of(
            "iris.director.commandstructure.director.assemble_place_iris_structure_at_your_location_studio_testing",
            "Assemble and place an iris structure at your location (studio testing)"
    );
    public static final TextKey COMMAND_STRUCTURE_PARAM_DIMENSION_WHOSE_PACK_HOLDS_STRUCTURE_2 = TextKey.of(
            "iris.director.commandstructure.param.dimension_whose_pack_holds_structure_2",
            "The dimension whose pack holds the structure"
    );
    public static final TextKey COMMAND_STRUCTURE_PARAM_IRIS_STRUCTURE_KEY_PLACE = TextKey.of(
            "iris.director.commandstructure.param.iris_structure_key_place",
            "The iris structure key to place"
    );
    public static final TextKey COMMAND_STUDIO_DIRECTOR_STUDIO_COMMANDS = TextKey.of(
            "iris.director.commandstudio.director.studio_commands",
            "Studio Commands"
    );
    public static final TextKey COMMAND_STUDIO_DIRECTOR_OPEN_NEW_STUDIO_WORLD = TextKey.of(
            "iris.director.commandstudio.director.open_new_studio_world",
            "Open a new studio world"
    );
    public static final TextKey COMMAND_STUDIO_PARAM_DIMENSION_PACK_OPEN_STUDIO = TextKey.of(
            "iris.director.commandstudio.param.dimension_pack_open_studio",
            "The dimension pack to open a studio for"
    );
    public static final TextKey COMMAND_STUDIO_PARAM_SEED_GENERATE_STUDIO_WITH = TextKey.of(
            "iris.director.commandstudio.param.seed_generate_studio_with",
            "The seed to generate the studio with"
    );
    public static final TextKey COMMAND_STUDIO_DIRECTOR_IMPORT_VANILLA_TREES_MUSHROOMS_OBJECTS_STRUCTURES_JIGSAW_FROM_SERVER_INTO_PACK_S = TextKey.of(
            "iris.director.commandstudio.director.import_vanilla_trees_mushrooms_objects_structures_jigsaw_from_server_into_pack_s",
            "Import vanilla trees, mushrooms & objects (and structures/jigsaw) from the server into a pack's objects/vanilla folder"
    );
    public static final TextKey COMMAND_STUDIO_PARAM_DIMENSION_PACK_IMPORT_VANILLA_CONTENT_INTO = TextKey.of(
            "iris.director.commandstudio.param.dimension_pack_import_vanilla_content_into",
            "The dimension pack to import vanilla content into"
    );
    public static final TextKey COMMAND_STUDIO_PARAM_HOW_MANY_VARIANTS_CAPTURE_PER_TREE_OBJECT_FEATURE = TextKey.of(
            "iris.director.commandstudio.param.how_many_variants_capture_per_tree_object_feature",
            "How many variants to capture per tree/object feature"
    );
    public static final TextKey COMMAND_STUDIO_PARAM_ALSO_IMPORT_VANILLA_DATAPACK_STRUCTURES_JIGSAW_INTO_PACK = TextKey.of(
            "iris.director.commandstudio.param.also_import_vanilla_datapack_structures_jigsaw_into_pack",
            "Also import vanilla & datapack structures/jigsaw into the pack"
    );
    public static final TextKey COMMAND_STUDIO_DIRECTOR_OPEN_VSCODE_DIMENSION = TextKey.of(
            "iris.director.commandstudio.director.open_vscode_dimension",
            "Open VSCode for a dimension"
    );
    public static final TextKey COMMAND_STUDIO_PARAM_DIMENSION_OPEN_VSCODE = TextKey.of(
            "iris.director.commandstudio.param.dimension_open_vscode",
            "The dimension to open VSCode for"
    );
    public static final TextKey COMMAND_STUDIO_DIRECTOR_CLOSE_OPEN_STUDIO_PROJECT = TextKey.of(
            "iris.director.commandstudio.director.close_open_studio_project",
            "Close an open studio project"
    );
    public static final TextKey COMMAND_STUDIO_DIRECTOR_TOGGLE_YOUR_STUDIO_DEBUG_SCOREBOARD = TextKey.of(
            "iris.director.commandstudio.director.toggle_your_studio_debug_scoreboard",
            "Toggle your Studio debug scoreboard"
    );
    public static final TextKey COMMAND_STUDIO_DIRECTOR_CREATE_NEW_STUDIO_PROJECT = TextKey.of(
            "iris.director.commandstudio.director.create_new_studio_project",
            "Create a new studio project"
    );
    public static final TextKey COMMAND_STUDIO_PARAM_NAME_THIS_NEW_IRIS_PROJECT = TextKey.of(
            "iris.director.commandstudio.param.name_this_new_iris_project",
            "The name of this new Iris Project."
    );
    public static final TextKey COMMAND_STUDIO_PARAM_COPY_CONTENTS_EXISTING_PROJECT_YOUR_PACKS_FOLDER_USE_IT_AS_TEMPLATE_THIS = TextKey.of(
            "iris.director.commandstudio.param.copy_contents_existing_project_your_packs_folder_use_it_as_template_this",
            "Copy the contents of an existing project in your packs folder and use it as a template in this new project."
    );
    public static final TextKey COMMAND_STUDIO_DIRECTOR_GET_VERSION_PACK = TextKey.of(
            "iris.director.commandstudio.director.get_version_pack",
            "Get the version of a pack"
    );
    public static final TextKey COMMAND_STUDIO_PARAM_DIMENSION_GET_VERSION = TextKey.of(
            "iris.director.commandstudio.param.dimension_get_version",
            "The dimension get the version of"
    );
    public static final TextKey COMMAND_STUDIO_DIRECTOR_OPEN_NOISE_EXPLORER_EXTERNAL_GUI = TextKey.of(
            "iris.director.commandstudio.director.open_noise_explorer_external_gui",
            "Open the noise explorer (External GUI)"
    );
    public static final TextKey COMMAND_STUDIO_PARAM_OPTIONAL_PACK_GENERATOR_PREVIEW = TextKey.of(
            "iris.director.commandstudio.param.optional_pack_generator_preview",
            "Optional pack generator to preview"
    );
    public static final TextKey COMMAND_STUDIO_PARAM_SEED_PREVIEW_GENERATOR_WITH = TextKey.of(
            "iris.director.commandstudio.param.seed_preview_generator_with",
            "The seed to preview the generator with"
    );
    public static final TextKey COMMAND_STUDIO_DIRECTOR_SHOW_LOOT_IF_CHEST_WERE_RIGHT_HERE = TextKey.of(
            "iris.director.commandstudio.director.show_loot_if_chest_were_right_here",
            "Show loot if a chest were right here"
    );
    public static final TextKey COMMAND_STUDIO_PARAM_FAST_INSERTION_ITEMS_VIRTUAL_INVENTORY_MAY_CAUSE_PERFORMANCE_DROP = TextKey.of(
            "iris.director.commandstudio.param.fast_insertion_items_virtual_inventory_may_cause_performance_drop",
            "Fast insertion of items in virtual inventory (may cause performance drop)"
    );
    public static final TextKey COMMAND_STUDIO_PARAM_WHETHER_NOT_APPEND_INVENTORY_CURRENTLY_OPEN_IF_FALSE_CLEARS_OPENED_INVENTORY = TextKey.of(
            "iris.director.commandstudio.param.whether_not_append_inventory_currently_open_if_false_clears_opened_inventory",
            "Whether or not to append to the inventory currently open (if false, clears opened inventory)"
    );
    public static final TextKey COMMAND_STUDIO_DIRECTOR_CALCULATE_CHANCE_EACH_REGION_GENERATE = TextKey.of(
            "iris.director.commandstudio.director.calculate_chance_each_region_generate",
            "Calculate the chance for each region to generate"
    );
    public static final TextKey COMMAND_STUDIO_PARAM_RADIUS_CHUNKS = TextKey.of(
            "iris.director.commandstudio.param.radius_chunks",
            "The radius in chunks"
    );
    public static final TextKey COMMAND_STUDIO_DIRECTOR_RENDER_WORLD_MAP_EXTERNAL_GUI = TextKey.of(
            "iris.director.commandstudio.director.render_world_map_external_gui",
            "Render a world map (External GUI)"
    );
    public static final TextKey COMMAND_STUDIO_PARAM_WORLD_OPEN_GENERATOR = TextKey.of(
            "iris.director.commandstudio.param.world_open_generator",
            "The world to open the generator for"
    );
    public static final TextKey COMMAND_STUDIO_DIRECTOR_PACKAGE_DIMENSION_INTO_COMPRESSED_FORMAT = TextKey.of(
            "iris.director.commandstudio.director.package_dimension_into_compressed_format",
            "Package a dimension into a compressed format"
    );
    public static final TextKey COMMAND_STUDIO_PARAM_DIMENSION_PACK_COMPRESS = TextKey.of(
            "iris.director.commandstudio.param.dimension_pack_compress",
            "The dimension pack to compress"
    );
    public static final TextKey COMMAND_STUDIO_PARAM_WHETHER_NOT_OBFUSCATE_PACK = TextKey.of(
            "iris.director.commandstudio.param.whether_not_obfuscate_pack",
            "Whether or not to obfuscate the pack"
    );
    public static final TextKey COMMAND_STUDIO_PARAM_WHETHER_NOT_MINIFY_PACK = TextKey.of(
            "iris.director.commandstudio.param.whether_not_minify_pack",
            "Whether or not to minify the pack"
    );
    public static final TextKey COMMAND_STUDIO_DIRECTOR_PROFILES_PERFORMANCE_DIMENSION = TextKey.of(
            "iris.director.commandstudio.director.profiles_performance_dimension",
            "Profiles the performance of a dimension"
    );
    public static final TextKey COMMAND_STUDIO_PARAM_DIMENSION_PROFILE = TextKey.of(
            "iris.director.commandstudio.param.dimension_profile",
            "The dimension to profile"
    );
    public static final TextKey COMMAND_STUDIO_DIRECTOR_SPAWN_IRIS_ENTITY = TextKey.of(
            "iris.director.commandstudio.director.spawn_iris_entity",
            "Spawn an Iris entity"
    );
    public static final TextKey COMMAND_STUDIO_PARAM_ENTITY_SPAWN = TextKey.of(
            "iris.director.commandstudio.param.entity_spawn",
            "The entity to spawn"
    );
    public static final TextKey COMMAND_STUDIO_PARAM_LOCATION_SPAWN_ENTITY_AT = TextKey.of(
            "iris.director.commandstudio.param.location_spawn_entity_at",
            "The location to spawn the entity at"
    );
    public static final TextKey COMMAND_STUDIO_DIRECTOR_TELEPORT_ACTIVE_STUDIO_WORLD = TextKey.of(
            "iris.director.commandstudio.director.teleport_active_studio_world",
            "Teleport to the active studio world"
    );
    public static final TextKey COMMAND_STUDIO_DIRECTOR_UPDATE_YOUR_DIMENSION_PROJECTS_VSCODE_WORKSPACE = TextKey.of(
            "iris.director.commandstudio.director.update_your_dimension_projects_vscode_workspace",
            "Update your dimension projects VSCode workspace"
    );
    public static final TextKey COMMAND_STUDIO_PARAM_DIMENSION_UPDATE_WORKSPACE = TextKey.of(
            "iris.director.commandstudio.param.dimension_update_workspace",
            "The dimension to update the workspace of"
    );
    public static final TextKey COMMAND_STUDIO_DIRECTOR_CAPTURE_IGENDATA_CHUNK_REPORT_NEARBY_CHUNKS = TextKey.of(
            "iris.director.commandstudio.director.capture_igendata_chunk_report_nearby_chunks",
            "Capture an IGenData chunk report for nearby chunks"
    );
    public static final TextKey COMMAND_WHAT_DIRECTOR_IRIS_WHAT = TextKey.of(
            "iris.director.commandwhat.director.iris_what",
            "Iris What?"
    );
    public static final TextKey COMMAND_WHAT_DIRECTOR_WHAT_IS_MY_HAND = TextKey.of(
            "iris.director.commandwhat.director.what_is_my_hand",
            "What is in my hand?"
    );
    public static final TextKey COMMAND_WHAT_DIRECTOR_WHAT_BIOME_AM_I = TextKey.of(
            "iris.director.commandwhat.director.what_biome_am_i",
            "What biome am i in?"
    );
    public static final TextKey COMMAND_WHAT_DIRECTOR_WHAT_REGION_AM_I = TextKey.of(
            "iris.director.commandwhat.director.what_region_am_i",
            "What region am i in?"
    );
    public static final TextKey COMMAND_WHAT_DIRECTOR_WHAT_BLOCK_AM_I_LOOKING_AT = TextKey.of(
            "iris.director.commandwhat.director.what_block_am_i_looking_at",
            "What block am i looking at?"
    );
    public static final TextKey COMMAND_WHAT_DIRECTOR_SHOW_MARKERS_CHUNK = TextKey.of(
            "iris.director.commandwhat.director.show_markers_chunk",
            "Show markers in chunk"
    );
    public static final TextKey COMMAND_WHAT_PARAM_MARKER_NAME_SUCH_AS_CAVE_FLOOR_CAVE_CEILING = TextKey.of(
            "iris.director.commandwhat.param.marker_name_such_as_cave_floor_cave_ceiling",
            "Marker name such as cave_floor or cave_ceiling"
    );

    private static final List<MessageKey> KEYS = List.of(
        DEBUG_DESCRIPTION,
        DEBUG_DUMP_DESCRIPTION,
        DEBUG_DUMP_UPLOAD,
            COMMAND_DATAPACK_DIRECTOR_DOWNLOAD_MANAGE_EXTERNAL_DATAPACK_IMPORTS_MODRINTH,
            COMMAND_DATAPACK_DIRECTOR_DOWNLOAD_UPDATE_EVERY_DATAPACK_LISTED_PACK_DIMENSION_S_DATAPACKIMPORTS_INSTALL_IT_INTO,
            COMMAND_DATAPACK_PARAM_RESTART_SERVER_WHEN_NEW_DATAPACKS_ARE_INSTALLED_REQUIRED_NEW_STRUCTURES_REGISTER_GENERATE,
            COMMAND_DATAPACK_DIRECTOR_LIST_CONFIGURED_DATAPACK_IMPORTS_THEIR_INSTALLED_VERSIONS,
            COMMAND_DATAPACK_DIRECTOR_REMOVE_INSTALLED_DATAPACK_BY_ID_ALSO_DELETE_ITS_URL_FROM_DATAPACKIMPORTS_KEEP,
            COMMAND_DATAPACK_PARAM_DATAPACK_ID_FOLDER_NAME_SHOWN_BY_IRIS_DATAPACK_LIST,
            COMMAND_DEVELOPER_DIRECTOR_IRIS_WORLD_MANAGER,
            COMMAND_DEVELOPER_DIRECTOR_GET_LOADED_TECTONICPLATES_COUNT,
            COMMAND_DEVELOPER_DIRECTOR_HASH_GENERATED_BLOCK_OUTPUT_FIXED_AREA_DETERMINISM_IDENTITY_TESTING,
            COMMAND_DEVELOPER_PARAM_WORLD_HASH,
            COMMAND_DEVELOPER_PARAM_RADIUS_CHUNKS_AROUND_CENTER,
            COMMAND_DEVELOPER_PARAM_CENTER_CHUNK_X,
            COMMAND_DEVELOPER_PARAM_CENTER_CHUNK_Z,
            COMMAND_DEVELOPER_DIRECTOR_STAGE_WORLD_GENERATION_UPDATE,
            COMMAND_DEVELOPER_PARAM_WORLD_UPDATE,
            COMMAND_DEVELOPER_PARAM_PACK_INSTALL_INTO_WORLD,
            COMMAND_DEVELOPER_PARAM_MAKE_SURE_MAKE_BACKUP_READ_WARNINGS_FIRST,
            COMMAND_DEVELOPER_DIRECTOR_TEST,
            COMMAND_DEVELOPER_PARAM_DUMP_WHOLE_TECTONIC_PLATE_INSTEAD_SINGLE_SECTION,
            COMMAND_DEVELOPER_PARAM_DUMP_FILE_ID_UNDER_PLUGINS_IRIS_DUMP_PV_ID,
            COMMAND_DEVELOPER_DIRECTOR_TEST_2,
            COMMAND_DEVELOPER_PARAM_PACK_BENCH,
            COMMAND_DEVELOPER_PARAM_RADIUS_REGIONS,
            COMMAND_DEVELOPER_PARAM_OPEN_GUI_WHILE_BENCHMARKING,
            COMMAND_DEVELOPER_DIRECTOR_UPGRADE_ANOTHER_MINECRAFT_VERSION,
            COMMAND_DEVELOPER_PARAM_VERSION_UPGRADE,
            COMMAND_DEVELOPER_DIRECTOR_TEST_3,
            COMMAND_DEVELOPER_PARAM_WORLD_FOLDER_SCAN_MCA_REGION_FILES,
            COMMAND_DEVELOPER_DIRECTOR_DELETE_NEARBY_CHUNK_BLOCKS_REGEN_TESTING,
            COMMAND_DEVELOPER_PARAM_RADIUS_CHUNKS_AROUND_YOUR_CURRENT_CHUNK,
            COMMAND_DEVELOPER_DIRECTOR_TEST_4,
            COMMAND_DEVELOPER_DIRECTOR_DELETE_REGENERATE_NEARBY_CHUNKS_PLACE_USING_IRIS_GENERATION,
            COMMAND_DEVELOPER_PARAM_RADIUS_NEARBY_CHUNKS,
            COMMAND_DEVELOPER_DIRECTOR_GENERATE_CHUNKS_INTO_BUFFERS_NO_WORLD_WRITES_HASH_BLOCKS_BIOMES_CAPTURES_GOLDEN,
            COMMAND_DEVELOPER_PARAM_WORLD_SCAN,
            COMMAND_DEVELOPER_PARAM_RADIUS_CHUNKS_AROUND_CENTER_2,
            COMMAND_DEVELOPER_PARAM_CENTER_CHUNK_X_2,
            COMMAND_DEVELOPER_PARAM_CENTER_CHUNK_Z_2,
            COMMAND_DEVELOPER_PARAM_DELETE_MANTLE_DATA_SCAN_AREA_FIRST_FULL_REGENERATION_FROM_SCRATCH,
            COMMAND_DEVELOPER_PARAM_CONCURRENT_CHUNK_GENERATIONS_1_STRICTLY_SERIAL_ORDER_DEPENDENCE_TESTING,
            COMMAND_DEVELOPER_PARAM_ALSO_DUMP_FULL_PER_CHUNK_NON_AIR_BLOCKSTATES_OFFLINE_DIFFING,
            COMMAND_EDIT_DIRECTOR_EDIT_SOMETHING,
            COMMAND_EDIT_DIRECTOR_EDIT_BIOME_YOU_SPECIFIED,
            COMMAND_EDIT_PARAM_BIOME_EDIT,
            COMMAND_EDIT_DIRECTOR_EDIT_REGION_YOU_SPECIFIED,
            COMMAND_EDIT_PARAM_REGION_EDIT,
            COMMAND_EDIT_DIRECTOR_EDIT_DIMENSION_YOU_SPECIFIED,
            COMMAND_EDIT_PARAM_DIMENSION_EDIT,
            COMMAND_FIND_DIRECTOR_IRIS_FIND_COMMANDS,
            COMMAND_FIND_DIRECTOR_FIND_BIOME,
            COMMAND_FIND_PARAM_BIOME_LOOK,
            COMMAND_FIND_PARAM_SHOULD_YOU_BE_TELEPORTED,
            COMMAND_FIND_DIRECTOR_FIND_REGION,
            COMMAND_FIND_PARAM_REGION_LOOK,
            COMMAND_FIND_PARAM_SHOULD_YOU_BE_TELEPORTED_2,
            COMMAND_FIND_DIRECTOR_FIND_POINT_INTEREST,
            COMMAND_FIND_PARAM_TYPE_POI_LOOK,
            COMMAND_FIND_PARAM_SHOULD_YOU_BE_TELEPORTED_3,
            COMMAND_FIND_DIRECTOR_FIND_STRUCTURE_VANILLA_KEY_LIKE_MINECRAFT_VILLAGE_PLAINS_MINECRAFT_STRONGHOLD_IMPORTED_IRIS,
            COMMAND_FIND_PARAM_STRUCTURE_LOOK_E_G_MINECRAFT_VILLAGE_PLAINS_MINECRAFT_STRONGHOLD_MINECRAFT_ANCIENT_CITY,
            COMMAND_FIND_DIRECTOR_FIND_OBJECT,
            COMMAND_FIND_PARAM_OBJECT_LOOK,
            COMMAND_FIND_PARAM_SHOULD_YOU_BE_TELEPORTED_4,
            COMMAND_IRIS_DIRECTOR_BASIC_COMMAND,
            COMMAND_IRIS_DIRECTOR_CREATE_NEW_WORLD,
            COMMAND_IRIS_PARAM_NAME_WORLD_CREATE,
            COMMAND_IRIS_PARAM_DIMENSION_PACK_CREATE_WORLD_WITH,
            COMMAND_IRIS_PARAM_SEED_GENERATE_WORLD_WITH,
            COMMAND_IRIS_PARAM_REPLACE_EXACT_EXISTING_WORLD_SLOT_NEXT_RESTART,
            COMMAND_IRIS_DIRECTOR_TELEPORT_ANOTHER_WORLD,
            COMMAND_IRIS_PARAM_WORLD_TELEPORT,
            COMMAND_IRIS_PARAM_PLAYER_TELEPORT,
            COMMAND_IRIS_DIRECTOR_PRINT_VERSION_INFORMATION,
            COMMAND_IRIS_DIRECTOR_BENCHMARK_PACK,
            COMMAND_IRIS_PARAM_DIMENSION_BENCHMARK,
            COMMAND_IRIS_DIRECTOR_PRINT_WORLD_HEIGHT_INFORMATION,
            COMMAND_IRIS_DIRECTOR_CHECK_ACCESS_ALL_WORLDS,
            COMMAND_IRIS_DIRECTOR_REMOVE_IRIS_WORLD,
            COMMAND_IRIS_PARAM_WORLD_REMOVE,
            COMMAND_IRIS_PARAM_WHETHER_ALSO_REMOVE_FOLDER_IF_SET_FALSE_JUST_DOES_NOT_LOAD_WORLD,
            COMMAND_IRIS_DIRECTOR_TOGGLE_DEBUG,
            COMMAND_IRIS_DIRECTOR_DOWNLOAD_PROJECT,
            COMMAND_IRIS_PARAM_PACK_DOWNLOAD,
            COMMAND_IRIS_DIRECTOR_GET_METRICS_YOUR_WORLD,
            COMMAND_IRIS_DIRECTOR_RELOAD_CONFIGURATION_FILE_THIS_IS_ALSO_DONE_AUTOMATICALLY,
            COMMAND_IRIS_DIRECTOR_UNLOAD_IRIS_WORLD,
            COMMAND_IRIS_PARAM_WORLD_UNLOAD,
            COMMAND_IRIS_DIRECTOR_LOAD_IRIS_WORLD,
            COMMAND_IRIS_PARAM_NAME_WORLD_LOAD,
            COMMAND_IRIS_DIRECTOR_EVACUATE_IRIS_WORLD,
            COMMAND_IRIS_PARAM_EVACUATE_WORLD,
            COMMAND_OBJECT_DIRECTOR_IRIS_OBJECT_MANIPULATION,
            COMMAND_OBJECT_DIRECTOR_OPEN_OBJECT_STUDIO_WORLD_GRID_EVERY_OBJECT_DIMENSION_OPTIONAL_DEFAULTS_ALL_PACKS,
            COMMAND_OBJECT_PARAM_OPTIONAL_DIMENSION_WHOSE_OBJECT_PACK_LAY_OUT_OMIT_AGGREGATE_OBJECTS_FROM_EVERY,
            COMMAND_OBJECT_PARAM_SEED_GENERATE_STUDIO_WITH,
            COMMAND_OBJECT_DIRECTOR_CHECK_COMPOSITION_OBJECT,
            COMMAND_OBJECT_PARAM_OBJECT_ANALYZE,
            COMMAND_OBJECT_DIRECTOR_SHRINK_OBJECT_ITS_MINIMUM_SIZE,
            COMMAND_OBJECT_PARAM_OBJECT_SHRINK,
            COMMAND_OBJECT_DIRECTOR_GROW_ORGANIC_BRANCHES_THROUGH_CANOPY_SO_EVERY_LEAF_SURVIVES_VANILLA_DECAY,
            COMMAND_OBJECT_PARAM_OBJECT_KEY_PREFIX_TREES_FILESYSTEM_PATH,
            COMMAND_OBJECT_PARAM_DRYRUN_TRUE_ANALYZES_ONLY_WRITES_NOTHING,
            COMMAND_OBJECT_PARAM_REACH_N_MAX_BRANCH_LENGTH_BLOCKS_FROM_EXISTING_WOOD_FARTHER_LEAF_CLUSTERS,
            COMMAND_OBJECT_DIRECTOR_CONVERT_SCHEM_FILES_CONVERT_FOLDER_IOB_FILES,
            COMMAND_OBJECT_DIRECTOR_GET_POWDER_THAT_REVEALS_OBJECTS,
            COMMAND_OBJECT_DIRECTOR_CONTRACT_SELECTION_BASED_ON_YOUR_LOOKING_DIRECTION,
            COMMAND_OBJECT_PARAM_AMOUNT_INSET_BY,
            COMMAND_OBJECT_DIRECTOR_SET_POINT_1_LOOK,
            COMMAND_OBJECT_PARAM_WHETHER_USE_YOUR_CURRENT_POSITION_WHERE_YOU_LOOK,
            COMMAND_OBJECT_DIRECTOR_SET_POINT_2_LOOK,
            COMMAND_OBJECT_PARAM_WHETHER_USE_YOUR_CURRENT_POSITION_WHERE_YOU_LOOK_2,
            COMMAND_OBJECT_DIRECTOR_PASTE_OBJECT,
            COMMAND_OBJECT_PARAM_OBJECT_PASTE,
            COMMAND_OBJECT_PARAM_WHETHER_NOT_EDIT_OBJECT_NEED_HOLD_WAND,
            COMMAND_OBJECT_PARAM_AMOUNT_DEGREES_ROTATE_BY,
            COMMAND_OBJECT_PARAM_FACTOR_BY_WHICH_SCALE_OBJECT_PLACEMENT,
            COMMAND_OBJECT_PARAM_SCALE_INTERPOLATOR_USE,
            COMMAND_OBJECT_DIRECTOR_SAVE_OBJECT,
            COMMAND_OBJECT_PARAM_DIMENSION_STORE_OBJECT,
            COMMAND_OBJECT_PARAM_FILE_STORE_IT_CAN_USE_SUBFOLDERS,
            COMMAND_OBJECT_PARAM_OVERWRITE_EXISTING_OBJECT_FILES,
            COMMAND_OBJECT_PARAM_USE_LEGACY_TILESTATE_SERIALIZATION_IF_POSSIBLE,
            COMMAND_OBJECT_DIRECTOR_SHIFT_SELECTION_YOUR_LOOKING_DIRECTION,
            COMMAND_OBJECT_PARAM_AMOUNT_SHIFT_BY,
            COMMAND_OBJECT_DIRECTOR_UNDO_NUMBER_PASTES,
            COMMAND_OBJECT_PARAM_AMOUNT_PASTES_UNDO,
            COMMAND_OBJECT_DIRECTOR_GETS_OBJECT_WAND_GRABS_CURRENT_WORLDEDIT_SELECTION,
            COMMAND_OBJECT_DIRECTOR_GET_OBJECT_WAND,
            COMMAND_OBJECT_DIRECTOR_AUTOSELECT_UP_DOWN_OUT,
            COMMAND_OBJECT_DIRECTOR_AUTOSELECT_UP_OUT,
            COMMAND_PACK_DIRECTOR_PACK_VALIDATION_MAINTENANCE,
            COMMAND_PACK_DIRECTOR_VALIDATE_PACK_ALL_PACKS_RE_PUBLISH_RESULTS,
            COMMAND_PACK_PARAM_PACK_FOLDER_NAME_VALIDATE_LEAVE_EMPTY_ALL,
            COMMAND_PACK_DIRECTOR_PREVIEW_APPLY_UNUSED_RESOURCE_CLEANUP,
            COMMAND_PACK_PARAM_PACK_FOLDER_NAME_CLEAN,
            COMMAND_PACK_PARAM_PREVIEW_APPLY,
            COMMAND_PACK_DIRECTOR_PREVIEW_APPLY_RESTORATION_LATEST_QUARANTINE,
            COMMAND_PACK_PARAM_PACK_FOLDER_NAME_RESTORE,
            COMMAND_PACK_PARAM_PREVIEW_APPLY_2,
            COMMAND_PACK_DIRECTOR_SHOW_CACHED_VALIDATION_STATUS_PACK,
            COMMAND_PACK_PARAM_PACK_FOLDER_NAME,
            COMMAND_PACK_DIRECTOR_SHOW_CONTENT_CANNOT_GENERATE_THIS_MINECRAFT_VERSION,
            COMMAND_PACK_PARAM_PACK_FOLDER_NAME_COMPAT,
            COMMAND_PREGEN_DIRECTOR_PREGENERATE_YOUR_IRIS_WORLDS,
            COMMAND_PREGEN_DIRECTOR_PREGENERATE_WORLD,
            COMMAND_PREGEN_PARAM_RADIUS_PREGEN_BLOCKS,
            COMMAND_PREGEN_PARAM_WORLD_PREGEN,
            COMMAND_PREGEN_PARAM_CENTER_LOCATION_PREGEN_USE_ME_YOUR_CURRENT_LOCATION,
            COMMAND_PREGEN_PARAM_OPEN_IRIS_PREGEN_GUI,
            COMMAND_PREGEN_PARAM_GENERATE_ONLY_ONE_CHUNK_AT_TIME,
            COMMAND_PREGEN_DIRECTOR_STOP_ACTIVE_PREGENERATION_TASK,
            COMMAND_PREGEN_DIRECTOR_PAUSE_CONTINUE_ACTIVE_PREGENERATION_TASK,
            COMMAND_PREGEN_DIRECTOR_SHOW_ACTIVE_PREGENERATION_STATUS,
            COMMAND_STRUCTURE_DIRECTOR_IRIS_STRUCTURE_TOOLS_INDEX_IMPORT_INFO,
            COMMAND_STRUCTURE_DIRECTOR_REGENERATE_STRUCTURE_INDEX_JSON_LISTING_ALL_VANILLA_DATAPACK_IRIS_STRUCTURES,
            COMMAND_STRUCTURE_PARAM_DIMENSION_WHOSE_PACK_INDEX,
            COMMAND_STRUCTURE_DIRECTOR_IMPORT_EVERY_STRUCTURE_VANILLA_INGESTED_DATAPACKS_INTO_THIS_PACK_AS_EDITABLE_IRIS,
            COMMAND_STRUCTURE_PARAM_DIMENSION_WHOSE_PACK_IMPORT_INTO,
            COMMAND_STRUCTURE_DIRECTOR_CAPTURE_CODE_GENERATED_STRUCTURES_THAT_HAVE_NO_NBT_TEMPLATE_SWAMP_HUTS_IGLOOS,
            COMMAND_STRUCTURE_PARAM_DIMENSION_WHOSE_PACK_CAPTURE_INTO,
            COMMAND_STRUCTURE_DIRECTOR_VERIFY_NATIVE_STRUCTURE_ELIGIBILITY_LOCATE_IRIS_PLACED_STRUCTURES_WITHOUT_RUNNING_BLOCKING_NATIVE,
            COMMAND_STRUCTURE_PARAM_DIMENSION_VERIFY,
            COMMAND_STRUCTURE_PARAM_SEARCH_RADIUS_CHUNKS_AROUND_ORIGIN_LARGER_IS_MUCH_SLOWER,
            COMMAND_STRUCTURE_DIRECTOR_RESOLVE_IRIS_STRUCTURE_S_JIGSAW_GRAPH_REPORT_PIECE_COUNT_BOUNDS,
            COMMAND_STRUCTURE_PARAM_DIMENSION_WHOSE_PACK_HOLDS_STRUCTURE,
            COMMAND_STRUCTURE_PARAM_IRIS_STRUCTURE_KEY_INSPECT,
            COMMAND_STRUCTURE_DIRECTOR_ASSEMBLE_PLACE_IRIS_STRUCTURE_AT_YOUR_LOCATION_STUDIO_TESTING,
            COMMAND_STRUCTURE_PARAM_DIMENSION_WHOSE_PACK_HOLDS_STRUCTURE_2,
            COMMAND_STRUCTURE_PARAM_IRIS_STRUCTURE_KEY_PLACE,
            COMMAND_STUDIO_DIRECTOR_STUDIO_COMMANDS,
            COMMAND_STUDIO_DIRECTOR_OPEN_NEW_STUDIO_WORLD,
            COMMAND_STUDIO_PARAM_DIMENSION_PACK_OPEN_STUDIO,
            COMMAND_STUDIO_PARAM_SEED_GENERATE_STUDIO_WITH,
            COMMAND_STUDIO_DIRECTOR_IMPORT_VANILLA_TREES_MUSHROOMS_OBJECTS_STRUCTURES_JIGSAW_FROM_SERVER_INTO_PACK_S,
            COMMAND_STUDIO_PARAM_DIMENSION_PACK_IMPORT_VANILLA_CONTENT_INTO,
            COMMAND_STUDIO_PARAM_HOW_MANY_VARIANTS_CAPTURE_PER_TREE_OBJECT_FEATURE,
            COMMAND_STUDIO_PARAM_ALSO_IMPORT_VANILLA_DATAPACK_STRUCTURES_JIGSAW_INTO_PACK,
            COMMAND_STUDIO_DIRECTOR_OPEN_VSCODE_DIMENSION,
            COMMAND_STUDIO_PARAM_DIMENSION_OPEN_VSCODE,
            COMMAND_STUDIO_DIRECTOR_CLOSE_OPEN_STUDIO_PROJECT,
            COMMAND_STUDIO_DIRECTOR_TOGGLE_YOUR_STUDIO_DEBUG_SCOREBOARD,
            COMMAND_STUDIO_DIRECTOR_CREATE_NEW_STUDIO_PROJECT,
            COMMAND_STUDIO_PARAM_NAME_THIS_NEW_IRIS_PROJECT,
            COMMAND_STUDIO_PARAM_COPY_CONTENTS_EXISTING_PROJECT_YOUR_PACKS_FOLDER_USE_IT_AS_TEMPLATE_THIS,
            COMMAND_STUDIO_DIRECTOR_GET_VERSION_PACK,
            COMMAND_STUDIO_PARAM_DIMENSION_GET_VERSION,
            COMMAND_STUDIO_DIRECTOR_OPEN_NOISE_EXPLORER_EXTERNAL_GUI,
            COMMAND_STUDIO_PARAM_OPTIONAL_PACK_GENERATOR_PREVIEW,
            COMMAND_STUDIO_PARAM_SEED_PREVIEW_GENERATOR_WITH,
            COMMAND_STUDIO_DIRECTOR_SHOW_LOOT_IF_CHEST_WERE_RIGHT_HERE,
            COMMAND_STUDIO_PARAM_FAST_INSERTION_ITEMS_VIRTUAL_INVENTORY_MAY_CAUSE_PERFORMANCE_DROP,
            COMMAND_STUDIO_PARAM_WHETHER_NOT_APPEND_INVENTORY_CURRENTLY_OPEN_IF_FALSE_CLEARS_OPENED_INVENTORY,
            COMMAND_STUDIO_DIRECTOR_CALCULATE_CHANCE_EACH_REGION_GENERATE,
            COMMAND_STUDIO_PARAM_RADIUS_CHUNKS,
            COMMAND_STUDIO_DIRECTOR_RENDER_WORLD_MAP_EXTERNAL_GUI,
            COMMAND_STUDIO_PARAM_WORLD_OPEN_GENERATOR,
            COMMAND_STUDIO_DIRECTOR_PACKAGE_DIMENSION_INTO_COMPRESSED_FORMAT,
            COMMAND_STUDIO_PARAM_DIMENSION_PACK_COMPRESS,
            COMMAND_STUDIO_PARAM_WHETHER_NOT_OBFUSCATE_PACK,
            COMMAND_STUDIO_PARAM_WHETHER_NOT_MINIFY_PACK,
            COMMAND_STUDIO_DIRECTOR_PROFILES_PERFORMANCE_DIMENSION,
            COMMAND_STUDIO_PARAM_DIMENSION_PROFILE,
            COMMAND_STUDIO_DIRECTOR_SPAWN_IRIS_ENTITY,
            COMMAND_STUDIO_PARAM_ENTITY_SPAWN,
            COMMAND_STUDIO_PARAM_LOCATION_SPAWN_ENTITY_AT,
            COMMAND_STUDIO_DIRECTOR_TELEPORT_ACTIVE_STUDIO_WORLD,
            COMMAND_STUDIO_DIRECTOR_UPDATE_YOUR_DIMENSION_PROJECTS_VSCODE_WORKSPACE,
            COMMAND_STUDIO_PARAM_DIMENSION_UPDATE_WORKSPACE,
            COMMAND_STUDIO_DIRECTOR_CAPTURE_IGENDATA_CHUNK_REPORT_NEARBY_CHUNKS,
            COMMAND_WHAT_DIRECTOR_IRIS_WHAT,
            COMMAND_WHAT_DIRECTOR_WHAT_IS_MY_HAND,
            COMMAND_WHAT_DIRECTOR_WHAT_BIOME_AM_I,
            COMMAND_WHAT_DIRECTOR_WHAT_REGION_AM_I,
            COMMAND_WHAT_DIRECTOR_WHAT_BLOCK_AM_I_LOOKING_AT,
            COMMAND_WHAT_DIRECTOR_SHOW_MARKERS_CHUNK,
            COMMAND_WHAT_PARAM_MARKER_NAME_SUCH_AS_CAVE_FLOOR_CAVE_CEILING
    );

    private DirectorCommandMessages() {
    }

    public static List<MessageKey> keys() {
        return KEYS;
    }
}

package art.arcane.iris.localization;

import art.arcane.volmlib.util.localization.LinesKey;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.PluralKey;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;
import java.util.Map;

public final class BukkitRuntimeMessages {
    public static final TextKey COMMAND_PACK_PACKS_FOLDER_NOT_FOUND = TextKey.of(
            "iris.bukkit.runtime.commandpack.packs_folder_not_found",
            C.RED + "packs/ folder not found."
    );
    public static final TextKey COMMAND_PACK_NO_PACKS_VALIDATE = TextKey.of(
            "iris.bukkit.runtime.commandpack.no_packs_validate",
            C.YELLOW + "No packs to validate."
    );
    public static final TextKey COMMAND_PACK_VALIDATION_COMPLETE_BROKEN_PACKS = TextKey.of(
            "iris.bukkit.runtime.commandpack.validation_complete_broken_packs",
            C.GREEN + "Validation complete. Broken packs: " + "{broken}" + "/" + "{value}"
    );
    public static final TextKey COMMAND_PACK_PACK_NOT_FOUND_UNDER_PACKS = TextKey.of(
            "iris.bukkit.runtime.commandpack.pack_not_found_under_packs",
            C.RED + "Pack '" + "{pack}" + "' not found under packs/."
    );
    public static final TextKey COMMAND_PACK_NO_CLEANUP_CANDIDATES_FOUND_PACK = TextKey.of(
            "iris.bukkit.runtime.commandpack.no_cleanup_candidates_found_pack",
            C.GREEN + "No cleanup candidates found for pack '" + "{pack}" + "'."
    );
    public static final TextKey COMMAND_PACK_QUARANTINED_CLEANUP_CANDIDATE_S_UNDER = TextKey.of(
            "iris.bukkit.runtime.commandpack.quarantined_cleanup_candidate_s_under",
            C.GREEN + "Quarantined " + "{size}" + " cleanup candidate(s) under " + "{quarantinePath}" + "."
    );
    public static final TextKey COMMAND_PACK_CLEANUP_MODE_MUST_BE_PREVIEW_APPLY = TextKey.of(
            "iris.bukkit.runtime.commandpack.cleanup_mode_must_be_preview_apply",
            C.RED + "Cleanup mode must be preview or apply."
    );
    public static final TextKey COMMAND_PACK_NO_CLEANUP_CANDIDATES_FOUND_PACK_2 = TextKey.of(
            "iris.bukkit.runtime.commandpack.no_cleanup_candidates_found_pack_2",
            C.GREEN + "No cleanup candidates found for pack '" + "{pack}" + "'."
    );
    public static final TextKey COMMAND_PACK_CLEANUP_PREVIEW_PACK_CANDIDATE_S_NO_FILES_WERE_CHANGED = TextKey.of(
            "iris.bukkit.runtime.commandpack.cleanup_preview_pack_candidate_s_no_files_were_changed",
            C.YELLOW + "Cleanup preview for pack '" + "{pack}" + "': " + "{size}" + " candidate(s). No files were changed."
    );
    public static final TextKey COMMAND_PACK_RUN_IRIS_PACK_CLEANUP_MODE_APPLY_QUARANTINE_THESE_CANDIDATES_AFTER_FRESH_SCAN = TextKey.of(
            "iris.bukkit.runtime.commandpack.run_iris_pack_cleanup_mode_apply_quarantine_these_candidates_after_fresh_scan",
            C.GRAY + "Run /iris pack cleanup " + "{pack}" + " mode=apply to quarantine these candidates after a fresh scan."
    );
    public static final TextKey COMMAND_PACK_RESTORE_REFUSED_BECAUSE_DESTINATION_S_ALREADY_EXIST = TextKey.of(
            "iris.bukkit.runtime.commandpack.restore_refused_because_destination_s_already_exist",
            C.RED + "Restore refused because " + "{size}" + " destination(s) already exist."
    );
    public static final TextKey COMMAND_PACK_NOTHING_RESTORE_PACK = TextKey.of(
            "iris.bukkit.runtime.commandpack.nothing_restore_pack",
            C.YELLOW + "Nothing to restore for pack '" + "{pack}" + "'."
    );
    public static final TextKey COMMAND_PACK_RESTORED_FILE_S_FROM = TextKey.of(
            "iris.bukkit.runtime.commandpack.restored_file_s_from",
            C.GREEN + "Restored " + "{size}" + " file(s) from " + "{dumpPath}" + "."
    );
    public static final TextKey COMMAND_PACK_RESTORE_MODE_MUST_BE_PREVIEW_APPLY = TextKey.of(
            "iris.bukkit.runtime.commandpack.restore_mode_must_be_preview_apply",
            C.RED + "Restore mode must be preview or apply."
    );
    public static final TextKey COMMAND_PACK_NOTHING_RESTORE_PACK_2 = TextKey.of(
            "iris.bukkit.runtime.commandpack.nothing_restore_pack_2",
            C.YELLOW + "Nothing to restore for pack '" + "{pack}" + "'."
    );
    public static final TextKey COMMAND_PACK_RESTORE_PREVIEW_FILE_S_NO_FILES_WERE_CHANGED = TextKey.of(
            "iris.bukkit.runtime.commandpack.restore_preview_file_s_no_files_were_changed",
            C.YELLOW + "Restore preview for " + "{dumpPath}" + ": " + "{size}" + " file(s). No files were changed."
    );
    public static final TextKey COMMAND_PACK_RESTORE_IS_BLOCKED_BY_EXISTING_DESTINATION_S = TextKey.of(
            "iris.bukkit.runtime.commandpack.restore_is_blocked_by_existing_destination_s",
            C.RED + "Restore is blocked by " + "{size}" + " existing destination(s)."
    );
    public static final TextKey COMMAND_PACK_RUN_IRIS_PACK_RESTORE_MODE_APPLY_RESTORE_AFTER_FRESH_CONFLICT_CHECK = TextKey.of(
            "iris.bukkit.runtime.commandpack.run_iris_pack_restore_mode_apply_restore_after_fresh_conflict_check",
            C.GRAY + "Run /iris pack restore " + "{pack}" + " mode=apply to restore after a fresh conflict check."
    );
    public static final TextKey COMMAND_PACK_NO_VALIDATION_RESULTS_RECORDED_RUN_IRIS_PACK_VALIDATE_FIRST = TextKey.of(
            "iris.bukkit.runtime.commandpack.no_validation_results_recorded_run_iris_pack_validate_first",
            C.YELLOW + "No validation results recorded. Run /iris pack validate first."
    );
    public static final TextKey COMMAND_PACK_STATUS_OK = TextKey.of(
            "iris.bukkit.runtime.commandpack.status.ok",
            C.GREEN + "OK" + C.RESET + " {pack}" + C.GRAY + " (blocking={blocking}, warnings={warnings})"
    );
    public static final TextKey COMMAND_PACK_STATUS_BROKEN = TextKey.of(
            "iris.bukkit.runtime.commandpack.status.broken",
            C.RED + "BROKEN" + C.RESET + " {pack}" + C.GRAY + " (blocking={blocking}, warnings={warnings})"
    );
    public static final TextKey COMMAND_PACK_CLEANUP_FAILED = TextKey.of(
            "iris.bukkit.runtime.commandpack.cleanup_failed",
            C.RED + "Cleanup failed: {error}"
    );
    public static final TextKey COMMAND_PACK_RESTORE_FAILED = TextKey.of(
            "iris.bukkit.runtime.commandpack.restore_failed",
            C.RED + "Restore failed: {error}"
    );
    public static final TextKey COMMAND_PACK_PATH_STILL_QUARANTINED = TextKey.of(
            "iris.bukkit.runtime.commandpack.path.still_quarantined",
            "still quarantined"
    );
    public static final TextKey COMMAND_PACK_PATH_QUARANTINED = TextKey.of(
            "iris.bukkit.runtime.commandpack.path.quarantined",
            "quarantined"
    );
    public static final TextKey COMMAND_PACK_PATH_CANDIDATE = TextKey.of(
            "iris.bukkit.runtime.commandpack.path.candidate",
            "candidate"
    );
    public static final TextKey COMMAND_PACK_PATH_CONFLICT = TextKey.of(
            "iris.bukkit.runtime.commandpack.path.conflict",
            "conflict"
    );
    public static final TextKey COMMAND_PACK_PATH_RESTORED = TextKey.of(
            "iris.bukkit.runtime.commandpack.path.restored",
            "restored"
    );
    public static final TextKey COMMAND_PACK_PATH_FILE = TextKey.of(
            "iris.bukkit.runtime.commandpack.path.file",
            "file"
    );
    public static final TextKey COMMAND_PACK_NO_VALIDATION_RESULT_RUN_IRIS_PACK_VALIDATE = TextKey.of(
            "iris.bukkit.runtime.commandpack.no_validation_result_run_iris_pack_validate",
            C.YELLOW + "No validation result for '" + "{pack}" + "'. Run /iris pack validate " + "{pack2}" + "."
    );
    public static final TextKey COMMAND_PACK_VALIDATION_FAILED = TextKey.of(
            "iris.bukkit.runtime.commandpack.validation_failed",
            C.RED + "Validation of '" + "{name}" + "' failed: " + "{error}"
    );
    public static final TextKey COMMAND_PACK_PACK_IS_LOADABLE_WARNINGS = TextKey.of(
            "iris.bukkit.runtime.commandpack.pack_is_loadable_warnings",
            C.GREEN + "Pack '" + "{packName}" + "' is loadable." + C.GRAY + " (warnings=" + "{size}" + ")"
    );
    public static final TextKey COMMAND_PACK_PACK_IS_BROKEN = TextKey.of(
            "iris.bukkit.runtime.commandpack.pack_is_broken",
            C.RED + "Pack '" + "{packName}" + "' is BROKEN:"
    );
    public static final TextKey COMMAND_PACK_MESSAGE = TextKey.of(
            "iris.bukkit.runtime.commandpack.message",
            C.RED + "  - " + "{reason}"
    );
    public static final TextKey COMMAND_PACK_MESSAGE_2 = TextKey.of(
            "iris.bukkit.runtime.commandpack.message_2",
            C.YELLOW + "  ! " + "{value}"
    );
    public static final PluralKey COMMAND_PACK_MORE_WARNING_S = PluralKey.of(
            "iris.bukkit.runtime.commandpack.more_warning_s",
            "count",
            Map.of(
                    "one", C.GRAY + "  ... and {count} more warning.",
                    "other", C.GRAY + "  ... and {count} more warnings."
            )
    );
    public static final TextKey COMMAND_PACK_COMPAT_HEADER = TextKey.of(
            "iris.bukkit.runtime.commandpack.compat_header",
            C.YELLOW + "Pack '" + "{pack}" + "': content unavailable on Minecraft " + "{version}"
    );
    public static final TextKey COMMAND_PACK_COMPAT_NONE = TextKey.of(
            "iris.bukkit.runtime.commandpack.compat_none",
            C.GREEN + "Pack '" + "{pack}" + "' uses no content that is unavailable on Minecraft " + "{version}" + "."
    );
    public static final TextKey COMMAND_PACK_COMPAT_REMEDY = TextKey.of(
            "iris.bukkit.runtime.commandpack.compat_remedy",
            C.GRAY + "Update the server to a newer Minecraft to restore this content, or declare fallbacks"
                    + " (dimension blockFallbacks, block backup)."
    );
    public static final LinesKey COMMAND_DEVELOPER_UPDATE_WORLD_WARNING = LinesKey.of(
            "iris.bukkit.runtime.commanddeveloper.stage_world_generation_update_warning",
            C.RED + "Back up the complete world before staging this update.",
            C.YELLOW + "Existing chunks will not be regenerated or rewritten.",
            C.YELLOW + "New chunks use the staged pack after restart and blend across the frozen boundary.",
            C.YELLOW + "The world seed, height, environment, and dimension type cannot change.",
            C.YELLOW + "Every backup must retain the complete iris/generation directory.",
            C.RED + "To stage the update and request a clean restart:",
            C.RED + "/iris developer update-world {world} {pack} confirm=true"
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_IMPORTING_VANILLA_DATAPACK_STRUCTURES_MODE_INCLUDENONJIGSAW = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.importing_vanilla_datapack_structures_mode_includenonjigsaw",
            C.GREEN + "Importing " + C.WHITE + "{total}" + C.GREEN + " vanilla & datapack structures (mode=" + "{mode}" + ", includeNonJigsaw=" + "{includeNonJigsaw}" + ")..."
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_FAIL_INVALID_KEY = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.fail_invalid_key",
            C.RED + "[fail] " + "{keyString}" + ": invalid key"
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_JIGSAW = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.jigsaw",
            C.GRAY + "[jigsaw] " + "{keyString}" + " -> " + "{name}"
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_SINGLE = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.single",
            C.GRAY + "[single] " + "{keyString}" + " -> " + "{name}"
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_SKIP_NO_SINGLE_TEMPLATE_NBT_VANILLA_BUILDS_THIS_CODE_FROM_SEPARATE_PIECE = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.skip_no_single_template_nbt_vanilla_builds_this_code_from_separate_piece",
            C.YELLOW + "[skip] " + "{keyString}" + ": no single-template NBT - vanilla builds this in code or from separate piece templates (imported via the templates pass); nothing to import as one structure."
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_FAIL = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.fail",
            C.RED + "[fail] " + "{keyString}" + ": " + "{message}"
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_FAIL_2 = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.fail_2",
            C.RED + "[fail] " + "{keyString}" + ": " + "{message}"
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_FAIL_3 = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.fail_3",
            C.RED + "[fail] " + "{keyString}" + ": " + "{error}"
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_BULK_IMPORT_COMPLETE_IMPORTED_SKIPPED_FAILED_TOTAL = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.bulk_import_complete_imported_skipped_failed_total",
            C.GREEN + "Bulk import complete: " + C.WHITE + "{imported}" + C.GREEN + " imported, " + C.WHITE + "{skipped}" + C.GREEN + " skipped, " + C.WHITE + "{failed}" + C.GREEN + " failed (" + C.WHITE + "{total}" + C.GREEN + " total)."
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_BUILDING_SINGLE_TEMPLATE_STRUCTURES_FROM_IMPORTED_PIECES_ONE_VARIANT_PLACED_PER_GENERATION = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.building_single_template_structures_from_imported_pieces_one_variant_placed_per_generation",
            C.GREEN + "Building single-template structures from imported pieces (one variant placed per generation)..."
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_GROUP_VARIANTS = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.group_variants",
            C.GRAY + "[group] " + "{value}" + " -> " + "{value2}" + " (" + "{blocks}" + " variants)"
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_SKIP = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.skip",
            C.YELLOW + "[skip] " + "{value}" + ": " + "{message}"
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_FAIL_4 = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.fail_4",
            C.RED + "[fail] " + "{value}" + ": " + "{error}"
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_SINGLE_TEMPLATE_STRUCTURES_BUILT_SKIPPED_FAILED = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.single_template_structures_built_skipped_failed",
            C.GREEN + "Single-template structures: " + C.WHITE + "{imported}" + C.GREEN + " built, " + C.WHITE + "{skipped}" + C.GREEN + " skipped, " + C.WHITE + "{failed}" + C.GREEN + " failed."
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_FAILED_ENUMERATE_STRUCTURE_TEMPLATES_VIA_SERVER_RESOURCEMANAGER = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.failed_enumerate_structure_templates_via_server_resourcemanager",
            C.RED + "Failed to enumerate structure templates via the server ResourceManager: " + "{e}"
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_NO_STRUCTURE_TEMPLATES_WERE_FOUND_UNDER_STRUCTURE_RESOURCE_PATH = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.no_structure_templates_were_found_under_structure_resource_path",
            C.YELLOW + "No structure templates were found under the 'structure' resource path."
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_IMPORTING_STRUCTURE_TEMPLATES_MODE = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.importing_structure_templates_mode",
            C.GREEN + "Importing " + C.WHITE + "{total}" + C.GREEN + " structure templates (mode=" + "{mode}" + ")..."
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_FAIL_INVALID_KEY_2 = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.fail_invalid_key_2",
            C.RED + "[fail] " + "{keyString}" + ": invalid key"
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_FAIL_5 = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.fail_5",
            C.RED + "[fail] " + "{keyString}" + ": " + "{message}"
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_FAIL_6 = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.fail_6",
            C.RED + "[fail] " + "{keyString}" + ": " + "{error}"
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_IMPORTED_SKIPPED_FAILED = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.imported_skipped_failed",
            C.GRAY + "..." + "{processed}" + "/" + "{total}" + " (" + "{imported}" + " imported, " + "{skipped}" + " skipped, " + "{failed}" + " failed)"
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_TEMPLATE_IMPORT_COMPLETE_IMPORTED_SKIPPED_FAILED_TOTAL = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.template_import_complete_imported_skipped_failed_total",
            C.GREEN + "Template import complete: " + C.WHITE + "{imported}" + C.GREEN + " imported, " + C.WHITE + "{skipped}" + C.GREEN + " skipped, " + C.WHITE + "{failed}" + C.GREEN + " failed (" + C.WHITE + "{total}" + C.GREEN + " total)."
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_NO_DATAPACK_NON_MINECRAFT_STRUCTURES_ARE_REGISTERED_INGEST_DATAPACK_RESTART_FIRST_THEN = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.no_datapack_non_minecraft_structures_are_registered_ingest_datapack_restart_first_then",
            C.YELLOW + "No datapack (non-minecraft) structures are registered. Ingest a datapack and restart first, then run this again."
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_IMPORTING_DATAPACK_STRUCTURES_MODE = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.importing_datapack_structures_mode",
            C.GREEN + "Importing " + C.WHITE + "{total}" + C.GREEN + " datapack structures (mode=" + "{mode}" + ")..."
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_FAIL_INVALID_KEY_3 = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.fail_invalid_key_3",
            C.RED + "[fail] " + "{keyString}" + ": invalid key"
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_JIGSAW_2 = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.jigsaw_2",
            C.GRAY + "[jigsaw] " + "{keyString}" + " -> " + "{name}"
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_SINGLE_2 = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.single_2",
            C.GRAY + "[single] " + "{keyString}" + " -> " + "{name}"
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_FAIL_7 = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.fail_7",
            C.RED + "[fail] " + "{keyString}" + ": " + "{message}"
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_FAIL_8 = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.fail_8",
            C.RED + "[fail] " + "{keyString}" + ": " + "{message}"
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_FAIL_9 = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.fail_9",
            C.RED + "[fail] " + "{keyString}" + ": " + "{error}"
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_IMPORTING_DATAPACK_STRUCTURE_TEMPLATES = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.importing_datapack_structure_templates",
            C.GREEN + "Importing " + C.WHITE + "{size}" + C.GREEN + " datapack structure templates..."
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_FAIL_10 = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.fail_10",
            C.RED + "[fail] " + "{keyString}" + ": " + "{message}"
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_FAIL_11 = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.fail_11",
            C.RED + "[fail] " + "{keyString}" + ": " + "{error}"
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_COULD_NOT_ENUMERATE_DATAPACK_TEMPLATES_VIA_SERVER_RESOURCEMANAGER = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.could_not_enumerate_datapack_templates_via_server_resourcemanager",
            C.YELLOW + "Could not enumerate datapack templates via the server ResourceManager: " + "{error}"
    );
    public static final TextKey BULK_STRUCTURE_IMPORTER_DATAPACK_STRUCTURE_IMPORT_COMPLETE_IMPORTED_SKIPPED_FAILED = TextKey.of(
            "iris.bukkit.runtime.bulkstructureimporter.datapack_structure_import_complete_imported_skipped_failed",
            C.GREEN + "Datapack structure import complete: " + C.WHITE + "{imported}" + C.GREEN + " imported, " + C.WHITE + "{skipped}" + C.GREEN + " skipped, " + C.WHITE + "{failed}" + C.GREEN + " failed."
    );
    public static final TextKey STRUCTURE_CAPTURE_IMPORTER_STRUCTURE_CAPTURE_IS_NOT_SUPPORTED_BY_ACTIVE_NMS_BINDING_SKIPPING_CAPTURE_PASS = TextKey.of(
            "iris.bukkit.runtime.structurecaptureimporter.structure_capture_is_not_supported_by_active_nms_binding_skipping_capture_pass",
            C.YELLOW + "Structure capture is not supported by the active NMS binding; skipping the capture pass."
    );
    public static final TextKey STRUCTURE_CAPTURE_IMPORTER_NO_CODE_GENERATED_STRUCTURES_LEFT_CAPTURE_EVERYTHING_IS_ALREADY_IMPORTED_AS_STRUCTURE = TextKey.of(
            "iris.bukkit.runtime.structurecaptureimporter.no_code_generated_structures_left_capture_everything_is_already_imported_as_structure",
            C.GRAY + "No code-generated structures left to capture (everything is already imported as a structure)."
    );
    public static final TextKey STRUCTURE_CAPTURE_IMPORTER_CAPTURING_CODE_GENERATED_STRUCTURES_NO_NBT_TEMPLATE_INTO_SCRATCH_WORLD_SKIPPING_ANY = TextKey.of(
            "iris.bukkit.runtime.structurecaptureimporter.capturing_code_generated_structures_no_nbt_template_into_scratch_world_skipping_any",
            C.GREEN + "Capturing " + C.WHITE + "{total}" + C.GREEN + " code-generated structures (no NBT template) into a scratch world (skipping any wider/taller than " + "{MAXSPAN}" + " blocks)..."
    );
    public static final TextKey STRUCTURE_CAPTURE_IMPORTER_SKIP_DID_NOT_PLACE_CAPTURABLE_STRUCTURE_HERE_TOO_LARGE_WRONG_DIMENSION_NO = TextKey.of(
            "iris.bukkit.runtime.structurecaptureimporter.skip_did_not_place_capturable_structure_here_too_large_wrong_dimension_no",
            C.YELLOW + "[skip] " + "{key}" + ": did not place a capturable structure here (too large, wrong dimension, or no valid placement in a flat world). Stays vanilla-generated."
    );
    public static final TextKey STRUCTURE_CAPTURE_IMPORTER_FAIL = TextKey.of(
            "iris.bukkit.runtime.structurecaptureimporter.fail",
            C.RED + "[fail] " + "{key}" + ": " + "{message}"
    );
    public static final TextKey STRUCTURE_CAPTURE_IMPORTER_CAPTURE_OBJECTS_IOB_X_X = TextKey.of(
            "iris.bukkit.runtime.structurecaptureimporter.capture_objects_iob_x_x",
            C.GRAY + "[capture] " + "{key}" + " -> objects/" + "{name}" + ".iob (" + "{w}" + "x" + "{h}" + "x" + "{d}" + ")"
    );
    public static final TextKey STRUCTURE_CAPTURE_IMPORTER_FAIL_2 = TextKey.of(
            "iris.bukkit.runtime.structurecaptureimporter.fail_2",
            C.RED + "[fail] " + "{key}" + ": " + "{error}"
    );
    public static final TextKey STRUCTURE_CAPTURE_IMPORTER_CAPTURED_SKIPPED_FAILED = TextKey.of(
            "iris.bukkit.runtime.structurecaptureimporter.captured_skipped_failed",
            C.GRAY + "..." + "{processed}" + "/" + "{total}" + " (" + "{imported}" + " captured, " + "{skipped}" + " skipped, " + "{failed}" + " failed)"
    );
    public static final TextKey STRUCTURE_CAPTURE_IMPORTER_STRUCTURE_CAPTURE_COMPLETE_CAPTURED_SKIPPED_FAILED_TOTAL = TextKey.of(
            "iris.bukkit.runtime.structurecaptureimporter.structure_capture_complete_captured_skipped_failed_total",
            C.GREEN + "Structure capture complete: " + C.WHITE + "{imported}" + C.GREEN + " captured, " + C.WHITE + "{skipped}" + C.GREEN + " skipped, " + C.WHITE + "{failed}" + C.GREEN + " failed (" + C.WHITE + "{total}" + C.GREEN + " total)."
    );
    public static final TextKey FEATURE_IMPORTER_NO_VANILLA_TREE_OBJECT_FEATURES_ARE_EXPOSED_BY_ACTIVE_NMS_BINDING_IMPORTING = TextKey.of(
            "iris.bukkit.runtime.featureimporter.no_vanilla_tree_object_features_are_exposed_by_active_nms_binding_importing",
            C.YELLOW + "No vanilla tree/object features are exposed by the active NMS binding (importing structures only)."
    );
    public static final TextKey FEATURE_IMPORTER_IMPORTING_VANILLA_TREE_OBJECT_FEATURES_VARIANTS_EACH_INTO_SCRATCH_WORLD = TextKey.of(
            "iris.bukkit.runtime.featureimporter.importing_vanilla_tree_object_features_variants_each_into_scratch_world",
            C.GREEN + "Importing " + C.WHITE + "{total}" + C.GREEN + " vanilla tree/object features (" + C.WHITE + "{wantVariants}" + C.GREEN + " variants each) into a scratch world..."
    );
    public static final TextKey FEATURE_IMPORTER_OBJ_OBJECTS_VANILLA = TextKey.of(
            "iris.bukkit.runtime.featureimporter.obj_objects_vanilla",
            C.GRAY + "[obj] " + "{key}" + " -> objects/vanilla/" + "{group}" + "/" + "{safeName}" + " (" + "{written}" + ")"
    );
    public static final TextKey FEATURE_IMPORTER_SKIP_FEATURE_PLACED_NOTHING_AFTER_RETRIES = TextKey.of(
            "iris.bukkit.runtime.featureimporter.skip_feature_placed_nothing_after_retries",
            C.YELLOW + "[skip] " + "{key}" + ": feature placed nothing after retries."
    );
    public static final TextKey FEATURE_IMPORTER_FAIL = TextKey.of(
            "iris.bukkit.runtime.featureimporter.fail",
            C.RED + "[fail] " + "{key}" + ": " + "{error}"
    );
    public static final TextKey FEATURE_IMPORTER_IMPORTED_SKIPPED_FAILED = TextKey.of(
            "iris.bukkit.runtime.featureimporter.imported_skipped_failed",
            C.GRAY + "..." + "{processed}" + "/" + "{total}" + " (" + "{imported}" + " imported, " + "{skipped}" + " skipped, " + "{failed}" + " failed)"
    );
    public static final TextKey FEATURE_IMPORTER_FEATURE_IMPORT_COMPLETE_FEATURES_WRITTEN_SKIPPED_FAILED_TOTAL = TextKey.of(
            "iris.bukkit.runtime.featureimporter.feature_import_complete_features_written_skipped_failed_total",
            C.GREEN + "Feature import complete: " + C.WHITE + "{imported}" + C.GREEN + " features written, " + C.WHITE + "{skipped}" + C.GREEN + " skipped, " + C.WHITE + "{failed}" + C.GREEN + " failed (" + C.WHITE + "{total}" + C.GREEN + " total)."
    );
    public static final TextKey FEATURE_IMPORTER_COULD_NOT_CREATE_SCRATCH_WORLD_FEATURE_IMPORT_SKIPPING_TREE_OBJECT_PASS = TextKey.of(
            "iris.bukkit.runtime.featureimporter.could_not_create_scratch_world_feature_import_skipping_tree_object_pass",
            C.RED + "Could not create the scratch world for feature import (" + "{error}" + "); skipping the tree/object pass."
    );
    public static final TextKey IRIS_CONVERTER_NO_SCHEMATIC_FILES_CONVERT_FOUND = TextKey.of(
            "iris.bukkit.runtime.irisconverter.no_schematic_files_convert_found",
            "No schematic files to convert found in " + "{path}"
    );
    public static final TextKey IRIS_CONVERTER_CONVERTED = TextKey.of(
            "iris.bukkit.runtime.irisconverter.converted",
            C.IRIS + "Converted " + "{name}" + " -> " + "{value}" + " in " + "{value2}"
    );
    public static final TextKey IRIS_CONVERTER_CONVERTED_2 = TextKey.of(
            "iris.bukkit.runtime.irisconverter.converted_2",
            C.IRIS + "Converted " + "{name}" + " -> " + "{value}"
    );
    public static final TextKey IRIS_CONVERTER_FAILED_SAVE = TextKey.of(
            "iris.bukkit.runtime.irisconverter.failed_save",
            C.RED + "Failed to save: " + "{name}"
    );
    public static final TextKey IRIS_CONVERTER_FAILED_CONVERT = TextKey.of(
            "iris.bukkit.runtime.irisconverter.failed_convert",
            C.RED + "Failed to convert: " + "{name}"
    );
    public static final TextKey IRIS_CONVERTER_CONVERTED_3 = TextKey.of(
            "iris.bukkit.runtime.irisconverter.converted_3",
            C.GRAY + "Converted: " + "{get}" + " in " + "{value}"
    );
    public static final TextKey IRIS_CONVERTER_SOME_SCHEMATICS_FAILED_CONVERT_CHECK_CONSOLE_DETAILS = TextKey.of(
            "iris.bukkit.runtime.irisconverter.some_schematics_failed_convert_check_console_details",
            C.RED + "Some schematics failed to convert. Check the console for details."
    );
    public static final TextKey STUDIO_S_V_C_PACK_COPY_REQUIRES_ASYNC_THREAD = TextKey.of(
            "iris.bukkit.runtime.studiosvc.pack_copy_requires_async_thread",
            C.RED + "Iris refused to copy the world pack on the Bukkit primary thread."
    );
    public static final TextKey STUDIO_S_V_C_PACK_INSTALL_FAILED = TextKey.of(
            "iris.bukkit.runtime.studiosvc.pack_install_failed",
            C.RED + "Failed to install world pack " + C.WHITE + "{dimension}" + C.RED + ": {error}"
    );
    public static final TextKey STUDIO_S_V_C_LOOKING_PACKAGE = TextKey.of(
            "iris.bukkit.runtime.studiosvc.looking_package",
            "Looking for Package: " + "{type}"
    );
    public static final TextKey STUDIO_S_V_C_FOUND_IRIS_FOLDER = TextKey.of(
            "iris.bukkit.runtime.studiosvc.found_iris_folder",
            "Found " + "{type}" + ".iris in " + "{WORKSPACENAME}" + " folder"
    );
    public static final TextKey STUDIO_S_V_C_FOUND_DIMENSION_FOLDER_REPACKAGING = TextKey.of(
            "iris.bukkit.runtime.studiosvc.found_dimension_folder_repackaging",
            "Found " + "{type}" + " dimension in " + "{WORKSPACENAME}" + " folder. Repackaging"
    );
    public static final TextKey STUDIO_S_V_C_CAN_T_FIND_DIMENSIONS_FOLDER_THIS_PACK_FAILED = TextKey.of(
            "iris.bukkit.runtime.studiosvc.can_t_find_dimensions_folder_this_pack_failed",
            "Can't find the " + "{name}" + " in the dimensions folder of this pack! Failed!"
    );
    public static final TextKey STUDIO_S_V_C_CAN_T_LOAD_DIMENSION_FAILED = TextKey.of(
            "iris.bukkit.runtime.studiosvc.can_t_load_dimension_failed",
            "Can't load the dimension! Failed!"
    );
    public static final TextKey STUDIO_S_V_C_TYPE_INSTALLED = TextKey.of(
            "iris.bukkit.runtime.studiosvc.type_installed",
            "{name}" + " type installed. "
    );
    public static final TextKey STUDIO_S_V_C_FAILED_OPEN_STUDIO_WORLD = TextKey.of(
            "iris.bukkit.runtime.studiosvc.failed_open_studio_world",
            "Failed to open studio world: " + "{error}"
    );
    public static final TextKey STUDIO_S_V_C_CANNOT_OPEN_STUDIO_PACK_HAS_BLOCKING_ERRORS = TextKey.of(
            "iris.bukkit.runtime.studiosvc.cannot_open_studio_pack_has_blocking_errors",
            "Cannot open studio '" + "{dimm}" + "' - pack has blocking errors:"
    );
    public static final TextKey STUDIO_S_V_C_MESSAGE = TextKey.of(
            "iris.bukkit.runtime.studiosvc.message",
            " - " + "{reason}"
    );
    public static final TextKey STUDIO_S_V_C_FIX_PACK_RUN_IRIS_PACK_VALIDATE_REVALIDATE = TextKey.of(
            "iris.bukkit.runtime.studiosvc.fix_pack_run_iris_pack_validate_revalidate",
            "Fix the pack and run /iris pack validate " + "{dimm}" + " to revalidate."
    );
    public static final TextKey STUDIO_S_V_C_FAILED_CLOSE_EXISTING_STUDIO_PROJECT = TextKey.of(
            "iris.bukkit.runtime.studiosvc.failed_close_existing_studio_project",
            "Failed to close the existing studio project: " + "{error}"
    );
    public static final TextKey STUDIO_S_V_C_FAILED_CLOSE_EXISTING_STUDIO_PROJECT_2 = TextKey.of(
            "iris.bukkit.runtime.studiosvc.failed_close_existing_studio_project_2",
            "Failed to close the existing studio project: " + "{error}"
    );
    public static final TextKey STUDIO_S_V_C_FAILED_OPEN_STUDIO_WORLD_2 = TextKey.of(
            "iris.bukkit.runtime.studiosvc.failed_open_studio_world_2",
            "Failed to open studio world: " + "{error}"
    );
    public static final TextKey STUDIO_S_V_C_COULDN_T_FIND_PACK_CREATE_NEW_DIMENSION_FROM = TextKey.of(
            "iris.bukkit.runtime.studiosvc.couldn_t_find_pack_create_new_dimension_from",
            "Couldn't find the pack to create a new dimension from."
    );
    public static final TextKey STUDIO_S_V_C_MISSING_IMPORTED_DIMENSION_FILE = TextKey.of(
            "iris.bukkit.runtime.studiosvc.missing_imported_dimension_file",
            "Missing Imported Dimension File"
    );
    public static final TextKey STUDIO_S_V_C_IMPORTING_INTO_NEW_PROJECT = TextKey.of(
            "iris.bukkit.runtime.studiosvc.importing_into_new_project",
            "Importing " + "{downloadable}" + " into new Project " + "{s}"
    );
    public static final TextKey OBJECT_STUDIO_SAVE_SERVICE_OBJECT_STUDIO_NO_CELL_UNDER_CLICK_X_Z = TextKey.of(
            "iris.bukkit.runtime.objectstudiosaveservice.object_studio_no_cell_under_click_x_z",
            C.GRAY + "Object Studio: no cell under click (x=" + "{x}" + " z=" + "{z}" + ")."
    );
    public static final TextKey OBJECT_STUDIO_SAVE_SERVICE_OBJECT_STUDIO_SAVING_X_X = TextKey.of(
            "iris.bukkit.runtime.objectstudiosaveservice.object_studio_saving_x_x",
            C.AQUA + "Object Studio: saving " + C.WHITE + "{pack}" + "/" + "{key}" + C.GRAY + " (" + "{w}" + "x" + "{h}" + "x" + "{d}" + ")"
    );
    public static final TextKey OBJECT_STUDIO_SAVE_SERVICE_OBJECT_STUDIO_NO_CHANGES = TextKey.of(
            "iris.bukkit.runtime.objectstudiosaveservice.object_studio_no_changes",
            C.GRAY + "Object Studio: no changes for " + "{pack}" + "/" + "{key}" + "."
    );
    public static final TextKey OBJECT_STUDIO_SAVE_SERVICE_OBJECT_STUDIO_EMPTY_CELL_NOTHING_WRITE = TextKey.of(
            "iris.bukkit.runtime.objectstudiosaveservice.object_studio_empty_cell_nothing_write",
            C.GRAY + "Object Studio: empty cell " + "{pack}" + "/" + "{key}" + " (nothing to write)."
    );
    public static final TextKey OBJECT_STUDIO_SAVE_SERVICE_OBJECT_STUDIO_NO_TARGET_FILE = TextKey.of(
            "iris.bukkit.runtime.objectstudiosaveservice.object_studio_no_target_file",
            C.RED + "Object Studio: no target file for " + "{pack}" + "/" + "{key}" + "."
    );
    public static final TextKey OBJECT_STUDIO_SAVE_SERVICE_OBJECT_STUDIO_SAVED = TextKey.of(
            "iris.bukkit.runtime.objectstudiosaveservice.object_studio_saved",
            C.GREEN + "Object Studio: saved " + C.WHITE + "{pack}" + "/" + "{key}"
    );
    public static final TextKey OBJECT_STUDIO_SAVE_SERVICE_OBJECT_STUDIO_SAVE_FAILED = TextKey.of(
            "iris.bukkit.runtime.objectstudiosaveservice.object_studio_save_failed",
            C.RED + "Object Studio: save failed for " + "{pack}" + "/" + "{key}" + " (" + "{error}" + ")"
    );
    public static final TextKey IRIS_PROJECT_COULD_NOT_LOAD_DIMENSION = TextKey.of(
            "iris.bukkit.runtime.irisproject.could_not_load_dimension",
            "Could not load dimension \"" + "{value}" + "\""
    );
    public static final TextKey IRIS_PROJECT_COULD_NOT_GET_DIMENSION_LOADER = TextKey.of(
            "iris.bukkit.runtime.irisproject.could_not_get_dimension_loader",
            "Could not get dimension loader"
    );
    public static final TextKey IRIS_PROJECT_STUDIO_OPEN_FAILED = TextKey.of(
            "iris.bukkit.runtime.irisproject.studio_open_failed",
            C.RED + "Studio open failed: " + "{error}"
    );
    public static final TextKey IRIS_PROJECT_STUDIO_OPEN_FAILED_2 = TextKey.of(
            "iris.bukkit.runtime.irisproject.studio_open_failed_2",
            C.RED + "Studio open failed."
    );
    public static final TextKey IRIS_PROJECT_STUDIO_READY = TextKey.of(
            "iris.bukkit.runtime.irisproject.studio_ready",
            C.GREEN + "Studio ready " + C.GRAY + "(" + "{value}" + ")"
    );
    public static final TextKey IRIS_PROJECT_STUDIO = TextKey.of(
            "iris.bukkit.runtime.irisproject.studio",
            C.GOLD + "Studio " + C.AQUA + "{bar}" + " " + C.YELLOW + "{percent}" + "%" + C.GRAY + " " + "{currentStage}" + C.DARK_GRAY + " (" + "{value}" + ")"
    );
    public static final TextKey IRIS_PROJECT_SERIALIZING_OBJECTS = TextKey.of(
            "iris.bukkit.runtime.irisproject.serializing_objects",
            "Serializing Objects"
    );
    public static final TextKey IRIS_PROJECT_WROTE_ANOTHER_OBJECTS = TextKey.of(
            "iris.bukkit.runtime.irisproject.wrote_another_objects",
            "Wrote another " + "{g}" + " Objects"
    );
    public static final TextKey IRIS_PROJECT_PACKAGE_COMPILED = TextKey.of(
            "iris.bukkit.runtime.irisproject.package_compiled",
            "Package Compiled!"
    );
    public static final TextKey IRIS_PROJECT_FAILED = TextKey.of(
            "iris.bukkit.runtime.irisproject.failed",
            "Failed!"
    );
    public static final TextKey IRIS_ENGINE_TOTAL = TextKey.of(
            "iris.bukkit.runtime.irisengine.total",
            "Total: " + C.BOLD + C.WHITE + "{value}"
    );
    public static final TextKey IRIS_ENGINE_ENGINE = TextKey.of(
            "iris.bukkit.runtime.irisengine.engine",
            "  Engine " + C.UNDERLINE + C.GREEN + "{i}" + C.RESET + ": " + C.BOLD + C.WHITE + "{value}"
    );
    public static final TextKey IRIS_ENGINE_DETAILS = TextKey.of(
            "iris.bukkit.runtime.irisengine.details",
            "Details: "
    );
    public static final TextKey IRIS_ENGINE_MESSAGE = TextKey.of(
            "iris.bukkit.runtime.irisengine.message",
            "  " + "{befb}" + "{num}" + "{afb}" + ": " + C.BOLD + C.WHITE + "{value}"
    );
    public static final TextKey ENGINE_BUKKIT_OPS_IS_NOT_DEFINED_DIMENSION = TextKey.of(
            "iris.bukkit.runtime.enginebukkitops.is_not_defined_dimension",
            C.RED + "{name}" + " is not defined in the dimension!"
    );
    public static final TextKey ENGINE_BUKKIT_OPS_COULD_NOT_FIND_WITHIN_SEARCH_RANGE = TextKey.of(
            "iris.bukkit.runtime.enginebukkitops.could_not_find_within_search_range",
            C.RED + "Could not find " + "{message}" + " within search range."
    );
    public static final TextKey ENGINE_BUKKIT_OPS_TELEPORTING = TextKey.of(
            "iris.bukkit.runtime.enginebukkitops.teleporting",
            C.GREEN + "Teleporting to " + "{message}" + "..."
    );
    public static final TextKey ENGINE_BUKKIT_OPS_AT = TextKey.of(
            "iris.bukkit.runtime.enginebukkitops.at",
            C.GREEN + "{message}" + " at: " + "{blockX}" + " " + "{blockY}" + " " + "{blockZ}"
    );
    public static final TextKey IRIS_TOOLBELT_YOU_HAVE_BEEN_EVACUATED_FROM_THIS_WORLD = TextKey.of(
            "iris.bukkit.runtime.iristoolbelt.you_have_been_evacuated_from_this_world",
            "You have been evacuated from this world."
    );
    public static final TextKey IRIS_TOOLBELT_YOU_HAVE_BEEN_EVACUATED_FROM_THIS_WORLD_2 = TextKey.of(
            "iris.bukkit.runtime.iristoolbelt.you_have_been_evacuated_from_this_world_2",
            "You have been evacuated from this world. " + "{m}"
    );
    public static final TextKey SERVER_CONFIGURATOR_THERE_ARE_SOME_IRIS_PACKS_THAT_HAVE_CUSTOM_BIOMES_THEM = TextKey.of(
            "iris.bukkit.runtime.serverconfigurator.there_are_some_iris_packs_that_have_custom_biomes_them",
            "There are some Iris Packs that have custom biomes in them"
    );
    public static final TextKey SERVER_CONFIGURATOR_YOU_NEED_RESTART_YOUR_SERVER_USE_THESE_PACKS = TextKey.of(
            "iris.bukkit.runtime.serverconfigurator.you_need_restart_your_server_use_these_packs",
            "You need to restart your server to use these packs."
    );
    public static final TextKey VIRTUAL_COMMAND_MESSAGE = TextKey.of(
            "iris.bukkit.runtime.virtualcommand.message",
            "- " + C.WHITE + "{i}"
    );
    public static final TextKey VIRTUAL_COMMAND_INSUFFICIENT_PERMISSIONS = TextKey.of(
            "iris.bukkit.runtime.virtualcommand.insufficient_permissions",
            "Insufficient Permissions"
    );
    public static final TextKey MORTAR_COMMAND_PARAMETERS_IGNORED = TextKey.of(
            "iris.bukkit.runtime.mortarcommand.parameters_ignored",
            "Parameters Ignored: " + "{m}"
    );

    private static final List<MessageKey> KEYS = List.of(
            COMMAND_PACK_PACKS_FOLDER_NOT_FOUND,
            COMMAND_PACK_NO_PACKS_VALIDATE,
            COMMAND_PACK_VALIDATION_COMPLETE_BROKEN_PACKS,
            COMMAND_PACK_PACK_NOT_FOUND_UNDER_PACKS,
            COMMAND_PACK_NO_CLEANUP_CANDIDATES_FOUND_PACK,
            COMMAND_PACK_QUARANTINED_CLEANUP_CANDIDATE_S_UNDER,
            COMMAND_PACK_CLEANUP_MODE_MUST_BE_PREVIEW_APPLY,
            COMMAND_PACK_NO_CLEANUP_CANDIDATES_FOUND_PACK_2,
            COMMAND_PACK_CLEANUP_PREVIEW_PACK_CANDIDATE_S_NO_FILES_WERE_CHANGED,
            COMMAND_PACK_RUN_IRIS_PACK_CLEANUP_MODE_APPLY_QUARANTINE_THESE_CANDIDATES_AFTER_FRESH_SCAN,
            COMMAND_PACK_RESTORE_REFUSED_BECAUSE_DESTINATION_S_ALREADY_EXIST,
            COMMAND_PACK_NOTHING_RESTORE_PACK,
            COMMAND_PACK_RESTORED_FILE_S_FROM,
            COMMAND_PACK_RESTORE_MODE_MUST_BE_PREVIEW_APPLY,
            COMMAND_PACK_NOTHING_RESTORE_PACK_2,
            COMMAND_PACK_RESTORE_PREVIEW_FILE_S_NO_FILES_WERE_CHANGED,
            COMMAND_PACK_RESTORE_IS_BLOCKED_BY_EXISTING_DESTINATION_S,
            COMMAND_PACK_RUN_IRIS_PACK_RESTORE_MODE_APPLY_RESTORE_AFTER_FRESH_CONFLICT_CHECK,
            COMMAND_PACK_NO_VALIDATION_RESULTS_RECORDED_RUN_IRIS_PACK_VALIDATE_FIRST,
            COMMAND_PACK_STATUS_OK,
            COMMAND_PACK_STATUS_BROKEN,
            COMMAND_PACK_CLEANUP_FAILED,
            COMMAND_PACK_RESTORE_FAILED,
            COMMAND_PACK_PATH_STILL_QUARANTINED,
            COMMAND_PACK_PATH_QUARANTINED,
            COMMAND_PACK_PATH_CANDIDATE,
            COMMAND_PACK_PATH_CONFLICT,
            COMMAND_PACK_PATH_RESTORED,
            COMMAND_PACK_PATH_FILE,
            COMMAND_PACK_NO_VALIDATION_RESULT_RUN_IRIS_PACK_VALIDATE,
            COMMAND_PACK_VALIDATION_FAILED,
            COMMAND_PACK_PACK_IS_LOADABLE_WARNINGS,
            COMMAND_PACK_PACK_IS_BROKEN,
            COMMAND_PACK_MESSAGE,
            COMMAND_PACK_MESSAGE_2,
            COMMAND_PACK_MORE_WARNING_S,
            COMMAND_PACK_COMPAT_HEADER,
            COMMAND_PACK_COMPAT_NONE,
            COMMAND_PACK_COMPAT_REMEDY,
            COMMAND_DEVELOPER_UPDATE_WORLD_WARNING,
            BULK_STRUCTURE_IMPORTER_IMPORTING_VANILLA_DATAPACK_STRUCTURES_MODE_INCLUDENONJIGSAW,
            BULK_STRUCTURE_IMPORTER_FAIL_INVALID_KEY,
            BULK_STRUCTURE_IMPORTER_JIGSAW,
            BULK_STRUCTURE_IMPORTER_SINGLE,
            BULK_STRUCTURE_IMPORTER_SKIP_NO_SINGLE_TEMPLATE_NBT_VANILLA_BUILDS_THIS_CODE_FROM_SEPARATE_PIECE,
            BULK_STRUCTURE_IMPORTER_FAIL,
            BULK_STRUCTURE_IMPORTER_FAIL_2,
            BULK_STRUCTURE_IMPORTER_FAIL_3,
            BULK_STRUCTURE_IMPORTER_BULK_IMPORT_COMPLETE_IMPORTED_SKIPPED_FAILED_TOTAL,
            BULK_STRUCTURE_IMPORTER_BUILDING_SINGLE_TEMPLATE_STRUCTURES_FROM_IMPORTED_PIECES_ONE_VARIANT_PLACED_PER_GENERATION,
            BULK_STRUCTURE_IMPORTER_GROUP_VARIANTS,
            BULK_STRUCTURE_IMPORTER_SKIP,
            BULK_STRUCTURE_IMPORTER_FAIL_4,
            BULK_STRUCTURE_IMPORTER_SINGLE_TEMPLATE_STRUCTURES_BUILT_SKIPPED_FAILED,
            BULK_STRUCTURE_IMPORTER_FAILED_ENUMERATE_STRUCTURE_TEMPLATES_VIA_SERVER_RESOURCEMANAGER,
            BULK_STRUCTURE_IMPORTER_NO_STRUCTURE_TEMPLATES_WERE_FOUND_UNDER_STRUCTURE_RESOURCE_PATH,
            BULK_STRUCTURE_IMPORTER_IMPORTING_STRUCTURE_TEMPLATES_MODE,
            BULK_STRUCTURE_IMPORTER_FAIL_INVALID_KEY_2,
            BULK_STRUCTURE_IMPORTER_FAIL_5,
            BULK_STRUCTURE_IMPORTER_FAIL_6,
            BULK_STRUCTURE_IMPORTER_IMPORTED_SKIPPED_FAILED,
            BULK_STRUCTURE_IMPORTER_TEMPLATE_IMPORT_COMPLETE_IMPORTED_SKIPPED_FAILED_TOTAL,
            BULK_STRUCTURE_IMPORTER_NO_DATAPACK_NON_MINECRAFT_STRUCTURES_ARE_REGISTERED_INGEST_DATAPACK_RESTART_FIRST_THEN,
            BULK_STRUCTURE_IMPORTER_IMPORTING_DATAPACK_STRUCTURES_MODE,
            BULK_STRUCTURE_IMPORTER_FAIL_INVALID_KEY_3,
            BULK_STRUCTURE_IMPORTER_JIGSAW_2,
            BULK_STRUCTURE_IMPORTER_SINGLE_2,
            BULK_STRUCTURE_IMPORTER_FAIL_7,
            BULK_STRUCTURE_IMPORTER_FAIL_8,
            BULK_STRUCTURE_IMPORTER_FAIL_9,
            BULK_STRUCTURE_IMPORTER_IMPORTING_DATAPACK_STRUCTURE_TEMPLATES,
            BULK_STRUCTURE_IMPORTER_FAIL_10,
            BULK_STRUCTURE_IMPORTER_FAIL_11,
            BULK_STRUCTURE_IMPORTER_COULD_NOT_ENUMERATE_DATAPACK_TEMPLATES_VIA_SERVER_RESOURCEMANAGER,
            BULK_STRUCTURE_IMPORTER_DATAPACK_STRUCTURE_IMPORT_COMPLETE_IMPORTED_SKIPPED_FAILED,
            STRUCTURE_CAPTURE_IMPORTER_STRUCTURE_CAPTURE_IS_NOT_SUPPORTED_BY_ACTIVE_NMS_BINDING_SKIPPING_CAPTURE_PASS,
            STRUCTURE_CAPTURE_IMPORTER_NO_CODE_GENERATED_STRUCTURES_LEFT_CAPTURE_EVERYTHING_IS_ALREADY_IMPORTED_AS_STRUCTURE,
            STRUCTURE_CAPTURE_IMPORTER_CAPTURING_CODE_GENERATED_STRUCTURES_NO_NBT_TEMPLATE_INTO_SCRATCH_WORLD_SKIPPING_ANY,
            STRUCTURE_CAPTURE_IMPORTER_SKIP_DID_NOT_PLACE_CAPTURABLE_STRUCTURE_HERE_TOO_LARGE_WRONG_DIMENSION_NO,
            STRUCTURE_CAPTURE_IMPORTER_FAIL,
            STRUCTURE_CAPTURE_IMPORTER_CAPTURE_OBJECTS_IOB_X_X,
            STRUCTURE_CAPTURE_IMPORTER_FAIL_2,
            STRUCTURE_CAPTURE_IMPORTER_CAPTURED_SKIPPED_FAILED,
            STRUCTURE_CAPTURE_IMPORTER_STRUCTURE_CAPTURE_COMPLETE_CAPTURED_SKIPPED_FAILED_TOTAL,
            FEATURE_IMPORTER_NO_VANILLA_TREE_OBJECT_FEATURES_ARE_EXPOSED_BY_ACTIVE_NMS_BINDING_IMPORTING,
            FEATURE_IMPORTER_IMPORTING_VANILLA_TREE_OBJECT_FEATURES_VARIANTS_EACH_INTO_SCRATCH_WORLD,
            FEATURE_IMPORTER_OBJ_OBJECTS_VANILLA,
            FEATURE_IMPORTER_SKIP_FEATURE_PLACED_NOTHING_AFTER_RETRIES,
            FEATURE_IMPORTER_FAIL,
            FEATURE_IMPORTER_IMPORTED_SKIPPED_FAILED,
            FEATURE_IMPORTER_FEATURE_IMPORT_COMPLETE_FEATURES_WRITTEN_SKIPPED_FAILED_TOTAL,
            FEATURE_IMPORTER_COULD_NOT_CREATE_SCRATCH_WORLD_FEATURE_IMPORT_SKIPPING_TREE_OBJECT_PASS,
            IRIS_CONVERTER_NO_SCHEMATIC_FILES_CONVERT_FOUND,
            IRIS_CONVERTER_CONVERTED,
            IRIS_CONVERTER_CONVERTED_2,
            IRIS_CONVERTER_FAILED_SAVE,
            IRIS_CONVERTER_FAILED_CONVERT,
            IRIS_CONVERTER_CONVERTED_3,
            IRIS_CONVERTER_SOME_SCHEMATICS_FAILED_CONVERT_CHECK_CONSOLE_DETAILS,
            STUDIO_S_V_C_PACK_COPY_REQUIRES_ASYNC_THREAD,
            STUDIO_S_V_C_PACK_INSTALL_FAILED,
            STUDIO_S_V_C_LOOKING_PACKAGE,
            STUDIO_S_V_C_FOUND_IRIS_FOLDER,
            STUDIO_S_V_C_FOUND_DIMENSION_FOLDER_REPACKAGING,
            STUDIO_S_V_C_CAN_T_FIND_DIMENSIONS_FOLDER_THIS_PACK_FAILED,
            STUDIO_S_V_C_CAN_T_LOAD_DIMENSION_FAILED,
            STUDIO_S_V_C_TYPE_INSTALLED,
            STUDIO_S_V_C_FAILED_OPEN_STUDIO_WORLD,
            STUDIO_S_V_C_CANNOT_OPEN_STUDIO_PACK_HAS_BLOCKING_ERRORS,
            STUDIO_S_V_C_MESSAGE,
            STUDIO_S_V_C_FIX_PACK_RUN_IRIS_PACK_VALIDATE_REVALIDATE,
            STUDIO_S_V_C_FAILED_CLOSE_EXISTING_STUDIO_PROJECT,
            STUDIO_S_V_C_FAILED_CLOSE_EXISTING_STUDIO_PROJECT_2,
            STUDIO_S_V_C_FAILED_OPEN_STUDIO_WORLD_2,
            STUDIO_S_V_C_COULDN_T_FIND_PACK_CREATE_NEW_DIMENSION_FROM,
            STUDIO_S_V_C_MISSING_IMPORTED_DIMENSION_FILE,
            STUDIO_S_V_C_IMPORTING_INTO_NEW_PROJECT,
            OBJECT_STUDIO_SAVE_SERVICE_OBJECT_STUDIO_NO_CELL_UNDER_CLICK_X_Z,
            OBJECT_STUDIO_SAVE_SERVICE_OBJECT_STUDIO_SAVING_X_X,
            OBJECT_STUDIO_SAVE_SERVICE_OBJECT_STUDIO_NO_CHANGES,
            OBJECT_STUDIO_SAVE_SERVICE_OBJECT_STUDIO_EMPTY_CELL_NOTHING_WRITE,
            OBJECT_STUDIO_SAVE_SERVICE_OBJECT_STUDIO_NO_TARGET_FILE,
            OBJECT_STUDIO_SAVE_SERVICE_OBJECT_STUDIO_SAVED,
            OBJECT_STUDIO_SAVE_SERVICE_OBJECT_STUDIO_SAVE_FAILED,
            IRIS_PROJECT_COULD_NOT_LOAD_DIMENSION,
            IRIS_PROJECT_COULD_NOT_GET_DIMENSION_LOADER,
            IRIS_PROJECT_STUDIO_OPEN_FAILED,
            IRIS_PROJECT_STUDIO_OPEN_FAILED_2,
            IRIS_PROJECT_STUDIO_READY,
            IRIS_PROJECT_STUDIO,
            IRIS_PROJECT_SERIALIZING_OBJECTS,
            IRIS_PROJECT_WROTE_ANOTHER_OBJECTS,
            IRIS_PROJECT_PACKAGE_COMPILED,
            IRIS_PROJECT_FAILED,
            IRIS_ENGINE_TOTAL,
            IRIS_ENGINE_ENGINE,
            IRIS_ENGINE_DETAILS,
            IRIS_ENGINE_MESSAGE,
            ENGINE_BUKKIT_OPS_IS_NOT_DEFINED_DIMENSION,
            ENGINE_BUKKIT_OPS_COULD_NOT_FIND_WITHIN_SEARCH_RANGE,
            ENGINE_BUKKIT_OPS_TELEPORTING,
            ENGINE_BUKKIT_OPS_AT,
            IRIS_TOOLBELT_YOU_HAVE_BEEN_EVACUATED_FROM_THIS_WORLD,
            IRIS_TOOLBELT_YOU_HAVE_BEEN_EVACUATED_FROM_THIS_WORLD_2,
            SERVER_CONFIGURATOR_THERE_ARE_SOME_IRIS_PACKS_THAT_HAVE_CUSTOM_BIOMES_THEM,
            SERVER_CONFIGURATOR_YOU_NEED_RESTART_YOUR_SERVER_USE_THESE_PACKS,
            VIRTUAL_COMMAND_MESSAGE,
            VIRTUAL_COMMAND_INSUFFICIENT_PERMISSIONS,
            MORTAR_COMMAND_PARAMETERS_IGNORED
    );

    private BukkitRuntimeMessages() {
    }

    public static List<MessageKey> keys() {
        return KEYS;
    }
}

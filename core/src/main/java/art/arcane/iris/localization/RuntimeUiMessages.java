package art.arcane.iris.localization;

import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;

public final class RuntimeUiMessages {
    public static final TextKey FINDING_REGIONS = TextKey.of("iris.runtime.progress.finding_regions", "Finding regions");
    public static final TextKey CONVERTING = TextKey.of("iris.runtime.progress.converting", "Converting");
    public static final TextKey STATUS_RUNNING = TextKey.of("iris.runtime.status.running", "Running");
    public static final TextKey STATUS_STOPPED = TextKey.of("iris.runtime.status.stopped", "Stopped");
    public static final TextKey STATUS_PAUSED = TextKey.of("iris.runtime.status.paused", "Paused");
    public static final TextKey STATUS_PAUSED_LOWER = TextKey.of("iris.runtime.status.paused_lower", "paused");
    public static final TextKey STATUS_RUNNING_LOWER = TextKey.of("iris.runtime.status.running_lower", "running");
    public static final TextKey STATUS_ENABLED = TextKey.of("iris.runtime.status.enabled", "enabled.");
    public static final TextKey STATUS_DISABLED = TextKey.of("iris.runtime.status.disabled", "disabled.");
    public static final TextKey STATUS_UNREGISTERED = TextKey.of("iris.runtime.status.unregistered", "unregistered");
    public static final TextKey STATUS_NONE = TextKey.of("iris.runtime.status.none", "none");
    public static final TextKey STATUS_RANDOM = TextKey.of("iris.runtime.status.random", "random");
    public static final TextKey STATUS_MATCH = TextKey.of("iris.runtime.status.match", "MATCH");
    public static final TextKey STATUS_MISMATCH = TextKey.of("iris.runtime.status.mismatch", "MISMATCH");
    public static final TextKey STATUS_TRUE = TextKey.of("iris.runtime.status.true", "true");
    public static final TextKey STATUS_FALSE = TextKey.of("iris.runtime.status.false", "false");
    public static final TextKey ERROR_DETAIL_SUFFIX = TextKey.of("iris.runtime.error.detail_suffix", " - {error}");
    public static final TextKey DATAPACK_OVERRIDE_SUFFIX = TextKey.of("iris.runtime.datapack.override_suffix", " (world datapack override installed)");
    public static final TextKey PRIMARY_PLAYERS_ROUTED_SUFFIX = TextKey.of("iris.runtime.primary_world.players_routed_suffix", " (players routed there)");
    public static final TextKey PRIMARY_ROUTING_DISABLED_SUFFIX = TextKey.of("iris.runtime.primary_world.routing_disabled_suffix", " (routing disabled)");
    public static final TextKey MODDED_NO_DIMENSION_MATCH = TextKey.of("iris.runtime.modded.no_dimension_match", "No Iris dimension matches '{filter}'.");
    public static final TextKey TELEPORTED_TO_WORLD = TextKey.of("iris.runtime.teleport.world", "You have been teleported to {world}.");
    public static final TextKey ENGINE_HOTLOADED = TextKey.of("iris.runtime.engine.hotloaded", "Engine Hotloaded");
    public static final TextKey JOB_COMPLETED = TextKey.of("iris.runtime.job.completed", "Completed {job} in {duration}");
    public static final TextKey JOB_SCANNING_SELECTION = TextKey.of("iris.runtime.job.scanning_selection", "Scanning Selection");
    public static final TextKey JOB_LOADING_CHUNKS = TextKey.of("iris.runtime.job.loading_chunks", "Loading Chunks");
    public static final TextKey JOB_SEARCHED_CHUNKS = TextKey.of("iris.runtime.job.searched_chunks", "Searched {chunks} Chunks");
    public static final TextKey JOB_COMPILE = TextKey.of("iris.runtime.job.compile", "Compile");
    public static final TextKey JOB_SAVING_OBJECT = TextKey.of("iris.runtime.job.saving_object", "Saving Object");
    public static final TextKey JOB_DOWNLOADING = TextKey.of("iris.runtime.job.downloading", "Downloading");
    public static final TextKey JOB_EXTRACTING = TextKey.of("iris.runtime.job.extracting", "Extracting");
    public static final TextKey JOB_INSTALLING = TextKey.of("iris.runtime.job.installing", "Installing");
    public static final TextKey COMPILE_IOB_EMPTY = TextKey.of("iris.runtime.compile.iob_empty", "- IOB {file} has 0 blocks!");
    public static final TextKey COMPILE_IOB_EMPTY_HOVER = TextKey.of("iris.runtime.compile.iob_empty_hover", "Error:\n{path}");
    public static final TextKey COMPILE_IOB_NOT_3D = TextKey.of("iris.runtime.compile.iob_not_3d", "- IOB {file} is not 3D!");
    public static final TextKey COMPILE_IOB_NOT_3D_HOVER = TextKey.of("iris.runtime.compile.iob_not_3d_hover", "Error:\n{path}\nThe width, height, or depth is zero (bad format)");
    public static final TextKey COMPILE_JSON_ERROR = TextKey.of("iris.runtime.compile.json_error", "- JSON Error {file}");
    public static final TextKey COMPILE_JSON_ERROR_HOVER = TextKey.of("iris.runtime.compile.json_error_hover", "Error:\n{path}\n{error}");
    public static final TextKey COMPILE_LOADER_NOT_FOUND = TextKey.of("iris.runtime.compile.loader_not_found", "Can't find loader for {path}");
    public static final TextKey FORCED_DATAPACK_NAME = TextKey.of("iris.runtime.datapack.name", "Iris World Generation");
    public static final TextKey PACK_ALREADY_EXISTS = TextKey.of("iris.runtime.pack.already_exists", "Pack already exists!");
    public static final TextKey TREE_DRY_SUFFIX = TextKey.of("iris.runtime.tree_plausibilize.dry_suffix", ", DRY");
    public static final TextKey TREE_SKIP_LOAD = TextKey.of("iris.runtime.tree_plausibilize.skip_load", "skip {object}: failed to load");
    public static final TextKey TREE_RESULT = TextKey.of("iris.runtime.tree_plausibilize.result", "{object}: +{wood} wood ({branches} branches), {converted} leaves->wood, ~{distances} distances");
    public static final TextKey TREE_RESULT_PINNED = TextKey.of("iris.runtime.tree_plausibilize.result_pinned", "{object}: +{wood} wood ({branches} branches), {converted} leaves->wood, ~{distances} distances, !{pinned} pinned");
    public static final TextKey TREE_PROGRESS = TextKey.of("iris.runtime.tree_plausibilize.progress", "[{current}/{total}]");
    public static final TextKey TREE_FAILED = TextKey.of("iris.runtime.tree_plausibilize.failed", "fail {object}: {type}: {error}");
    public static final TextKey TREE_DONE = TextKey.of("iris.runtime.tree_plausibilize.done", "Done: {processed} processed, {changed} changed, {skipped} skipped, {failed} failed");
    public static final TextKey TREE_DONE_DRY = TextKey.of("iris.runtime.tree_plausibilize.done_dry", "Done: {processed} processed, {changed} changed, {skipped} skipped, {failed} failed (dry run, nothing written)");
    public static final TextKey TREE_TOTALS = TextKey.of("iris.runtime.tree_plausibilize.totals", "Totals: +{wood} wood ({branches} branches), {converted} leaves->wood, ~{distances} distances, !{pinned} pinned, unreachable {before} -> {after}");
    public static final TextKey WAND_NAME = TextKey.of("iris.runtime.item.wand.name", "Wand of Iris");
    public static final TextKey WAND_LORE_FIRST = TextKey.of("iris.runtime.item.wand.lore.first", "Left click a block to set the first corner");
    public static final TextKey WAND_LORE_SECOND = TextKey.of("iris.runtime.item.wand.lore.second", "Right click a block to set the second corner");
    public static final TextKey DUST_NAME = TextKey.of("iris.runtime.item.dust.name", "Dust of Revealing");
    public static final TextKey DUST_LORE = TextKey.of("iris.runtime.item.dust.lore", "Right click a block to reveal its placement structure!");
    public static final TextKey WAND_POSITION_SET = TextKey.of("iris.runtime.wand.position_set", "Position {position} set to {x}, {y}, {z}");
    public static final TextKey DUST_IRIS_WORLD_REQUIRED = TextKey.of("iris.runtime.dust.iris_world_required", "This dimension is not generated by Iris.");
    public static final TextKey DUST_FOUND_OBJECT = TextKey.of("iris.runtime.dust.found_object", "Found object {object}");
    public static final TextKey DUST_REVEALED = TextKey.of("iris.runtime.dust.revealed", "Revealed {count} block(s) of {object}");
    public static final TextKey DUST_REVEALED_CAPPED = TextKey.of("iris.runtime.dust.revealed_capped", "Revealed {count} block(s) of {object} (capped)");
    public static final TextKey DUST_HEADER = TextKey.of("iris.runtime.dust.header", "--- Iris Dust @ {x}, {y}, {z} ---");
    public static final TextKey DUST_BLOCK = TextKey.of("iris.runtime.dust.block", "Block: {block}");
    public static final TextKey DUST_POSITION_ABOVE = TextKey.of("iris.runtime.dust.position.above", "Position: +{offset} ABOVE surface (surface Y={surfaceY})");
    public static final TextKey DUST_POSITION_BELOW = TextKey.of("iris.runtime.dust.position.below", "Position: {offset} below surface (surface Y={surfaceY})");
    public static final TextKey DUST_POSITION_AT = TextKey.of("iris.runtime.dust.position.at", "Position: at surface (Y={surfaceY})");
    public static final TextKey DUST_OBJECT_AT_BLOCK = TextKey.of("iris.runtime.dust.object_at_block", "Object @block: {object}");
    public static final TextKey DUST_NONE = TextKey.of("iris.runtime.dust.none", "none");
    public static final TextKey DUST_PLACED_BY_OBJECT_ABOVE = TextKey.of("iris.runtime.dust.placed_by.object_above", "Placed by: object/stilt '{object}' (above surface)");
    public static final TextKey DUST_PLACED_BY_DECORATION_ABOVE = TextKey.of("iris.runtime.dust.placed_by.decoration_above", "Placed by: decoration/object/stilt (above surface)");
    public static final TextKey DUST_PLACED_BY_BURIED_OBJECT = TextKey.of("iris.runtime.dust.placed_by.buried_object", "Placed by: buried object '{object}'");
    public static final TextKey DUST_PLACED_BY_TERRAIN = TextKey.of("iris.runtime.dust.placed_by.terrain", "Placed by: terrain layer (depth {depth} below surface)");
    public static final TextKey DUST_COLUMN_OBJECT = TextKey.of("iris.runtime.dust.column_object", "Column object: {object} -> this block is likely that object's stilt");
    public static final TextKey DUST_COLUMN_OBJECT_NONE = TextKey.of("iris.runtime.dust.column_object_none", "Column object: {detail}");
    public static final TextKey DUST_COLUMN_NONE = TextKey.of("iris.runtime.dust.column_none", "none within 64 (decorator or terrain, NOT an object stilt)");
    public static final TextKey DUST_COLUMN_ABOVE = TextKey.of("iris.runtime.dust.column.above", "{object} @Y={y} (above)");
    public static final TextKey DUST_COLUMN_BELOW = TextKey.of("iris.runtime.dust.column.below", "{object} @Y={y} (below)");
    public static final TextKey DUST_SURFACE_BIOME = TextKey.of("iris.runtime.dust.surface_biome", "Surface biome: {biome}");
    public static final TextKey DUST_SURFACE_BIOME_DETAIL = TextKey.of("iris.runtime.dust.surface_biome_detail", "Surface biome: {biome} ({derivative})");
    public static final TextKey DUST_BIOME_AT_Y = TextKey.of("iris.runtime.dust.biome_at_y", "Biome @Y: {biome}");
    public static final TextKey DUST_CAVE_BIOME = TextKey.of("iris.runtime.dust.cave_biome", "Cave/Mantle biome: {biome}");
    public static final TextKey DUST_SERVER_BIOME = TextKey.of("iris.runtime.dust.server_biome", "Server biome: {biome} (ID: {id})");
    public static final TextKey DUST_REGION = TextKey.of("iris.runtime.dust.region", "Region: {region} ({name})");
    public static final TextKey DUST_OBJECTS_IN_CHUNK = TextKey.of("iris.runtime.dust.objects_in_chunk", "Objects in chunk: {objects}");
    public static final TextKey DUST_COPY_BUTTON = TextKey.of("iris.runtime.dust.copy_button", "[Click to copy these stats]");
    public static final TextKey DUST_COPY_HOVER = TextKey.of("iris.runtime.dust.copy_hover", "Copy block stats to clipboard");
    public static final TextKey DUST_REVEAL_FAILED = TextKey.of("iris.runtime.dust.reveal_failed", "Object reveal failed; see the console for details.");
    public static final TextKey WHAT_MATERIAL = TextKey.of("iris.runtime.what.material", "Material: {material}");
    public static final TextKey WHAT_FULL_STATE = TextKey.of("iris.runtime.what.full_state", "Full: {state}");
    public static final TextKey WHAT_ITEM_COUNT = TextKey.of("iris.runtime.what.item_count", "Count: {count}");
    public static final TextKey WHAT_IRIS_BIOME = TextKey.of("iris.runtime.what.iris_biome", "Iris biome: {biome} ({name})");
    public static final TextKey WHAT_DERIVATIVE_BIOME = TextKey.of("iris.runtime.what.derivative_biome", "Derivative biome: {biome}");
    public static final TextKey WHAT_NATIVE_BIOME = TextKey.of("iris.runtime.what.native_biome", "Registered biome: {biome} (ID: {id})");
    public static final TextKey WHAT_NON_IRIS_BIOME = TextKey.of("iris.runtime.what.non_iris_biome", "Non-Iris biome: {biome} (ID: {id})");
    public static final TextKey WHAT_IRIS_REGION = TextKey.of("iris.runtime.what.iris_region", "Iris region: {region} ({name})");
    public static final TextKey WHAT_POSITION = TextKey.of("iris.runtime.what.position", "Position: {x}, {y}, {z} (chunk {chunkX}, {chunkZ})");
    public static final TextKey WHAT_OBJECT = TextKey.of("iris.runtime.what.object", "Iris object: {object}");
    public static final TextKey WHAT_BLOCK_ENTITY = TextKey.of("iris.runtime.what.block_entity", "Block entity: {type}");
    public static final TextKey WHAT_LOOT_TABLE = TextKey.of("iris.runtime.what.loot_table", "Loot table: {loot}");
    public static final TextKey WHAT_SPAWNER_ENTITY = TextKey.of("iris.runtime.what.spawner_entity", "Spawner entity: {entity}");
    public static final TextKey WHAT_FLAG_SOLID = TextKey.of("iris.runtime.what.flag.solid", "solid");
    public static final TextKey WHAT_FLAG_FLUID = TextKey.of("iris.runtime.what.flag.fluid", "fluid");
    public static final TextKey WHAT_FLAG_WATER = TextKey.of("iris.runtime.what.flag.water", "water");
    public static final TextKey WHAT_FLAG_WATERLOGGED = TextKey.of("iris.runtime.what.flag.waterlogged", "waterlogged");
    public static final TextKey WHAT_FLAG_STORAGE = TextKey.of("iris.runtime.what.flag.storage", "storage (loot capable)");
    public static final TextKey WHAT_FLAG_LIT = TextKey.of("iris.runtime.what.flag.lit", "lit");
    public static final TextKey WHAT_FLAG_FOLIAGE = TextKey.of("iris.runtime.what.flag.foliage", "foliage");
    public static final TextKey WHAT_FLAG_PLANTABLE_FOLIAGE = TextKey.of("iris.runtime.what.flag.plantable_foliage", "plantable foliage");
    public static final TextKey WHAT_FLAG_DECORANT = TextKey.of("iris.runtime.what.flag.decorant", "decorant");
    public static final TextKey WHAT_FLAG_ORE = TextKey.of("iris.runtime.what.flag.ore", "ore");
    public static final TextKey WHAT_FLAG_BLOCK_ENTITY = TextKey.of("iris.runtime.what.flag.block_entity", "block entity");
    public static final TextKey WORLD_HEIGHT_RANGE = TextKey.of("iris.runtime.world.height_range", "World height: {minY} to {maxY}");
    public static final TextKey WORLD_HEIGHT_TOTAL = TextKey.of("iris.runtime.world.height_total", "Total height: {height}");
    public static final TextKey PREGEN_STARTING = TextKey.of("iris.runtime.pregen.starting", "Iris Pregen starting...");
    public static final TextKey PREGEN_HEADER = TextKey.of("iris.runtime.pregen.header", "Iris Pregen");
    public static final TextKey PREGEN_BOSSBAR_PAUSED = TextKey.of("iris.runtime.pregen.bossbar.paused", "Iris Pregen {generated}/{total} {percent}% PAUSED");
    public static final TextKey PREGEN_BOSSBAR_RUNNING = TextKey.of("iris.runtime.pregen.bossbar.running", "Iris Pregen {generated}/{total} {percent}% {speed}/s{eta}{failed}");
    public static final TextKey PREGEN_ETA_FRAGMENT = TextKey.of("iris.runtime.pregen.eta_fragment", "  ETA {eta}");
    public static final TextKey PREGEN_FAILED_FRAGMENT = TextKey.of("iris.runtime.pregen.failed_fragment", "  failed {failed}");
    public static final TextKey PREGEN_STATUS_CONTEXT = TextKey.of("iris.runtime.pregen.status.context", "Dimension {dimension} · Method {method}");
    public static final TextKey PREGEN_STATUS_PROGRESS = TextKey.of("iris.runtime.pregen.status.progress", "{percent}%");
    public static final TextKey PREGEN_STATUS_CHUNKS = TextKey.of("iris.runtime.pregen.status.chunks", "Chunks {generated}/{total} · Rates overall {overall}/s, 10s {tenSecond}/s, 30s {thirtySecond}/s, 60s {sixtySecond}/s");
    public static final TextKey PREGEN_STATUS_CHUNKS_FAILED = TextKey.of("iris.runtime.pregen.status.chunks_failed", "Chunks {generated}/{total} · Rates overall {overall}/s, 10s {tenSecond}/s, 30s {thirtySecond}/s, 60s {sixtySecond}/s · Failed {failed}");
    public static final TextKey PREGEN_STATUS_TIME = TextKey.of("iris.runtime.pregen.status.time", "ETA {eta} · Elapsed {elapsed}");
    public static final TextKey PREGEN_STATUS_TIME_PAUSED = TextKey.of("iris.runtime.pregen.status.time_paused", "ETA {eta} · Elapsed {elapsed} · PAUSED");
    public static final TextKey PREGEN_PAUSE_BUTTON = TextKey.of("iris.runtime.pregen.button.pause", "Pause/Resume");
    public static final TextKey PREGEN_PAUSE_HOVER = TextKey.of("iris.runtime.pregen.button.pause.hover", "Toggle pregeneration pause state");
    public static final TextKey PREGEN_STOP_BUTTON = TextKey.of("iris.runtime.pregen.button.stop", "Stop");
    public static final TextKey PREGEN_STOP_HOVER = TextKey.of("iris.runtime.pregen.button.stop.hover", "Finish the current region and stop pregeneration");

    private static final List<MessageKey> KEYS = List.of(
            FINDING_REGIONS,
            CONVERTING,
            STATUS_RUNNING,
            STATUS_STOPPED,
            STATUS_PAUSED,
            STATUS_PAUSED_LOWER,
            STATUS_RUNNING_LOWER,
            STATUS_ENABLED,
            STATUS_DISABLED,
            STATUS_UNREGISTERED,
            STATUS_NONE,
            STATUS_RANDOM,
            STATUS_MATCH,
            STATUS_MISMATCH,
            STATUS_TRUE,
            STATUS_FALSE,
            ERROR_DETAIL_SUFFIX,
            DATAPACK_OVERRIDE_SUFFIX,
            PRIMARY_PLAYERS_ROUTED_SUFFIX,
            PRIMARY_ROUTING_DISABLED_SUFFIX,
            MODDED_NO_DIMENSION_MATCH,
            TELEPORTED_TO_WORLD,
            ENGINE_HOTLOADED,
            JOB_COMPLETED,
            JOB_SCANNING_SELECTION,
            JOB_LOADING_CHUNKS,
            JOB_SEARCHED_CHUNKS,
            JOB_COMPILE,
            JOB_SAVING_OBJECT,
            JOB_DOWNLOADING,
            JOB_EXTRACTING,
            JOB_INSTALLING,
            COMPILE_IOB_EMPTY,
            COMPILE_IOB_EMPTY_HOVER,
            COMPILE_IOB_NOT_3D,
            COMPILE_IOB_NOT_3D_HOVER,
            COMPILE_JSON_ERROR,
            COMPILE_JSON_ERROR_HOVER,
            COMPILE_LOADER_NOT_FOUND,
            FORCED_DATAPACK_NAME,
            PACK_ALREADY_EXISTS,
            TREE_DRY_SUFFIX,
            TREE_SKIP_LOAD,
            TREE_RESULT,
            TREE_RESULT_PINNED,
            TREE_PROGRESS,
            TREE_FAILED,
            TREE_DONE,
            TREE_DONE_DRY,
            TREE_TOTALS,
            WAND_NAME,
            WAND_LORE_FIRST,
            WAND_LORE_SECOND,
            DUST_NAME,
            DUST_LORE,
            WAND_POSITION_SET,
            DUST_IRIS_WORLD_REQUIRED,
            DUST_FOUND_OBJECT,
            DUST_REVEALED,
            DUST_REVEALED_CAPPED,
            DUST_HEADER,
            DUST_BLOCK,
            DUST_POSITION_ABOVE,
            DUST_POSITION_BELOW,
            DUST_POSITION_AT,
            DUST_OBJECT_AT_BLOCK,
            DUST_NONE,
            DUST_PLACED_BY_OBJECT_ABOVE,
            DUST_PLACED_BY_DECORATION_ABOVE,
            DUST_PLACED_BY_BURIED_OBJECT,
            DUST_PLACED_BY_TERRAIN,
            DUST_COLUMN_OBJECT,
            DUST_COLUMN_OBJECT_NONE,
            DUST_COLUMN_NONE,
            DUST_COLUMN_ABOVE,
            DUST_COLUMN_BELOW,
            DUST_SURFACE_BIOME,
            DUST_SURFACE_BIOME_DETAIL,
            DUST_BIOME_AT_Y,
            DUST_CAVE_BIOME,
            DUST_SERVER_BIOME,
            DUST_REGION,
            DUST_OBJECTS_IN_CHUNK,
            DUST_COPY_BUTTON,
            DUST_COPY_HOVER,
            DUST_REVEAL_FAILED,
            WHAT_MATERIAL,
            WHAT_FULL_STATE,
            WHAT_ITEM_COUNT,
            WHAT_IRIS_BIOME,
            WHAT_DERIVATIVE_BIOME,
            WHAT_NATIVE_BIOME,
            WHAT_NON_IRIS_BIOME,
            WHAT_IRIS_REGION,
            WHAT_POSITION,
            WHAT_OBJECT,
            WHAT_BLOCK_ENTITY,
            WHAT_LOOT_TABLE,
            WHAT_SPAWNER_ENTITY,
            WHAT_FLAG_SOLID,
            WHAT_FLAG_FLUID,
            WHAT_FLAG_WATER,
            WHAT_FLAG_WATERLOGGED,
            WHAT_FLAG_STORAGE,
            WHAT_FLAG_LIT,
            WHAT_FLAG_FOLIAGE,
            WHAT_FLAG_PLANTABLE_FOLIAGE,
            WHAT_FLAG_DECORANT,
            WHAT_FLAG_ORE,
            WHAT_FLAG_BLOCK_ENTITY,
            WORLD_HEIGHT_RANGE,
            WORLD_HEIGHT_TOTAL,
            PREGEN_STARTING,
            PREGEN_HEADER,
            PREGEN_BOSSBAR_PAUSED,
            PREGEN_BOSSBAR_RUNNING,
            PREGEN_ETA_FRAGMENT,
            PREGEN_FAILED_FRAGMENT,
            PREGEN_STATUS_CONTEXT,
            PREGEN_STATUS_PROGRESS,
            PREGEN_STATUS_CHUNKS,
            PREGEN_STATUS_CHUNKS_FAILED,
            PREGEN_STATUS_TIME,
            PREGEN_STATUS_TIME_PAUSED,
            PREGEN_PAUSE_BUTTON,
            PREGEN_PAUSE_HOVER,
            PREGEN_STOP_BUTTON,
            PREGEN_STOP_HOVER
    );

    private RuntimeUiMessages() {
    }

    public static List<MessageKey> keys() {
        return KEYS;
    }
}

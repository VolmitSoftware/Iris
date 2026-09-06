package art.arcane.iris.core.localization;

import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;

public final class DesktopUiMessages {
    public static final TextKey VISION_TITLE = TextKey.of("iris.desktop.vision.title", "Iris Vision");
    public static final TextKey VISION_VIEW = TextKey.of("iris.desktop.vision.view", "View:");
    public static final TextKey VISION_GRID = TextKey.of("iris.desktop.vision.grid", "Grid");
    public static final TextKey VISION_ENTITIES = TextKey.of("iris.desktop.vision.entities", "Entities");
    public static final TextKey VISION_FOLLOW = TextKey.of("iris.desktop.vision.follow", "Follow");
    public static final TextKey VISION_REFRESHING = TextKey.of("iris.desktop.vision.refreshing", "Refreshing");
    public static final TextKey VISION_FPS = TextKey.of("iris.desktop.vision.fps", "{fps} FPS");
    public static final TextKey VISION_ZOOM_RESET = TextKey.of("iris.desktop.vision.zoom_reset", "Zoom reset");
    public static final TextKey VISION_GRID_ENABLED = TextKey.of("iris.desktop.vision.grid_enabled", "Grid enabled");
    public static final TextKey VISION_GRID_DISABLED = TextKey.of("iris.desktop.vision.grid_disabled", "Grid disabled");
    public static final TextKey VISION_ENTITIES_ENABLED = TextKey.of("iris.desktop.vision.entities_enabled", "Entities enabled");
    public static final TextKey VISION_ENTITIES_DISABLED = TextKey.of("iris.desktop.vision.entities_disabled", "Entities disabled");
    public static final TextKey VISION_FOLLOWING = TextKey.of("iris.desktop.vision.following", "Following {player}");
    public static final TextKey VISION_NO_PLAYER = TextKey.of("iris.desktop.vision.no_player", "No player in world");
    public static final TextKey VISION_FOLLOW_DISABLED = TextKey.of("iris.desktop.vision.follow_disabled", "Follow disabled");
    public static final TextKey VISION_STATUS_LEFT = TextKey.of("iris.desktop.vision.status_left", "{mode}  |  {bpp} bpp  |  {width} x {height} blocks");
    public static final TextKey VISION_STATUS_RIGHT = TextKey.of("iris.desktop.vision.status_right", "X: {x}  Z: {z}  |  {fps} FPS");
    public static final TextKey VISION_ENTITY_POSITION = TextKey.of("iris.desktop.vision.entity_position", "Position: {x}, {y}, {z}");
    public static final TextKey VISION_ENTITY_HEALTH = TextKey.of("iris.desktop.vision.entity_health", "Health: {health} / {maximum}");
    public static final TextKey VISION_BLOCK_POSITION = TextKey.of("iris.desktop.vision.block_position", "Block {x}, {z}");
    public static final TextKey VISION_CHUNK_POSITION = TextKey.of("iris.desktop.vision.chunk_position", "Chunk {x}, {z}");
    public static final TextKey VISION_REGION_POSITION = TextKey.of("iris.desktop.vision.region_position", "Region {x}, {z}");
    public static final TextKey VISION_BIOME_KEY = TextKey.of("iris.desktop.vision.biome_key", "Key: {key}");
    public static final TextKey VISION_BIOME_FILE = TextKey.of("iris.desktop.vision.biome_file", "File: {file}");
    public static final TextKey VISION_VELOCITY = TextKey.of("iris.desktop.vision.velocity", "Velocity: {velocity}");
    public static final TextKey VISION_TILES = TextKey.of("iris.desktop.vision.tiles", "Atlas pages: {ready} / {total} exact");
    public static final TextKey VISION_WORKERS = TextKey.of("iris.desktop.vision.workers", "Workers: {active} active / {queued} queued");
    public static final TextKey VISION_CENTER = TextKey.of("iris.desktop.vision.center", "Center: {x}, {z}");
    public static final TextKey VISION_HELP_TOGGLE = TextKey.of("iris.desktop.vision.help.toggle", "Toggle help");
    public static final TextKey VISION_HELP_REFRESH = TextKey.of("iris.desktop.vision.help.refresh", "Refresh tiles");
    public static final TextKey VISION_HELP_FOLLOW = TextKey.of("iris.desktop.vision.help.follow", "Follow player");
    public static final TextKey VISION_HELP_ZOOM = TextKey.of("iris.desktop.vision.help.zoom", "Zoom in/out");
    public static final TextKey VISION_HELP_RESET_ZOOM = TextKey.of("iris.desktop.vision.help.reset_zoom", "Reset zoom");
    public static final TextKey VISION_HELP_CYCLE_MODE = TextKey.of("iris.desktop.vision.help.cycle_mode", "Cycle render mode");
    public static final TextKey VISION_HELP_FPS = TextKey.of("iris.desktop.vision.help.fps", "Toggle 30/60 FPS");
    public static final TextKey VISION_HELP_GRID = TextKey.of("iris.desktop.vision.help.grid", "Toggle grid");
    public static final TextKey VISION_HELP_BIOME = TextKey.of("iris.desktop.vision.help.biome", "Detailed biome info");
    public static final TextKey VISION_HELP_TELEPORT = TextKey.of("iris.desktop.vision.help.teleport", "Teleport to cursor");
    public static final TextKey VISION_HELP_EDITOR = TextKey.of("iris.desktop.vision.help.editor", "Open biome in editor");
    public static final TextKey VISION_OPENED = TextKey.of("iris.desktop.vision.opened", "Opened {target}");
    public static final TextKey VISION_TELEPORTING = TextKey.of("iris.desktop.vision.teleporting", "Teleporting to {x}, {z}");
    public static final TextKey VISION_MODE_BIOME = TextKey.of("iris.desktop.vision.mode.biome", "Biome");
    public static final TextKey VISION_MODE_BIOME_LAND = TextKey.of("iris.desktop.vision.mode.biome_land", "Biome land");
    public static final TextKey VISION_MODE_BIOME_SEA = TextKey.of("iris.desktop.vision.mode.biome_sea", "Biome sea");
    public static final TextKey VISION_MODE_REGION = TextKey.of("iris.desktop.vision.mode.region", "Region");
    public static final TextKey VISION_MODE_CAVE_LAND = TextKey.of("iris.desktop.vision.mode.cave_land", "Cave land");
    public static final TextKey VISION_MODE_RIVER = TextKey.of("iris.desktop.vision.mode.river", "River network");
    public static final TextKey VISION_MODE_HEIGHT = TextKey.of("iris.desktop.vision.mode.height", "Height");
    public static final TextKey VISION_MODE_OBJECT_LOAD = TextKey.of("iris.desktop.vision.mode.object_load", "Object load");
    public static final TextKey VISION_MODE_DECORATOR_LOAD = TextKey.of("iris.desktop.vision.mode.decorator_load", "Decorator load");
    public static final TextKey VISION_MODE_CONTINENT = TextKey.of("iris.desktop.vision.mode.continent", "Continent");
    public static final TextKey VISION_MODE_LAYER_LOAD = TextKey.of("iris.desktop.vision.mode.layer_load", "Layer load");
    public static final TextKey NOISE_TITLE = TextKey.of("iris.desktop.noise.title", "Noise Explorer");
    public static final TextKey NOISE_TITLE_GENERATOR = TextKey.of("iris.desktop.noise.title_generator", "Noise Explorer: {generator}");
    public static final TextKey NOISE_SEARCH = TextKey.of("iris.desktop.noise.search", "Search...");
    public static final TextKey NOISE_STATUS = TextKey.of("iris.desktop.noise.status", "{name}  |  X: {x}  Z: {z}  |  Zoom: {zoom}  |  Value: {value}  |  {fps} FPS");
    public static final TextKey NOISE_CATEGORY_CUSTOM = TextKey.of("iris.desktop.noise.category.custom", "Custom");
    public static final TextKey NOISE_CATEGORY_PACK_GENERATORS = TextKey.of("iris.desktop.noise.category.pack_generators", "Pack Generators");
    public static final TextKey NOISE_CATEGORY_SIMPLEX = TextKey.of("iris.desktop.noise.category.simplex", "Simplex");
    public static final TextKey NOISE_CATEGORY_PERLIN = TextKey.of("iris.desktop.noise.category.perlin", "Perlin");
    public static final TextKey NOISE_CATEGORY_CELLULAR = TextKey.of("iris.desktop.noise.category.cellular", "Cellular");
    public static final TextKey NOISE_CATEGORY_IRIS = TextKey.of("iris.desktop.noise.category.iris", "Iris");
    public static final TextKey NOISE_CATEGORY_CLOVER = TextKey.of("iris.desktop.noise.category.clover", "Clover");
    public static final TextKey NOISE_CATEGORY_HEXAGON = TextKey.of("iris.desktop.noise.category.hexagon", "Hexagon");
    public static final TextKey NOISE_CATEGORY_VASCULAR = TextKey.of("iris.desktop.noise.category.vascular", "Vascular");
    public static final TextKey NOISE_CATEGORY_GLOBE = TextKey.of("iris.desktop.noise.category.globe", "Globe");
    public static final TextKey NOISE_CATEGORY_CUBIC = TextKey.of("iris.desktop.noise.category.cubic", "Cubic");
    public static final TextKey NOISE_CATEGORY_FRACTAL = TextKey.of("iris.desktop.noise.category.fractal", "Fractal");
    public static final TextKey NOISE_CATEGORY_STATIC = TextKey.of("iris.desktop.noise.category.static", "Static");
    public static final TextKey NOISE_CATEGORY_NOWHERE = TextKey.of("iris.desktop.noise.category.nowhere", "Nowhere");
    public static final TextKey NOISE_CATEGORY_SIERPINSKI = TextKey.of("iris.desktop.noise.category.sierpinski", "Sierpinski");
    public static final TextKey NOISE_CATEGORY_UTILITY = TextKey.of("iris.desktop.noise.category.utility", "Utility");
    public static final TextKey NOISE_CATEGORY_OTHER = TextKey.of("iris.desktop.noise.category.other", "Other");
    public static final TextKey PREGEN_INITIALIZING = TextKey.of("iris.desktop.pregen.initializing", "Preparing generation");
    public static final TextKey PREGEN_TITLE = TextKey.of("iris.desktop.pregen.title", "Pregeneration");
    public static final TextKey PREGEN_METHOD_PENDING = TextKey.of("iris.desktop.pregen.method_pending", "Pending");
    public static final TextKey PREGEN_PAUSED = TextKey.of("iris.desktop.pregen.paused", "Paused");
    public static final TextKey PREGEN_RESUME_HINT = TextKey.of("iris.desktop.pregen.resume_hint", "Press P to resume");
    public static final TextKey PREGEN_PAUSE_HINT = TextKey.of("iris.desktop.pregen.pause_hint", "Press P to pause");
    public static final TextKey PREGEN_PAUSE = TextKey.of("iris.desktop.pregen.pause", "Pause");
    public static final TextKey PREGEN_RESUME = TextKey.of("iris.desktop.pregen.resume", "Resume");
    public static final TextKey PREGEN_GENERATING = TextKey.of("iris.desktop.pregen.generating", "Generating");
    public static final TextKey PREGEN_SAVING = TextKey.of("iris.desktop.pregen.saving", "Saving");
    public static final TextKey PREGEN_STOPPING = TextKey.of("iris.desktop.pregen.stopping", "Stopping");
    public static final TextKey PREGEN_COMPLETED = TextKey.of("iris.desktop.pregen.completed", "Complete");
    public static final TextKey PREGEN_ERROR = TextKey.of("iris.desktop.pregen.error", "Generation failed");
    public static final TextKey PREGEN_CONTROL_FAILED = TextKey.of("iris.desktop.pregen.control_failed", "Pause control failed · see server log");
    public static final TextKey PREGEN_ERROR_DETAILS = TextKey.of("iris.desktop.pregen.error_details", "See server log for details");
    public static final TextKey PREGEN_PROGRESS = TextKey.of("iris.desktop.pregen.progress", "{generated} / {total} chunks");
    public static final TextKey PREGEN_PERCENT = TextKey.of("iris.desktop.pregen.percent", "{percent} complete");
    public static final TextKey PREGEN_CURRENT = TextKey.of("iris.desktop.pregen.current", "Current · 10 s");
    public static final TextKey PREGEN_OVERALL = TextKey.of("iris.desktop.pregen.overall", "Overall");
    public static final TextKey PREGEN_THIRTY = TextKey.of("iris.desktop.pregen.thirty", "30 s average");
    public static final TextKey PREGEN_SIXTY = TextKey.of("iris.desktop.pregen.sixty", "60 s average");
    public static final TextKey PREGEN_RATE = TextKey.of("iris.desktop.pregen.rate", "{rate} chunks/s");
    public static final TextKey PREGEN_ETA = TextKey.of("iris.desktop.pregen.eta", "Remaining");
    public static final TextKey PREGEN_ELAPSED = TextKey.of("iris.desktop.pregen.elapsed", "Elapsed");
    public static final TextKey PREGEN_MEMORY_LABEL = TextKey.of("iris.desktop.pregen.memory_label", "Memory");
    public static final TextKey PREGEN_MEMORY_USAGE = TextKey.of("iris.desktop.pregen.memory_usage", "{used} · {usage}");
    public static final TextKey PREGEN_PRESSURE = TextKey.of("iris.desktop.pregen.pressure", "Allocation");
    public static final TextKey PREGEN_PRESSURE_VALUE = TextKey.of("iris.desktop.pregen.pressure_value", "{rate}/s");
    public static final TextKey PREGEN_METHOD = TextKey.of("iris.desktop.pregen.method", "Method: {method}");
    public static final TextKey PREGEN_CACHED = TextKey.of("iris.desktop.pregen.cached", "{method} · cached");
    public static final TextKey PREGEN_FAILED = TextKey.of("iris.desktop.pregen.failed", "{count} failed · see server log");
    public static final TextKey PREGEN_MAP = TextKey.of("iris.desktop.pregen.map", "Generation map");
    public static final TextKey PREGEN_BOUNDS = TextKey.of("iris.desktop.pregen.bounds", "Chunks X {minX} to {maxX} · Z {minZ} to {maxZ}");
    public static final TextKey PREGEN_WAITING = TextKey.of("iris.desktop.pregen.waiting", "Waiting");
    public static final TextKey PREGEN_READY = TextKey.of("iris.desktop.pregen.ready", "Ready");
    public static final TextKey PREGEN_EXISTING = TextKey.of("iris.desktop.pregen.existing", "Existing");
    public static final TextKey PREGEN_NETWORK = TextKey.of("iris.desktop.pregen.network", "Network");
    public static final TextKey PREGEN_TERRAIN_HINT = TextKey.of("iris.desktop.pregen.terrain_hint", "Finished chunks show terrain colors");
    public static final TextKey IMAGEMAP_TITLE = TextKey.of("iris.desktop.imagemap.title", "Image Map Studio");
    public static final TextKey IMAGEMAP_PRESET = TextKey.of("iris.desktop.imagemap.preset", "Preset:");
    public static final TextKey IMAGEMAP_LOAD = TextKey.of("iris.desktop.imagemap.load", "Load");
    public static final TextKey IMAGEMAP_IMPORT_PNG = TextKey.of("iris.desktop.imagemap.import_png", "Import PNG");
    public static final TextKey IMAGEMAP_REPLACE_PNG = TextKey.of("iris.desktop.imagemap.replace_png", "Replace PNG");
    public static final TextKey IMAGEMAP_PREVIEW = TextKey.of("iris.desktop.imagemap.preview", "Preview");
    public static final TextKey IMAGEMAP_EXPORT = TextKey.of("iris.desktop.imagemap.export", "Export to active pack");
    public static final TextKey IMAGEMAP_METADATA = TextKey.of("iris.desktop.imagemap.metadata", "Source metadata");
    public static final TextKey IMAGEMAP_RESOURCE = TextKey.of("iris.desktop.imagemap.resource", "Resource binding");
    public static final TextKey IMAGEMAP_COORDINATES = TextKey.of("iris.desktop.imagemap.coordinates", "Coordinates and sampling");
    public static final TextKey IMAGEMAP_BINDING_KEY = TextKey.of("iris.desktop.imagemap.binding_key", "Binding key");
    public static final TextKey IMAGEMAP_MAP_KEY = TextKey.of("iris.desktop.imagemap.map_key", "Map key");
    public static final TextKey IMAGEMAP_IMAGE_KEY = TextKey.of("iris.desktop.imagemap.image_key", "Image key");
    public static final TextKey IMAGEMAP_TYPE = TextKey.of("iris.desktop.imagemap.type", "Type");
    public static final TextKey IMAGEMAP_APPLICATION = TextKey.of("iris.desktop.imagemap.application", "Application");
    public static final TextKey IMAGEMAP_BLOCKS_PER_PIXEL = TextKey.of("iris.desktop.imagemap.blocks_per_pixel", "Blocks per pixel");
    public static final TextKey IMAGEMAP_ORIGIN_X = TextKey.of("iris.desktop.imagemap.origin_x", "World origin X");
    public static final TextKey IMAGEMAP_ORIGIN_Z = TextKey.of("iris.desktop.imagemap.origin_z", "World origin Z");
    public static final TextKey IMAGEMAP_SOURCE_ORIGIN_X = TextKey.of("iris.desktop.imagemap.source_origin_x", "Source origin X");
    public static final TextKey IMAGEMAP_SOURCE_ORIGIN_Z = TextKey.of("iris.desktop.imagemap.source_origin_z", "Source origin Z");
    public static final TextKey IMAGEMAP_ROTATION = TextKey.of("iris.desktop.imagemap.rotation", "Rotation");
    public static final TextKey IMAGEMAP_MIRROR_X = TextKey.of("iris.desktop.imagemap.mirror_x", "Mirror X");
    public static final TextKey IMAGEMAP_MIRROR_Z = TextKey.of("iris.desktop.imagemap.mirror_z", "Mirror Z");
    public static final TextKey IMAGEMAP_SAMPLING = TextKey.of("iris.desktop.imagemap.sampling", "Sampling");
    public static final TextKey IMAGEMAP_OUT_OF_BOUNDS = TextKey.of("iris.desktop.imagemap.out_of_bounds", "Out of bounds");
    public static final TextKey IMAGEMAP_ALPHA = TextKey.of("iris.desktop.imagemap.alpha", "Alpha policy");
    public static final TextKey IMAGEMAP_FALLBACK_VALUE = TextKey.of("iris.desktop.imagemap.fallback_value", "Fallback value");
    public static final TextKey IMAGEMAP_FALLBACK_TARGET = TextKey.of("iris.desktop.imagemap.fallback_target", "Fallback target");
    public static final TextKey IMAGEMAP_MINIMUM_HEIGHT = TextKey.of("iris.desktop.imagemap.minimum_height", "Minimum height");
    public static final TextKey IMAGEMAP_MAXIMUM_HEIGHT = TextKey.of("iris.desktop.imagemap.maximum_height", "Maximum height");
    public static final TextKey IMAGEMAP_VERTICAL_OFFSET = TextKey.of("iris.desktop.imagemap.vertical_offset", "Vertical offset");
    public static final TextKey IMAGEMAP_CLAMP = TextKey.of("iris.desktop.imagemap.clamp", "Clamp height");
    public static final TextKey IMAGEMAP_INVERTED = TextKey.of("iris.desktop.imagemap.inverted", "Invert values");
    public static final TextKey IMAGEMAP_CURVE_EXPONENT = TextKey.of("iris.desktop.imagemap.curve_exponent", "Curve exponent");
    public static final TextKey IMAGEMAP_SMOOTHING_RADIUS = TextKey.of("iris.desktop.imagemap.smoothing_radius", "Smoothing radius");
    public static final TextKey IMAGEMAP_THRESHOLD = TextKey.of("iris.desktop.imagemap.threshold", "Threshold");
    public static final TextKey IMAGEMAP_FALLOFF = TextKey.of("iris.desktop.imagemap.falloff", "Falloff");
    public static final TextKey IMAGEMAP_COLOR_TOLERANCE = TextKey.of("iris.desktop.imagemap.color_tolerance", "Raw sRGB tolerance");
    public static final TextKey IMAGEMAP_UNKNOWN_COLOR = TextKey.of("iris.desktop.imagemap.unknown_color", "Unknown color");
    public static final TextKey IMAGEMAP_ADD_COLOR = TextKey.of("iris.desktop.imagemap.add_color", "Add legend color");
    public static final TextKey IMAGEMAP_REMOVE_COLOR = TextKey.of("iris.desktop.imagemap.remove_color", "Remove selected");
    public static final TextKey IMAGEMAP_COMPOSED_MASKS = TextKey.of("iris.desktop.imagemap.composed_masks", "Composed masks");
    public static final TextKey IMAGEMAP_ADD_MASK = TextKey.of("iris.desktop.imagemap.add_mask", "Add mask binding");
    public static final TextKey IMAGEMAP_HEIGHT = TextKey.of("iris.desktop.imagemap.height", "Height interpretation");
    public static final TextKey IMAGEMAP_MASK = TextKey.of("iris.desktop.imagemap.mask", "Mask interpretation");
    public static final TextKey IMAGEMAP_COLOR_MAP = TextKey.of("iris.desktop.imagemap.color_map", "Color legend");
    public static final TextKey IMAGEMAP_OVERLAYS = TextKey.of("iris.desktop.imagemap.overlays", "World overlays");
    public static final TextKey IMAGEMAP_CHUNKS = TextKey.of("iris.desktop.imagemap.chunks", "16-block chunks");
    public static final TextKey IMAGEMAP_REGIONS = TextKey.of("iris.desktop.imagemap.regions", "512-block regions");
    public static final TextKey IMAGEMAP_BOUNDARY = TextKey.of("iris.desktop.imagemap.boundary", "World boundary");
    public static final TextKey IMAGEMAP_COVERAGE = TextKey.of("iris.desktop.imagemap.coverage", "Source coverage");
    public static final TextKey IMAGEMAP_DIAGNOSTICS = TextKey.of("iris.desktop.imagemap.diagnostics", "Diagnostics");
    public static final TextKey IMAGEMAP_READY = TextKey.of("iris.desktop.imagemap.ready", "Ready");
    public static final TextKey IMAGEMAP_NO_SOURCE = TextKey.of("iris.desktop.imagemap.no_source", "Import a PNG source to begin.");
    public static final TextKey IMAGEMAP_LOADING = TextKey.of("iris.desktop.imagemap.loading", "Loading preset...");
    public static final TextKey IMAGEMAP_LOAD_FAILED = TextKey.of("iris.desktop.imagemap.load_failed", "Preset load failed");
    public static final TextKey IMAGEMAP_PREVIEWING = TextKey.of("iris.desktop.imagemap.previewing", "Compiling preview...");
    public static final TextKey IMAGEMAP_PREVIEW_VALID = TextKey.of("iris.desktop.imagemap.preview_valid", "Runtime compiler validation passed.");
    public static final TextKey IMAGEMAP_PREVIEW_FAILED = TextKey.of("iris.desktop.imagemap.preview_failed", "Preview failed");
    public static final TextKey IMAGEMAP_EXPORTING = TextKey.of("iris.desktop.imagemap.exporting", "Validating and exporting...");
    public static final TextKey IMAGEMAP_EXPORTED = TextKey.of("iris.desktop.imagemap.exported", "Exported image map, PNG, and dimension binding atomically.");
    public static final TextKey IMAGEMAP_EXPORT_FAILED = TextKey.of("iris.desktop.imagemap.export_failed", "Export failed");
    public static final TextKey IMAGEMAP_SOURCE = TextKey.of("iris.desktop.imagemap.source", "Source pixels");
    public static final TextKey IMAGEMAP_INTERPRETED = TextKey.of("iris.desktop.imagemap.interpreted", "Runtime interpretation");
    public static final TextKey IMAGEMAP_NO_PREVIEW = TextKey.of("iris.desktop.imagemap.no_preview", "Compile a valid preview to inspect world output.");
    public static final TextKey IMAGEMAP_PREVIEW_STATUS = TextKey.of("iris.desktop.imagemap.preview_status", "X {x}  Z {z}  |  {value}  |  {scale} blocks/pixel");

    private static final List<MessageKey> KEYS = List.of(
            VISION_TITLE, VISION_VIEW, VISION_GRID, VISION_ENTITIES, VISION_FOLLOW,
            VISION_REFRESHING, VISION_FPS, VISION_ZOOM_RESET, VISION_GRID_ENABLED, VISION_GRID_DISABLED,
            VISION_ENTITIES_ENABLED, VISION_ENTITIES_DISABLED,
            VISION_FOLLOWING, VISION_NO_PLAYER, VISION_FOLLOW_DISABLED,
            VISION_STATUS_LEFT, VISION_STATUS_RIGHT, VISION_ENTITY_POSITION, VISION_ENTITY_HEALTH,
            VISION_BLOCK_POSITION, VISION_CHUNK_POSITION, VISION_REGION_POSITION, VISION_BIOME_KEY,
            VISION_BIOME_FILE, VISION_VELOCITY, VISION_TILES, VISION_WORKERS, VISION_CENTER,
            VISION_HELP_TOGGLE, VISION_HELP_REFRESH, VISION_HELP_FOLLOW, VISION_HELP_ZOOM,
            VISION_HELP_RESET_ZOOM, VISION_HELP_CYCLE_MODE, VISION_HELP_FPS,
            VISION_HELP_GRID, VISION_HELP_BIOME, VISION_HELP_TELEPORT, VISION_HELP_EDITOR, VISION_OPENED,
            VISION_TELEPORTING, VISION_MODE_BIOME, VISION_MODE_BIOME_LAND, VISION_MODE_BIOME_SEA,
            VISION_MODE_REGION, VISION_MODE_CAVE_LAND, VISION_MODE_RIVER, VISION_MODE_HEIGHT, VISION_MODE_OBJECT_LOAD,
            VISION_MODE_DECORATOR_LOAD, VISION_MODE_CONTINENT, VISION_MODE_LAYER_LOAD, NOISE_TITLE,
            NOISE_TITLE_GENERATOR, NOISE_SEARCH, NOISE_STATUS, NOISE_CATEGORY_CUSTOM,
            NOISE_CATEGORY_PACK_GENERATORS, NOISE_CATEGORY_SIMPLEX, NOISE_CATEGORY_PERLIN,
            NOISE_CATEGORY_CELLULAR, NOISE_CATEGORY_IRIS, NOISE_CATEGORY_CLOVER, NOISE_CATEGORY_HEXAGON,
            NOISE_CATEGORY_VASCULAR, NOISE_CATEGORY_GLOBE, NOISE_CATEGORY_CUBIC, NOISE_CATEGORY_FRACTAL,
            NOISE_CATEGORY_STATIC, NOISE_CATEGORY_NOWHERE, NOISE_CATEGORY_SIERPINSKI,
            NOISE_CATEGORY_UTILITY, NOISE_CATEGORY_OTHER,
            PREGEN_INITIALIZING, PREGEN_TITLE, PREGEN_METHOD_PENDING, PREGEN_PAUSED,
            PREGEN_RESUME_HINT, PREGEN_PAUSE_HINT, PREGEN_PAUSE, PREGEN_RESUME,
            PREGEN_GENERATING, PREGEN_SAVING, PREGEN_STOPPING, PREGEN_COMPLETED,
            PREGEN_ERROR, PREGEN_CONTROL_FAILED, PREGEN_ERROR_DETAILS, PREGEN_PROGRESS,
            PREGEN_PERCENT, PREGEN_CURRENT, PREGEN_OVERALL, PREGEN_THIRTY,
            PREGEN_SIXTY, PREGEN_RATE, PREGEN_ETA, PREGEN_ELAPSED,
            PREGEN_MEMORY_LABEL, PREGEN_MEMORY_USAGE, PREGEN_PRESSURE, PREGEN_PRESSURE_VALUE,
            PREGEN_METHOD, PREGEN_CACHED, PREGEN_FAILED, PREGEN_MAP,
            PREGEN_BOUNDS, PREGEN_WAITING, PREGEN_READY, PREGEN_EXISTING,
            PREGEN_NETWORK, PREGEN_TERRAIN_HINT,
            IMAGEMAP_TITLE, IMAGEMAP_PRESET, IMAGEMAP_LOAD, IMAGEMAP_IMPORT_PNG,
            IMAGEMAP_REPLACE_PNG, IMAGEMAP_PREVIEW, IMAGEMAP_EXPORT, IMAGEMAP_METADATA,
            IMAGEMAP_RESOURCE, IMAGEMAP_COORDINATES, IMAGEMAP_BINDING_KEY, IMAGEMAP_MAP_KEY,
            IMAGEMAP_IMAGE_KEY, IMAGEMAP_TYPE, IMAGEMAP_APPLICATION, IMAGEMAP_BLOCKS_PER_PIXEL,
            IMAGEMAP_ORIGIN_X, IMAGEMAP_ORIGIN_Z, IMAGEMAP_SOURCE_ORIGIN_X, IMAGEMAP_SOURCE_ORIGIN_Z,
            IMAGEMAP_ROTATION, IMAGEMAP_MIRROR_X, IMAGEMAP_MIRROR_Z, IMAGEMAP_SAMPLING,
            IMAGEMAP_OUT_OF_BOUNDS, IMAGEMAP_ALPHA, IMAGEMAP_FALLBACK_VALUE, IMAGEMAP_FALLBACK_TARGET,
            IMAGEMAP_MINIMUM_HEIGHT, IMAGEMAP_MAXIMUM_HEIGHT, IMAGEMAP_VERTICAL_OFFSET, IMAGEMAP_CLAMP,
            IMAGEMAP_INVERTED, IMAGEMAP_CURVE_EXPONENT, IMAGEMAP_SMOOTHING_RADIUS, IMAGEMAP_THRESHOLD,
            IMAGEMAP_FALLOFF, IMAGEMAP_COLOR_TOLERANCE, IMAGEMAP_UNKNOWN_COLOR, IMAGEMAP_ADD_COLOR,
            IMAGEMAP_REMOVE_COLOR, IMAGEMAP_COMPOSED_MASKS, IMAGEMAP_ADD_MASK,
            IMAGEMAP_HEIGHT, IMAGEMAP_MASK, IMAGEMAP_COLOR_MAP,
            IMAGEMAP_OVERLAYS, IMAGEMAP_CHUNKS, IMAGEMAP_REGIONS, IMAGEMAP_BOUNDARY,
            IMAGEMAP_COVERAGE, IMAGEMAP_DIAGNOSTICS, IMAGEMAP_READY, IMAGEMAP_NO_SOURCE,
            IMAGEMAP_LOADING, IMAGEMAP_LOAD_FAILED, IMAGEMAP_PREVIEWING, IMAGEMAP_PREVIEW_VALID,
            IMAGEMAP_PREVIEW_FAILED, IMAGEMAP_EXPORTING, IMAGEMAP_EXPORTED, IMAGEMAP_EXPORT_FAILED,
            IMAGEMAP_SOURCE, IMAGEMAP_INTERPRETED, IMAGEMAP_NO_PREVIEW, IMAGEMAP_PREVIEW_STATUS
    );

    private DesktopUiMessages() {
    }

    public static List<MessageKey> keys() {
        return KEYS;
    }
}

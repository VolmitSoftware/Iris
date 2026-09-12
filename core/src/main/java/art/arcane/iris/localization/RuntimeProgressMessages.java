package art.arcane.iris.localization;

import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.PluralKey;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;
import java.util.Map;

public final class RuntimeProgressMessages {
    public static final TextKey STUDIO_OPENING = TextKey.of("iris.runtime.studio.opening", C.GOLD + "Studio " + C.AQUA + "OPENING");
    public static final TextKey STUDIO_OPENING_PROGRESS = TextKey.of("iris.runtime.studio.opening_progress", C.GOLD + "Studio " + C.AQUA + "OPENING" + C.GRAY + " {percent}%");
    public static final TextKey STUDIO_FAILED_PROGRESS = TextKey.of("iris.runtime.studio.failed_progress", C.GOLD + "Studio " + C.RED + "FAILED" + C.GRAY + " {percent}%");
    public static final TextKey STUDIO_READY_PROGRESS = TextKey.of("iris.runtime.studio.ready_progress", C.GOLD + "Studio " + C.GREEN + "READY" + C.GRAY + " 100%");
    public static final TextKey STUDIO_ACTION_FAILED = TextKey.of("iris.runtime.studio.action.failed", "{bar}" + C.GRAY + " " + C.RED + "FAILED" + C.GRAY + " | " + C.WHITE + "{stage}");
    public static final TextKey STUDIO_ACTION_READY = TextKey.of("iris.runtime.studio.action.ready", "{bar}" + C.GRAY + " " + C.GREEN + "100%" + C.GRAY + " | " + C.GREEN + "Studio ready" + C.DARK_GRAY + " {elapsed}");
    public static final TextKey STUDIO_ACTION_PROGRESS = TextKey.of("iris.runtime.studio.action.progress", "{bar}" + C.GRAY + " " + C.YELLOW + "{percent}%" + C.GRAY + " | " + C.WHITE + "{stage}" + C.DARK_GRAY + " {elapsed}");
    public static final TextKey STUDIO_CONSOLE_PROGRESS = TextKey.of("iris.runtime.studio.console.progress", C.GOLD + "Studio " + C.AQUA + "{bar}" + C.YELLOW + " {percent}%" + C.GRAY + " {stage}" + C.DARK_GRAY + " ({elapsed})");
    public static final TextKey STUDIO_STAGE_INITIALIZING = TextKey.of("iris.runtime.studio.stage.initializing", "Initializing");
    public static final TextKey STUDIO_STAGE_QUEUED = TextKey.of("iris.runtime.studio.stage.queued", "Queued");
    public static final TextKey STUDIO_STAGE_RESOLVE_DIMENSION = TextKey.of("iris.runtime.studio.stage.resolve_dimension", "Resolving dimension");
    public static final TextKey STUDIO_STAGE_PREPARE_WORLD_PACK = TextKey.of("iris.runtime.studio.stage.prepare_world_pack", "Preparing world pack");
    public static final TextKey STUDIO_STAGE_INSTALL_DATAPACKS = TextKey.of("iris.runtime.studio.stage.install_datapacks", "Installing datapacks");
    public static final TextKey STUDIO_STAGE_CREATE_WORLD = TextKey.of("iris.runtime.studio.stage.create_world", "Creating world");
    public static final TextKey STUDIO_STAGE_APPLY_WORLD_RULES = TextKey.of("iris.runtime.studio.stage.apply_world_rules", "Applying world rules");
    public static final TextKey STUDIO_STAGE_PREPARE_GENERATOR = TextKey.of("iris.runtime.studio.stage.prepare_generator", "Preparing generator");
    public static final TextKey STUDIO_STAGE_TELEPORT_PLAYER = TextKey.of("iris.runtime.studio.stage.teleport_player", "Teleporting");
    public static final TextKey STUDIO_STAGE_FINALIZE_OPEN = TextKey.of("iris.runtime.studio.stage.finalize_open", "Finalizing");
    public static final TextKey STUDIO_STAGE_CLEANUP = TextKey.of("iris.runtime.studio.stage.cleanup", "Cleaning up");
    public static final TextKey WORLD_CREATE_TELEPORT_FAILED = TextKey.of("iris.runtime.world_create.teleport_failed", C.YELLOW + "The world was created, but automatic teleport failed. Try /iris teleport world={world}");
    public static final TextKey WORLD_CREATE_LIFECYCLE_ACTION = TextKey.of("iris.runtime.world_create.lifecycle.action", "{bar}" + C.GRAY + " " + C.YELLOW + "{percent}%" + C.GRAY + " | " + C.WHITE + "{stage}{detail}" + C.DARK_GRAY + " {elapsed}");
    public static final TextKey WORLD_CREATE_LIFECYCLE_ACTION_FAILED = TextKey.of("iris.runtime.world_create.lifecycle.action.failed", "{bar}" + C.GRAY + " " + C.RED + "FAILED" + C.GRAY + " | " + C.WHITE + "{stage}{detail}" + C.DARK_GRAY + " {elapsed}");
    public static final TextKey WORLD_CREATE_LIFECYCLE_ACTION_READY = TextKey.of("iris.runtime.world_create.lifecycle.action.ready", "{bar}" + C.GRAY + " " + C.GREEN + "100%" + C.GRAY + " | " + C.GREEN + "World ready" + C.DARK_GRAY + " {elapsed}");
    public static final TextKey WORLD_CREATE_LIFECYCLE_CONSOLE = TextKey.of("iris.runtime.world_create.lifecycle.console", C.GOLD + "World " + C.AQUA + "{world} {bar} " + C.YELLOW + "{percent}%" + C.GRAY + " {stage}{detail}" + C.DARK_GRAY + " ({elapsed})");
    public static final TextKey WORLD_CREATE_LIFECYCLE_CONSOLE_FAILED = TextKey.of("iris.runtime.world_create.lifecycle.console.failed", C.GOLD + "World " + C.AQUA + "{world}" + C.GRAY + " | " + C.RED + "creation failed" + C.DARK_GRAY + " after {elapsed}");
    public static final TextKey WORLD_CREATE_LIFECYCLE_CONSOLE_READY = TextKey.of("iris.runtime.world_create.lifecycle.console.ready", C.GOLD + "World " + C.AQUA + "{world}" + C.GRAY + " | " + C.GREEN + "ready" + C.DARK_GRAY + " in {elapsed}");
    public static final TextKey WORLD_CREATE_STAGE_INITIALIZING = TextKey.of("iris.runtime.world_create.stage.initializing", "Initializing");
    public static final TextKey WORLD_CREATE_STAGE_RESOLVE_DIMENSION = TextKey.of("iris.runtime.world_create.stage.resolve_dimension", "Resolving dimension");
    public static final TextKey WORLD_CREATE_STAGE_VALIDATE_PACK = TextKey.of("iris.runtime.world_create.stage.validate_pack", "Validating pack");
    public static final TextKey WORLD_CREATE_STAGE_PREPARE_WORLD_PACK = TextKey.of("iris.runtime.world_create.stage.prepare_world_pack", "Preparing world pack");
    public static final TextKey WORLD_CREATE_STAGE_INSTALL_DATAPACKS = TextKey.of("iris.runtime.world_create.stage.install_datapacks", "Installing datapacks");
    public static final TextKey WORLD_CREATE_STAGE_PREPARE_GENERATOR = TextKey.of("iris.runtime.world_create.stage.prepare_generator", "Preparing generator");
    public static final TextKey WORLD_CREATE_STAGE_CREATE_WORLD = TextKey.of("iris.runtime.world_create.stage.create_world", "Generating spawn");
    public static final TextKey WORLD_CREATE_STAGE_REGISTER_WORLD = TextKey.of("iris.runtime.world_create.stage.register_world", "Registering world");
    public static final TextKey WORLD_CREATE_STAGE_TELEPORT_PLAYER = TextKey.of("iris.runtime.world_create.stage.teleport_player", "Entering world");
    public static final TextKey WORLD_CREATE_STAGE_PREGENERATE = TextKey.of("iris.runtime.world_create.stage.pregenerate", "Pregenerating");
    public static final TextKey WORLD_CREATE_STAGE_FINALIZE = TextKey.of("iris.runtime.world_create.stage.finalize", "Finalizing");
    public static final TextKey WORLD_CREATE_STAGE_COMPLETE = TextKey.of("iris.runtime.world_create.stage.complete", "World ready");
    public static final TextKey WORLD_REPLACE_START = TextKey.of("iris.runtime.world_replace.start", C.YELLOW + "Preparing Iris replacement for {world}. The current world remains active until restart.");
    public static final TextKey WORLD_REPLACE_PROGRESS = TextKey.of("iris.runtime.world_replace.progress", C.YELLOW + "Replacement for {world}: {stage} ({elapsed}s elapsed)");
    public static final TextKey WORLD_REPLACE_STAGE_DATAPACKS = TextKey.of("iris.runtime.world_replace.stage.datapacks", "Checking dimension datapacks");
    public static final TextKey WORLD_REPLACE_STAGE_SEED = TextKey.of("iris.runtime.world_replace.stage.seed", "Preparing the replacement seed");
    public static final TextKey WORLD_REPLACE_STAGE_PACK = TextKey.of("iris.runtime.world_replace.stage.pack", "Copying and validating the dimension pack");
    public static final TextKey WORLD_REPLACE_STAGE_PLAYERS = TextKey.of("iris.runtime.world_replace.stage.players", "Recording player entry protection");
    public static final TextKey WORLD_REPLACE_STAGE_VERIFY = TextKey.of("iris.runtime.world_replace.stage.verify", "Verifying the staged replacement");
    public static final TextKey WORLD_REPLACE_STAGE_SAVE = TextKey.of("iris.runtime.world_replace.stage.save", "Saving the replacement for restart");
    public static final TextKey WORLD_REPLACE_STAGE_CLEANUP = TextKey.of("iris.runtime.world_replace.stage.cleanup", "Cleaning up the failed replacement");
    public static final TextKey WORLD_PREGEN_ACTION = TextKey.of("iris.runtime.world_create.pregen.action", "{bar}" + C.GRAY + " " + C.YELLOW + "{percent}%" + C.GRAY + " | " + C.WHITE + "Pregenerating");
    public static final TextKey WORLD_PREGEN_CONSOLE = TextKey.of("iris.runtime.world_create.pregen.console", C.GOLD + "Pregenerating " + C.YELLOW + "{percent}%");
    public static final TextKey CHUNK_TITLE_REGEN = TextKey.of("iris.runtime.chunk_job.title.regen", "Regen");
    public static final TextKey CHUNK_TITLE_DELETE = TextKey.of("iris.runtime.chunk_job.title.delete", "Delete");
    public static final TextKey CHUNK_TITLE_GOLDEN_HASH = TextKey.of("iris.runtime.chunk_job.title.golden_hash", "GoldenHash");
    public static final TextKey CHUNK_STAGE_PREPARING = TextKey.of("iris.runtime.chunk_job.stage.preparing", "Preparing");
    public static final TextKey CHUNK_STAGE_CLEARING = TextKey.of("iris.runtime.chunk_job.stage.clearing", "Clearing");
    public static final TextKey CHUNK_STAGE_RESETTING_MANTLE = TextKey.of("iris.runtime.chunk_job.stage.resetting_mantle", "Resetting mantle");
    public static final TextKey CHUNK_STAGE_REGENERATING = TextKey.of("iris.runtime.chunk_job.stage.regenerating", "Regenerating");
    public static final TextKey CHUNK_STAGE_GENERATING = TextKey.of("iris.runtime.chunk_job.stage.generating", "Generating");
    public static final TextKey CHUNK_STAGE_COMPARING = TextKey.of("iris.runtime.chunk_job.stage.comparing", "Comparing");
    public static final TextKey CHUNK_STAGE_DIAGNOSING = TextKey.of("iris.runtime.chunk_job.stage.diagnosing", "Diagnosing");
    public static final TextKey CHUNK_BOSSBAR_WORKING = TextKey.of("iris.runtime.chunk_job.bossbar.working", C.GOLD + "{title} " + C.AQUA + "WORKING");
    public static final TextKey CHUNK_BOSSBAR_PROGRESS = TextKey.of("iris.runtime.chunk_job.bossbar.progress", C.GOLD + "{title} " + C.AQUA + "{stage}" + C.GRAY + " " + C.YELLOW + "{percent}%");
    public static final TextKey CHUNK_ACTION_PROGRESS = TextKey.of("iris.runtime.chunk_job.action.progress", "{bar}" + C.GRAY + " " + C.YELLOW + "{percent}%" + C.GRAY + " | " + C.WHITE + "{stage} {applied}/{total}");
    public static final TextKey CHUNK_SUMMARY = TextKey.of("iris.runtime.chunk_job.summary", "{applied}/{total} chunks in {elapsed}");
    public static final TextKey CHUNK_SUMMARY_FAILED = TextKey.of("iris.runtime.chunk_job.summary.failed", "{applied}/{total} chunks in {elapsed} ({failures} failed)");
    public static final TextKey CHUNK_BOSSBAR_DONE = TextKey.of("iris.runtime.chunk_job.bossbar.done", C.GOLD + "{title} " + C.GREEN + "DONE" + C.GRAY + " " + C.YELLOW + "{summary}");
    public static final TextKey CHUNK_BOSSBAR_FAILED = TextKey.of("iris.runtime.chunk_job.bossbar.failed", C.GOLD + "{title} " + C.RED + "FAILED" + C.GRAY + " " + C.YELLOW + "{summary}");
    public static final TextKey CHUNK_ACTION_DONE = TextKey.of("iris.runtime.chunk_job.action.done", "{bar}" + C.GRAY + " " + C.GREEN + "Done" + C.GRAY + " | " + C.WHITE + "{summary}");
    public static final TextKey CHUNK_ACTION_FAILED = TextKey.of("iris.runtime.chunk_job.action.failed", "{bar}" + C.GRAY + " " + C.RED + "Failed" + C.GRAY + " | " + C.WHITE + "{summary}");
    public static final TextKey CHUNK_COMPLETE = TextKey.of("iris.runtime.chunk_job.complete", C.GREEN + "{title} complete: {summary}");
    public static final TextKey CHUNK_FAILED = TextKey.of("iris.runtime.chunk_job.failed", C.RED + "{title} finished with errors: {summary}");
    public static final TextKey GOLDEN_NO_CAPTURE = TextKey.of("iris.runtime.golden.no_capture", "No golden capture at {path}; run a capture first.");
    public static final PluralKey GOLDEN_ABORTED = PluralKey.of(
            "iris.runtime.golden.aborted",
            "failed",
            Map.of(
                    "one", "GoldenHash aborted: {failed} chunk failed to generate. No golden file written.",
                    "other", "GoldenHash aborted: {failed} chunks failed to generate. No golden file written."
            )
    );
    public static final TextKey GOLDEN_FAILED = TextKey.of("iris.runtime.golden.failed", "GoldenHash failed: {error}");
    public static final TextKey GOLDEN_MANTLE_RESET = TextKey.of("iris.runtime.golden.mantle_reset", "Mantle reset ({path})");
    public static final TextKey GOLDEN_MANTLE_RESET_FAILED = TextKey.of("iris.runtime.golden.mantle_reset_failed", "Mantle reset failed ({type}); continuing with existing mantle state.");
    public static final TextKey GOLDEN_CAPTURED = TextKey.of("iris.runtime.golden.captured", "Golden captured: {chunks} chunks combined={hash}");
    public static final TextKey GOLDEN_WRONG_WORLD = TextKey.of("iris.runtime.golden.wrong_world", "Golden file is for dim={goldenDimension} seed={goldenSeed} but this world is dim={dimension} seed={seed}. Aborting.");
    public static final TextKey GOLDEN_VERSION_WARNING = TextKey.of("iris.runtime.golden.version_warning", "Golden was captured on mc={goldenVersion}, running mc={version}. Diffs may be version-induced.");
    public static final TextKey GOLDEN_MATCH = TextKey.of("iris.runtime.golden.match", "GOLDEN MATCH: {current}/{golden} chunks, combined={hash}");
    public static final TextKey GOLDEN_MISMATCH = TextKey.of("iris.runtime.golden.mismatch", "GOLDEN MISMATCH: {mismatches}/{chunks} chunks differ.");
    public static final TextKey GOLDEN_MISMATCH_CHUNK = TextKey.of("iris.runtime.golden.mismatch_chunk", "  chunk {chunk}");
    public static final TextKey GOLDEN_MISSING_IN_GOLDEN = TextKey.of("iris.runtime.golden.missing_in_golden", "{chunk} (missing in golden)");
    public static final TextKey GOLDEN_MISMATCH_MORE = TextKey.of("iris.runtime.golden.mismatch_more", "  ... and {count} more");
    public static final TextKey GOLDEN_CURRENT_WRITTEN = TextKey.of("iris.runtime.golden.current_written", "Current hashes written to {file}");
    public static final TextKey GOLDEN_DIAG_STABLE = TextKey.of("iris.runtime.golden.diag.stable", "Repeat-gen STABLE, mantle-reset {mantleStatus} -> {file}");
    public static final TextKey GOLDEN_DIAG_UNSTABLE = TextKey.of("iris.runtime.golden.diag.unstable", "Repeat-gen UNSTABLE ({diffs}+ block diffs), mantle-reset {mantleStatus} -> {file}");
    public static final TextKey GOLDEN_MANTLE_STABLE = TextKey.of("iris.runtime.golden.diag.mantle_stable", "STABLE (mantle rebuild reproduces scan output)");
    public static final TextKey GOLDEN_MANTLE_DIVERGED = TextKey.of("iris.runtime.golden.diag.mantle_diverged", "DIVERGED ({diffs}+ diffs - mantle build is state/order dependent)");
    public static final TextKey GOLDEN_MANTLE_SKIPPED = TextKey.of("iris.runtime.golden.diag.mantle_skipped", "SKIPPED ({type})");
    public static final TextKey GOLDEN_DIAG_FAILED = TextKey.of("iris.runtime.golden.diag.failed", "Diagnosis failed: {error}");
    public static final TextKey GOLDEN_STARTED = TextKey.of("iris.runtime.golden.started", "GoldenHash started: {chunks} chunks around 0,0 in buffers (world untouched), threads={threads} mode={mode}");
    public static final TextKey GOLDEN_CHUNK_HASHED = TextKey.of("iris.runtime.golden.chunk_hashed", "[{done}/{total}] chunk {x},{z} hashed");
    public static final TextKey GOLDEN_CHUNK_FAILED = TextKey.of("iris.runtime.golden.chunk_failed", "Chunk {x},{z} FAILED: {type}");

    private static final List<MessageKey> KEYS = List.of(
            STUDIO_OPENING,
            STUDIO_OPENING_PROGRESS,
            STUDIO_FAILED_PROGRESS,
            STUDIO_READY_PROGRESS,
            STUDIO_ACTION_FAILED,
            STUDIO_ACTION_READY,
            STUDIO_ACTION_PROGRESS,
            STUDIO_CONSOLE_PROGRESS,
            STUDIO_STAGE_INITIALIZING,
            STUDIO_STAGE_QUEUED,
            STUDIO_STAGE_RESOLVE_DIMENSION,
            STUDIO_STAGE_PREPARE_WORLD_PACK,
            STUDIO_STAGE_INSTALL_DATAPACKS,
            STUDIO_STAGE_CREATE_WORLD,
            STUDIO_STAGE_APPLY_WORLD_RULES,
            STUDIO_STAGE_PREPARE_GENERATOR,
            STUDIO_STAGE_TELEPORT_PLAYER,
            STUDIO_STAGE_FINALIZE_OPEN,
            STUDIO_STAGE_CLEANUP,
            WORLD_CREATE_TELEPORT_FAILED,
            WORLD_CREATE_LIFECYCLE_ACTION,
            WORLD_CREATE_LIFECYCLE_ACTION_FAILED,
            WORLD_CREATE_LIFECYCLE_ACTION_READY,
            WORLD_CREATE_LIFECYCLE_CONSOLE,
            WORLD_CREATE_LIFECYCLE_CONSOLE_FAILED,
            WORLD_CREATE_LIFECYCLE_CONSOLE_READY,
            WORLD_CREATE_STAGE_INITIALIZING,
            WORLD_CREATE_STAGE_RESOLVE_DIMENSION,
            WORLD_CREATE_STAGE_VALIDATE_PACK,
            WORLD_CREATE_STAGE_PREPARE_WORLD_PACK,
            WORLD_CREATE_STAGE_INSTALL_DATAPACKS,
            WORLD_CREATE_STAGE_PREPARE_GENERATOR,
            WORLD_CREATE_STAGE_CREATE_WORLD,
            WORLD_CREATE_STAGE_REGISTER_WORLD,
            WORLD_CREATE_STAGE_TELEPORT_PLAYER,
            WORLD_CREATE_STAGE_PREGENERATE,
            WORLD_CREATE_STAGE_FINALIZE,
            WORLD_CREATE_STAGE_COMPLETE,
            WORLD_REPLACE_START,
            WORLD_REPLACE_PROGRESS,
            WORLD_REPLACE_STAGE_DATAPACKS,
            WORLD_REPLACE_STAGE_SEED,
            WORLD_REPLACE_STAGE_PACK,
            WORLD_REPLACE_STAGE_PLAYERS,
            WORLD_REPLACE_STAGE_VERIFY,
            WORLD_REPLACE_STAGE_SAVE,
            WORLD_REPLACE_STAGE_CLEANUP,
            WORLD_PREGEN_ACTION,
            WORLD_PREGEN_CONSOLE,
            CHUNK_TITLE_REGEN,
            CHUNK_TITLE_DELETE,
            CHUNK_TITLE_GOLDEN_HASH,
            CHUNK_STAGE_PREPARING,
            CHUNK_STAGE_CLEARING,
            CHUNK_STAGE_RESETTING_MANTLE,
            CHUNK_STAGE_REGENERATING,
            CHUNK_STAGE_GENERATING,
            CHUNK_STAGE_COMPARING,
            CHUNK_STAGE_DIAGNOSING,
            CHUNK_BOSSBAR_WORKING,
            CHUNK_BOSSBAR_PROGRESS,
            CHUNK_ACTION_PROGRESS,
            CHUNK_SUMMARY,
            CHUNK_SUMMARY_FAILED,
            CHUNK_BOSSBAR_DONE,
            CHUNK_BOSSBAR_FAILED,
            CHUNK_ACTION_DONE,
            CHUNK_ACTION_FAILED,
            CHUNK_COMPLETE,
            CHUNK_FAILED,
            GOLDEN_NO_CAPTURE,
            GOLDEN_ABORTED,
            GOLDEN_FAILED,
            GOLDEN_MANTLE_RESET,
            GOLDEN_MANTLE_RESET_FAILED,
            GOLDEN_CAPTURED,
            GOLDEN_WRONG_WORLD,
            GOLDEN_VERSION_WARNING,
            GOLDEN_MATCH,
            GOLDEN_MISMATCH,
            GOLDEN_MISMATCH_CHUNK,
            GOLDEN_MISSING_IN_GOLDEN,
            GOLDEN_MISMATCH_MORE,
            GOLDEN_CURRENT_WRITTEN,
            GOLDEN_DIAG_STABLE,
            GOLDEN_DIAG_UNSTABLE,
            GOLDEN_MANTLE_STABLE,
            GOLDEN_MANTLE_DIVERGED,
            GOLDEN_MANTLE_SKIPPED,
            GOLDEN_DIAG_FAILED,
            GOLDEN_STARTED,
            GOLDEN_CHUNK_HASHED,
            GOLDEN_CHUNK_FAILED
    );

    private RuntimeProgressMessages() {
    }

    public static List<MessageKey> keys() {
        return KEYS;
    }
}

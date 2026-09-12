package art.arcane.iris.localization;

import art.arcane.volmlib.util.localization.LinesKey;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.PluralKey;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;
import java.util.Map;

public final class PackDownloadMessages {
    public static final TextKey PROGRESS_START = TextKey.of(
            "iris.runtime.pack_download.progress.start",
            C.IRIS + "Iris " + C.GOLD + "PACK DOWNLOAD" + C.DARK_GRAY + " | " + C.WHITE + "{source}"
    );
    public static final TextKey PROGRESS_PHASE = TextKey.of(
            "iris.runtime.pack_download.progress.phase",
            C.IRIS + "Iris " + C.AQUA + "{phase}" + C.DARK_GRAY + " | " + C.GRAY + "{source}"
    );
    public static final TextKey PROGRESS_DETERMINATE = TextKey.of(
            "iris.runtime.pack_download.progress.determinate",
            "{bar}" + C.GRAY + " " + C.YELLOW + "{percent}%" + C.DARK_GRAY + " | "
                    + C.WHITE + "{transferred}" + C.GRAY + "/" + C.WHITE + "{total}"
                    + C.DARK_GRAY + " | " + C.AQUA + "{rate}/s"
    );
    public static final TextKey PROGRESS_INDETERMINATE = TextKey.of(
            "iris.runtime.pack_download.progress.indeterminate",
            "{bar}" + C.GRAY + " " + C.AQUA + "{phase}" + C.DARK_GRAY + " | "
                    + C.WHITE + "{transferred}" + C.DARK_GRAY + " | " + C.AQUA + "{rate}/s"
    );
    public static final TextKey PROGRESS_DETAIL = TextKey.of(
            "iris.runtime.pack_download.progress.detail",
            C.DARK_GRAY + "  - " + C.GRAY + "{detail}"
    );
    public static final TextKey PROGRESS_COMPLETE = TextKey.of(
            "iris.runtime.pack_download.progress.complete",
            C.GREEN + "Iris pack '{pack}' installed" + C.DARK_GRAY + " | "
                    + C.WHITE + "{transferred}" + C.GRAY + " in " + C.WHITE + "{elapsed}"
    );
    public static final TextKey PROGRESS_UNCHANGED = TextKey.of(
            "iris.runtime.pack_download.progress.unchanged",
            C.YELLOW + "Iris pack '{pack}' is already installed."
    );
    public static final TextKey PROGRESS_FAILED = TextKey.of(
            "iris.runtime.pack_download.progress.failed",
            C.RED + "Iris pack download failed." + C.GRAY + " Review the download details above and retry."
    );
    public static final TextKey PROGRESS_FAILED_DETAIL = TextKey.of(
            "iris.runtime.pack_download.progress.failed_detail",
            C.RED + "Iris pack download failed." + C.GRAY + " {error}"
    );
    public static final TextKey PROGRESS_CANCELLED = TextKey.of(
            "iris.runtime.pack_download.progress.cancelled",
            C.YELLOW + "Iris pack download cancelled before publication."
    );
    public static final TextKey PROGRESS_RESTART = TextKey.of(
            "iris.runtime.pack_download.progress.restart",
            C.GOLD + "Restart required" + C.DARK_GRAY + " | "
                    + C.GRAY + "Restart the server before creating or replacing a world with this pack."
    );
    public static final TextKey PROGRESS_PHASE_CONNECTING = TextKey.of(
            "iris.runtime.pack_download.progress.phase.connecting",
            "Connecting"
    );
    public static final TextKey PROGRESS_PHASE_DOWNLOADING = TextKey.of(
            "iris.runtime.pack_download.progress.phase.downloading",
            "Downloading"
    );
    public static final TextKey PROGRESS_PHASE_UNPACKING = TextKey.of(
            "iris.runtime.pack_download.progress.phase.unpacking",
            "Unpacking"
    );
    public static final TextKey PROGRESS_PHASE_VALIDATING = TextKey.of(
            "iris.runtime.pack_download.progress.phase.validating",
            "Validating"
    );
    public static final TextKey PROGRESS_PHASE_PUBLISHING = TextKey.of(
            "iris.runtime.pack_download.progress.phase.publishing",
            "Publishing"
    );
    public static final TextKey PROGRESS_SOURCE_REMOTE = TextKey.of(
            "iris.runtime.pack_download.progress.source.remote",
            "Remote ZIP"
    );
    public static final TextKey INVALID_SOURCE = TextKey.of(
            "iris.runtime.pack_download.invalid_source",
            C.RED + "Choose exactly one source: /iris download pack=overworld, "
                    + "/iris download pack=underworld, or /iris download link=‹zip-url›."
    );
    public static final TextKey INVALID_URL = TextKey.of(
            "iris.runtime.pack_download.invalid_url",
            C.RED + "Iris requires a valid HTTP or HTTPS .zip URL."
    );
    public static final TextKey INVALID_BUILT_IN = TextKey.of(
            "iris.runtime.pack_download.invalid_built_in",
            C.RED + "Iris only provides built-in downloads for 'overworld' and 'underworld'."
    );
    public static final TextKey SHUTTING_DOWN = TextKey.of(
            "iris.runtime.pack_download.shutting_down",
            C.YELLOW + "Iris is shutting down and is not accepting pack downloads."
    );
    public static final TextKey DOWNLOADING = TextKey.of(
            "iris.runtime.pack_download.downloading",
            "Downloading {url}"
    );
    public static final TextKey UNPACKING = TextKey.of(
            "iris.runtime.pack_download.unpacking",
            "Unpacking {repository}"
    );
    public static final LinesKey UNPACK_FAILED = LinesKey.of(
            "iris.runtime.pack_download.unpack_failed",
            "Issue when unpacking. Please check/do the following:",
            "1. Do you have a functioning internet connection?",
            "2. Did the download corrupt?",
            "3. Try deleting the */plugins/iris/packs folder and re-download.",
            "4. Download the pack from the GitHub repo: https://github.com/IrisDimensions/overworld",
            "5. Contact support (if all other options do not help)"
    );
    public static final TextKey NO_EXTRACTED_FILES = TextKey.of(
            "iris.runtime.pack_download.no_extracted_files",
            "No files were extracted from the zip file."
    );
    public static final TextKey HOME_DIRECTORY_ERROR = TextKey.of(
            "iris.runtime.pack_download.home_directory_error",
            "Error when finding home directory. Are there any non-text characters in the file name?"
    );
    public static final TextKey INVALID_ARCHIVE_FORMAT = TextKey.of(
            "iris.runtime.pack_download.invalid_archive_format",
            "Invalid format. Missing root folder or too many folders!"
    );
    public static final TextKey NO_DIMENSION_FILE = TextKey.of(
            "iris.runtime.pack_download.no_dimension_file",
            "No dimension file found in the extracted zip file."
    );
    public static final TextKey CHECK_GITHUB = TextKey.of(
            "iris.runtime.pack_download.check_github",
            "Check that it is present on GitHub and report this to staff!"
    );
    public static final TextKey INVALID_DIMENSION = TextKey.of(
            "iris.runtime.pack_download.invalid_dimension",
            "Invalid dimension folder under dimensions/."
    );
    public static final TextKey IMPORTING = TextKey.of(
            "iris.runtime.pack_download.importing",
            "Importing {name} ({key})"
    );
    public static final TextKey DIMENSION_KEY_CONFLICT = TextKey.of(
            "iris.runtime.pack_download.dimension_key_conflict",
            "Another dimension in the packs folder is already using the key {key}. Import failed!"
    );
    public static final TextKey PACK_KEY_CONFLICT = TextKey.of(
            "iris.runtime.pack_download.pack_key_conflict",
            "Another pack is using the key {key}. Import failed!"
    );
    public static final TextKey ACQUIRED = TextKey.of(
            "iris.runtime.pack_download.acquired",
            "Successfully acquired {name}."
    );
    public static final TextKey ALREADY_INSTALLED = TextKey.of(
            "iris.runtime.pack_download.already_installed",
            "Pack {key} is already installed, skipping download."
    );
    public static final TextKey IN_PROGRESS = TextKey.of(
            "iris.runtime.pack_download.in_progress",
            "Another Iris pack download is already in progress. Wait for it to finish before retrying."
    );
    public static final TextKey VALIDATION_FAILED = TextKey.of(
            "iris.runtime.pack_download.validation_failed",
            "Pack '{pack}' failed validation; world and Studio creation will be refused. Reasons:"
    );
    public static final TextKey VALIDATION_REASON = TextKey.of(
            "iris.runtime.pack_download.validation_reason",
            "  - {reason}"
    );
    public static final PluralKey VALIDATED_WITH_WARNINGS = PluralKey.of(
            "iris.runtime.pack_download.validated_with_warnings",
            "count",
            Map.of(
                    "one", "Pack '{pack}' validated with {count} warning.",
                    "other", "Pack '{pack}' validated with {count} warnings."
            )
    );
    public static final TextKey VALIDATED = TextKey.of(
            "iris.runtime.pack_download.validated",
            "Pack '{pack}' validated."
    );

    private static final List<MessageKey> KEYS = List.of(
            PROGRESS_START,
            PROGRESS_PHASE,
            PROGRESS_DETERMINATE,
            PROGRESS_INDETERMINATE,
            PROGRESS_DETAIL,
            PROGRESS_COMPLETE,
            PROGRESS_UNCHANGED,
            PROGRESS_FAILED,
            PROGRESS_FAILED_DETAIL,
            PROGRESS_CANCELLED,
            PROGRESS_RESTART,
            PROGRESS_PHASE_CONNECTING,
            PROGRESS_PHASE_DOWNLOADING,
            PROGRESS_PHASE_UNPACKING,
            PROGRESS_PHASE_VALIDATING,
            PROGRESS_PHASE_PUBLISHING,
            PROGRESS_SOURCE_REMOTE,
            INVALID_SOURCE,
            INVALID_URL,
            INVALID_BUILT_IN,
            SHUTTING_DOWN,
            DOWNLOADING,
            UNPACKING,
            UNPACK_FAILED,
            NO_EXTRACTED_FILES,
            HOME_DIRECTORY_ERROR,
            INVALID_ARCHIVE_FORMAT,
            NO_DIMENSION_FILE,
            CHECK_GITHUB,
            INVALID_DIMENSION,
            IMPORTING,
            DIMENSION_KEY_CONFLICT,
            PACK_KEY_CONFLICT,
            ACQUIRED,
            ALREADY_INSTALLED,
            IN_PROGRESS,
            VALIDATION_FAILED,
            VALIDATION_REASON,
            VALIDATED_WITH_WARNINGS,
            VALIDATED
    );

    private PackDownloadMessages() {
    }

    public static List<MessageKey> keys() {
        return KEYS;
    }
}

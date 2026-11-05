package art.arcane.iris.core.localization;

import art.arcane.volmlib.util.localization.LinesKey;
import art.arcane.volmlib.util.localization.PluralKey;
import art.arcane.volmlib.util.localization.TextKey;

import static art.arcane.iris.core.localization.IrisMessages.lines;
import static art.arcane.iris.core.localization.IrisMessages.plural;
import static art.arcane.iris.core.localization.IrisMessages.text;

public final class PackDownloadMessages {
    public static final TextKey PROGRESS_START = text("iris.runtime.pack_download.progress.start");
    public static final TextKey PROGRESS_PHASE = text("iris.runtime.pack_download.progress.phase");
    public static final TextKey PROGRESS_DETERMINATE = text("iris.runtime.pack_download.progress.determinate");
    public static final TextKey PROGRESS_INDETERMINATE = text("iris.runtime.pack_download.progress.indeterminate");
    public static final TextKey PROGRESS_DETAIL = text("iris.runtime.pack_download.progress.detail");
    public static final TextKey PROGRESS_COMPLETE = text("iris.runtime.pack_download.progress.complete");
    public static final TextKey PROGRESS_UNCHANGED = text("iris.runtime.pack_download.progress.unchanged");
    public static final TextKey PROGRESS_FAILED = text("iris.runtime.pack_download.progress.failed");
    public static final TextKey PROGRESS_FAILED_DETAIL = text("iris.runtime.pack_download.progress.failed_detail");
    public static final TextKey PROGRESS_CANCELLED = text("iris.runtime.pack_download.progress.cancelled");
    public static final TextKey PROGRESS_RESTART = text("iris.runtime.pack_download.progress.restart");
    public static final TextKey PROGRESS_PHASE_CONNECTING = text("iris.runtime.pack_download.progress.phase.connecting");
    public static final TextKey PROGRESS_PHASE_DOWNLOADING = text("iris.runtime.pack_download.progress.phase.downloading");
    public static final TextKey PROGRESS_PHASE_UNPACKING = text("iris.runtime.pack_download.progress.phase.unpacking");
    public static final TextKey PROGRESS_PHASE_VALIDATING = text("iris.runtime.pack_download.progress.phase.validating");
    public static final TextKey PROGRESS_PHASE_PUBLISHING = text("iris.runtime.pack_download.progress.phase.publishing");
    public static final TextKey PROGRESS_SOURCE_REMOTE = text("iris.runtime.pack_download.progress.source.remote");
    public static final TextKey INVALID_SOURCE = text("iris.runtime.pack_download.invalid_source");
    public static final TextKey INVALID_URL = text("iris.runtime.pack_download.invalid_url");
    public static final TextKey INVALID_BUILT_IN = text("iris.runtime.pack_download.invalid_built_in");
    public static final TextKey SHUTTING_DOWN = text("iris.runtime.pack_download.shutting_down");
    public static final TextKey DOWNLOADING = text("iris.runtime.pack_download.downloading");
    public static final TextKey UNPACKING = text("iris.runtime.pack_download.unpacking");
    public static final LinesKey UNPACK_FAILED = lines("iris.runtime.pack_download.unpack_failed");
    public static final TextKey NO_EXTRACTED_FILES = text("iris.runtime.pack_download.no_extracted_files");
    public static final TextKey HOME_DIRECTORY_ERROR = text("iris.runtime.pack_download.home_directory_error");
    public static final TextKey INVALID_ARCHIVE_FORMAT = text("iris.runtime.pack_download.invalid_archive_format");
    public static final TextKey NO_DIMENSION_FILE = text("iris.runtime.pack_download.no_dimension_file");
    public static final TextKey CHECK_GITHUB = text("iris.runtime.pack_download.check_github");
    public static final TextKey INVALID_DIMENSION = text("iris.runtime.pack_download.invalid_dimension");
    public static final TextKey IMPORTING = text("iris.runtime.pack_download.importing");
    public static final TextKey DIMENSION_KEY_CONFLICT = text("iris.runtime.pack_download.dimension_key_conflict");
    public static final TextKey PACK_KEY_CONFLICT = text("iris.runtime.pack_download.pack_key_conflict");
    public static final TextKey ACQUIRED = text("iris.runtime.pack_download.acquired");
    public static final TextKey ALREADY_INSTALLED = text("iris.runtime.pack_download.already_installed");
    public static final TextKey IN_PROGRESS = text("iris.runtime.pack_download.in_progress");
    public static final TextKey VALIDATION_FAILED = text("iris.runtime.pack_download.validation_failed");
    public static final TextKey VALIDATION_REASON = text("iris.runtime.pack_download.validation_reason");
    public static final PluralKey VALIDATED_WITH_WARNINGS = plural("iris.runtime.pack_download.validated_with_warnings");
    public static final TextKey VALIDATED = text("iris.runtime.pack_download.validated");

    private PackDownloadMessages() {
    }
}

package art.arcane.iris.pack.datapack;

import java.util.Objects;

public record DatapackInstallResult(Status status) {
    public DatapackInstallResult {
        Objects.requireNonNull(status, "status");
    }

    public static DatapackInstallResult failedResult() {
        return new DatapackInstallResult(Status.FAILED);
    }

    public static DatapackInstallResult unchangedResult() {
        return new DatapackInstallResult(Status.UNCHANGED);
    }

    public static DatapackInstallResult readyResult() {
        return new DatapackInstallResult(Status.READY);
    }

    public static DatapackInstallResult restartRequiredResult() {
        return new DatapackInstallResult(Status.RESTART_REQUIRED);
    }

    public boolean succeeded() {
        return status != Status.FAILED;
    }

    public boolean changed() {
        return status == Status.READY || status == Status.RESTART_REQUIRED;
    }

    public boolean restartRequired() {
        return status == Status.RESTART_REQUIRED;
    }

    public enum Status {
        FAILED,
        UNCHANGED,
        READY,
        RESTART_REQUIRED
    }
}

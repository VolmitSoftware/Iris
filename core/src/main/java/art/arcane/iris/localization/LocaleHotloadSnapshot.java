package art.arcane.iris.localization;

import java.io.File;
import java.util.Objects;

record LocaleHotloadSnapshot(File file, String locale, String content, String sha256) {
    LocaleHotloadSnapshot {
        file = Objects.requireNonNull(file, "Locale override file cannot be null").getAbsoluteFile();
        locale = Objects.requireNonNull(locale, "Locale cannot be null");
        sha256 = Objects.requireNonNull(sha256, "Locale content hash cannot be null");
    }

    static LocaleHotloadSnapshot missing(File file, String locale) {
        return new LocaleHotloadSnapshot(file, locale, null, "missing");
    }

    static LocaleHotloadSnapshot present(File file, String locale, String content, String sha256) {
        return new LocaleHotloadSnapshot(
                file,
                locale,
                Objects.requireNonNull(content, "Locale content cannot be null"),
                sha256
        );
    }

    boolean missing() {
        return content == null;
    }
}

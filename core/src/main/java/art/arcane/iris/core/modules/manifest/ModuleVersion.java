/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.core.modules.manifest;

import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A module version as {@code major.minor.patch}. The major number says whether old and new chunks match: it rises when
 * the same settings place different blocks or settings stop loading. Minor is for new features and patch for fixes, both
 * leaving the output of existing packs alone.
 */
public record ModuleVersion(int major, int minor, int patch) implements Comparable<ModuleVersion> {
    private static final Pattern FORMAT = Pattern.compile("(\\d{1,9})(?:\\.(\\d{1,9}))?(?:\\.(\\d{1,9}))?");
    private static final Comparator<ModuleVersion> ORDER = Comparator.comparingInt(ModuleVersion::major)
            .thenComparingInt(ModuleVersion::minor)
            .thenComparingInt(ModuleVersion::patch);

    /** Empty for text that is not one to three numbers separated by dots; a left out minor or patch number is 0. */
    public static Optional<ModuleVersion> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher numbers = FORMAT.matcher(text.strip());
        if (!numbers.matches()) {
            return Optional.empty();
        }
        return Optional.of(new ModuleVersion(Integer.parseInt(numbers.group(1)), numberOr0(numbers.group(2)), numberOr0(numbers.group(3))));
    }

    public boolean placesTheSameBlocksAs(ModuleVersion other) {
        return major == other.major;
    }

    public boolean isNewerThan(ModuleVersion other) {
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(ModuleVersion other) {
        return ORDER.compare(this, other);
    }

    private static int numberOr0(String digits) {
        return digits == null ? 0 : Integer.parseInt(digits);
    }
}

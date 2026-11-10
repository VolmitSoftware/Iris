/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core.structure.authoring;

import java.util.Objects;
import java.util.regex.Pattern;

public record StructureLoss(
        StructureCapability capability,
        Severity severity,
        String code,
        String detail,
        String affectedResource
) {
    private static final Pattern CODE_PATTERN = Pattern.compile("[a-z0-9._-]+");

    public StructureLoss {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(affectedResource, "affectedResource");
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException("Invalid structure loss code: " + code);
        }
        if (detail.isBlank()) {
            throw new IllegalArgumentException("Structure loss detail cannot be blank");
        }
    }

    public static StructureLoss warning(StructureCapability capability, String code, String detail) {
        return new StructureLoss(capability, Severity.WARNING, code, detail, "");
    }

    public static StructureLoss error(StructureCapability capability, String code, String detail) {
        return new StructureLoss(capability, Severity.ERROR, code, detail, "");
    }

    public StructureLoss affecting(String resource) {
        Objects.requireNonNull(resource, "resource");
        return new StructureLoss(capability, severity, code, detail, resource);
    }

    public enum Severity {
        INFO,
        WARNING,
        ERROR
    }
}

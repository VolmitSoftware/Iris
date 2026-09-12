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

package art.arcane.iris.pack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BrokenPackException extends RuntimeException {
    private final String packName;
    private final List<String> reasons;

    public BrokenPackException(String packName, List<String> reasons) {
        super(buildMessage(packName, reasons));
        this.packName = packName;
        this.reasons = reasons == null ? new ArrayList<>() : new ArrayList<>(reasons);
    }

    public String getPackName() {
        return packName;
    }

    public List<String> getReasons() {
        return Collections.unmodifiableList(reasons);
    }

    private static String buildMessage(String packName, List<String> reasons) {
        StringBuilder sb = new StringBuilder();
        sb.append("Iris pack '").append(packName).append("' is broken and cannot be used for world or studio creation.");
        if (reasons != null) {
            for (String reason : reasons) {
                if (reason == null || reason.isBlank()) {
                    continue;
                }
                sb.append(System.lineSeparator()).append(" - ").append(reason);
            }
        }
        return sb.toString();
    }
}

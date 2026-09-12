/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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
package art.arcane.iris.pack.validation;

import java.util.List;

/**
 * Gate verdict for one compat unit. {@code excluded} units must be filtered out of every pool that could pick them;
 * {@code reasons} lists every finding recorded while evaluating the unit (excluded or not).
 */
public record CompatStatus(boolean excluded, List<CompatFinding> reasons) {
    public static final CompatStatus OK = new CompatStatus(false, List.of());

    public CompatStatus {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public static CompatStatus excludedBy(List<CompatFinding> reasons) {
        return new CompatStatus(true, reasons);
    }
}

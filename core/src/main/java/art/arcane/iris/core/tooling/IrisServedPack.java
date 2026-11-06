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

package art.arcane.iris.core.tooling;

import art.arcane.iris.core.loader.IrisData;
import com.volmit.ModuleLog;
import com.volmit.host.CommandRegistration;
import com.volmit.tooling.ToolPack;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** A pack Iris serves from its {@link IrisData}; two instances are equal when they read one pack folder. */
public final class IrisServedPack implements ServedPack {
    private final IrisData data;

    public IrisServedPack(IrisData data) {
        this.data = Objects.requireNonNull(data, "data");
    }

    @Override
    public String name() {
        return data.getDataFolder().getName();
    }

    @Override
    public List<CommandRegistration> commands() {
        try {
            return data.getModules().pack().catalog().commands();
        } catch (RuntimeException modulesFailed) {
            throw new IllegalStateException("The modules of pack " + name() + " did not load: " + modulesFailed.getMessage(),
                    modulesFailed);
        }
    }

    @Override
    public ToolPack toolPack(ModuleLog log) {
        return new IrisToolPack(data, log);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof IrisServedPack served && folder().equals(served.folder());
    }

    @Override
    public int hashCode() {
        return folder().hashCode();
    }

    @Override
    public String toString() {
        return "pack " + name();
    }

    private Path folder() {
        return data.getDataFolder().toPath().toAbsolutePath().normalize();
    }
}

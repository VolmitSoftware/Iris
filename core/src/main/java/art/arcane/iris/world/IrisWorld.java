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

package art.arcane.iris.world;

import art.arcane.iris.spi.PlatformWorld;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.File;

@Builder
@Data
@Accessors(chain = true, fluent = true)
public class IrisWorld {
    private String platformIdentity;
    private String name;
    private File worldFolder;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private long seed;
    private PlatformWorld platformWorld;
    private int minHeight;
    private int maxHeight;

    public long getRawWorldSeed() {
        return seed;
    }

    public String identity() {
        return platformIdentity;
    }

    public void setRawWorldSeed(long seed) {
        this.seed = seed;
    }

    public boolean hasPlatformWorld() {
        return platformWorld != null;
    }

    public int getHeight() {
        return maxHeight - minHeight;
    }
}

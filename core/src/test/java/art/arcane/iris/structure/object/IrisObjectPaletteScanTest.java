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

package art.arcane.iris.structure.object;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IrisObjectPaletteScanTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void readPaletteKeysReturnsV2PaletteWithoutResolvingStates() throws IOException {
        File file = writeObject("v2.iob", true, "minecraft:stone", "minecraft:oak_log[axis=y]");
        assertEquals(List.of("minecraft:stone", "minecraft:oak_log[axis=y]"), IrisObjectIO.readPaletteKeys(file));
    }

    @Test
    public void readPaletteKeysReturnsEmptyForLegacyHeader() throws IOException {
        File file = writeObject("legacy.iob", false, "minecraft:stone");
        assertTrue(IrisObjectIO.readPaletteKeys(file).isEmpty());
    }

    @Test
    public void readPaletteKeysReturnsEmptyForTruncatedFile() throws IOException {
        File file = folder.newFile("truncated.iob");
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
            out.writeInt(1);
            out.writeInt(1);
        }
        assertTrue(IrisObjectIO.readPaletteKeys(file).isEmpty());
    }

    @Test
    public void readPaletteKeysReturnsEmptyForMissingFile() {
        assertTrue(IrisObjectIO.readPaletteKeys(new File(folder.getRoot(), "absent.iob")).isEmpty());
    }

    private File writeObject(String name, boolean v2Header, String... palette) throws IOException {
        File file = folder.newFile(name);
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
            out.writeInt(3);
            out.writeInt(3);
            out.writeInt(3);
            out.writeUTF(v2Header ? "Iris V2 IOB;" : "Iris V1 IOB;");
            out.writeShort(palette.length);
            for (String key : palette) {
                out.writeUTF(key);
            }
            out.writeInt(0);
            out.writeInt(0);
        }
        return file;
    }
}

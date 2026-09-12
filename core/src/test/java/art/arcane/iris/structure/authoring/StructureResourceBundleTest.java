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

package art.arcane.iris.structure.authoring;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

public class StructureResourceBundleTest {
    private static final StructureKey KEY = StructureKey.parse("iris_test:temple");

    @Test
    public void resourceContentIsImmutableAcrossThePublicBoundary() {
        byte[] input = "original".getBytes(StandardCharsets.UTF_8);
        StructureResourceBundle bundle = builder()
                .resource("objects/temple.iob", input)
                .build();
        input[0] = 'X';

        byte[] exposed = bundle.resources().get("objects/temple.iob").content();
        exposed[1] = 'X';

        assertArrayEquals(
                "original".getBytes(StandardCharsets.UTF_8),
                bundle.resources().get("objects/temple.iob").content()
        );
    }

    @Test
    public void rejectsTraversalInternalAndNonPortablePaths() {
        assertThrows(IllegalArgumentException.class, () -> builder().resource("../outside.json", new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> builder().resource(".iris/manifest.json", new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> builder().resource("objects/CON.iob", new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> builder().resource("objects/bad:name.iob", new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> builder().resource("objects\\bad.iob", new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> builder().resource("objects/bad\u007f.iob", new byte[0]));
    }

    @Test
    public void rejectsResourcesThatCollideOnCaseInsensitiveFileSystems() {
        StructureResourceBundle.Builder builder = builder().resource("objects/Temple.iob", new byte[0]);

        assertThrows(IllegalArgumentException.class, () -> builder.resource("objects/temple.iob", new byte[0]));
    }

    private StructureResourceBundle.Builder builder() {
        return StructureResourceBundle.builder(KEY)
                .source(StructureSource.of(StructureSource.Kind.IRIS, KEY))
                .backend(StructureBackend.IRIS_ASSEMBLY)
                .capability(StructureCapability.BLOCKS);
    }
}

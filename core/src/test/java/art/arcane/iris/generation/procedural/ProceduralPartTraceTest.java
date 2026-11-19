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

package art.arcane.iris.generation.procedural;

import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.iris.generation.procedural.part.PartTrace;
import art.arcane.iris.generation.procedural.part.ResolvedProceduralObject;
import art.arcane.iris.structure.object.IrisObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The parts the studio picks out of a baked variant are the ones that placed the blocks it shows. */
public class ProceduralPartTraceTest {
    private static final String[] OBJECTS = {"trees/estranged/baobab-giant", "trees/estranged/stiltroot", "formations/frozen/glacial-arch"};

    private ProceduralFixture fixture;

    @Before
    public void bindGameLikePlatform() throws URISyntaxException {
        fixture = ProceduralFixture.bind();
    }

    @After
    public void unbindPlatform() {
        ProceduralFixture.unbind();
    }

    @Test
    public void everyTracedBlockIsABlockOfTheVariantAndItsPartIsInTheOutline() throws IOException {
        for (String path : OBJECTS) {
            ResolvedProceduralObject object = fixture.resolved(path);
            for (IrisObject variant : object.variants()) {
                PartTrace trace = object.trace(variant);
                Map<IrisBlockVector, Integer> parts = object.parts(variant);

                assertFalse(path + " " + variant.getLoadKey() + " has no part blocks", parts.isEmpty());
                assertEquals(path + " " + variant.getLoadKey() + " traced blocks that are not shown", trace.blockCount(), parts.size());
                assertTrue(parts.values().stream().allMatch(part -> part < object.outline().nodeCount()));
            }
        }
    }

    @Test
    public void aVariantBakedFromAnotherObjectHasNoParts() throws IOException {
        ResolvedProceduralObject object = fixture.resolved(OBJECTS[0]);
        IrisObject foreign = fixture.resolved(OBJECTS[1]).variants().get(0);

        assertTrue(object.parts(foreign).isEmpty());
    }

    @Test
    public void tracingTwiceFindsTheSameParts() throws IOException {
        ResolvedProceduralObject object = fixture.resolved(OBJECTS[0]);
        IrisObject variant = object.variants().get(0);

        assertEquals(object.parts(variant), object.parts(variant));
    }
}

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

package art.arcane.iris.engine.procedural;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.core.modules.IrisPackResources;
import art.arcane.iris.core.modules.JsonFormsCodecs;
import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.procedural.part.PartTrace;
import art.arcane.iris.engine.procedural.part.ProceduralObject;
import art.arcane.iris.engine.procedural.part.ResolvedProceduralObject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.volmit.object.PlaceableObject;
import com.volmit.tooling.BlockParts;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

/** The parts the studio picks out of a baked variant are the ones that placed the blocks it shows. */
public class ProceduralPartTraceTest {
    private static final String FIXTURE = "/procedural-parts";
    private static final String DEFINITIONS = "procedural";
    private static final String[] OBJECTS = {"trees/estranged/baobab-giant", "trees/estranged/stiltroot", "formations/frozen/glacial-arch"};

    private Path pack;
    private IrisPackResources resources;
    private Gson gson;

    @Before
    public void bindGameLikePlatform() throws URISyntaxException {
        UsableBlockPlatform.bind(new GameLikeBlocks().registries());
        pack = Path.of(ProceduralPartTraceTest.class.getResource(FIXTURE).toURI());
        IrisData data = mock(IrisData.class, withSettings().stubOnly().defaultAnswer(CALLS_REAL_METHODS));
        doReturn(pack.toFile()).when(data).getDataFolder();
        doReturn(mock(ResourceLoader.class)).when(data).getBlockLoader();
        doReturn(null).when(data).getEngine();
        resources = new IrisPackResources(data);
        doReturn(resources).when(data).getPackResources();
        gson = new GsonBuilder().registerTypeAdapterFactory(data).registerTypeAdapterFactory(new JsonFormsCodecs()).create();
    }

    @After
    public void unbindPlatform() {
        UsableBlockPlatform.unbind();
    }

    @Test
    public void everyTracedBlockIsABlockOfTheVariantAndItsPartIsInTheOutline() throws IOException {
        for (String path : OBJECTS) {
            ResolvedProceduralObject object = resolved(path);
            for (PlaceableObject variant : object.variants()) {
                PartTrace trace = object.trace(variant);
                BlockParts parts = object.parts(variant);

                assertFalse(path + " " + variant.name() + " has no part blocks", parts.partsByBlock().isEmpty());
                assertEquals(path + " " + variant.name() + " traced blocks that are not shown", trace.blockCount(), parts.partsByBlock().size());
                assertTrue(parts.partsByBlock().values().stream().allMatch(part -> part < object.outline().nodeCount()));
            }
        }
    }

    @Test
    public void aVariantBakedFromAnotherObjectHasNoParts() throws IOException {
        ResolvedProceduralObject object = resolved(OBJECTS[0]);
        PlaceableObject foreign = resolved(OBJECTS[1]).variants().get(0);

        assertTrue(object.parts(foreign).partsByBlock().isEmpty());
    }

    @Test
    public void tracingTwiceFindsTheSameParts() throws IOException {
        ResolvedProceduralObject object = resolved(OBJECTS[0]);
        PlaceableObject variant = object.variants().get(0);

        assertEquals(object.parts(variant), object.parts(variant));
    }

    private ResolvedProceduralObject resolved(String path) throws IOException {
        String json = Files.readString(pack.resolve(DEFINITIONS).resolve(path + ".json"));
        return new ResolvedProceduralObject(path, gson.fromJson(json, ProceduralObject.class), resources);
    }
}

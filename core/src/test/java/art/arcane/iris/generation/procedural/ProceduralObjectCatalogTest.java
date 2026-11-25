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

import art.arcane.iris.generation.procedural.part.ProceduralObject;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.volmlib.util.math.RNG;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class ProceduralObjectCatalogTest {
    private static final String RED_CAP = "fungi/mushroom/red-cap";
    private static final String RED_CAP_KEY = ProceduralObjectCatalog.KEY_PREFIX + RED_CAP;
    private static final int RED_CAP_VARIANTS = 6;

    private ProceduralFixture fixture;
    private ResourceLoader<ProceduralObject> definitions;
    private ProceduralObjectCatalog catalog;

    @Before
    @SuppressWarnings("unchecked")
    public void bindPackWithRedCap() throws Exception {
        fixture = ProceduralFixture.bind();
        definitions = mock(ResourceLoader.class);
        doReturn(new String[]{RED_CAP}).when(definitions).getPossibleKeys();
        doReturn(null).when(definitions).load(anyString(), eq(false));
        doReturn(fixture.definition(RED_CAP)).when(definitions).load(RED_CAP, false);
        doReturn(definitions).when(fixture.data()).getProceduralObjectLoader();
        catalog = new ProceduralObjectCatalog(fixture.data());
    }

    @After
    public void unbindPlatform() {
        ProceduralFixture.unbind();
    }

    @Test
    public void listsEveryDefinitionAsAPoolKey() {
        assertEquals(List.of(RED_CAP_KEY), catalog.definitionKeys());
    }

    @Test
    public void aPinnedKeyGivesThatVariant() {
        Optional<IrisObject> third = catalog.object(RED_CAP_KEY + "#2");
        assertTrue(third.isPresent());
        assertEquals(RED_CAP_KEY + "#2", third.get().getLoadKey());
    }

    @Test
    public void aPoolKeyPicksTheSameVariantForTheSameSeed() {
        IrisObject first = catalog.object(RED_CAP_KEY, new RNG(42)).orElseThrow();
        IrisObject again = catalog.object(RED_CAP_KEY, new RNG(42)).orElseThrow();
        assertEquals(first.getLoadKey(), again.getLoadKey());
        assertEquals(RED_CAP_VARIANTS, catalog.placeable(RED_CAP_KEY).size());
    }

    @Test
    public void aKeyTheOtherPackObjectsUseIsNotProcedural() {
        assertFalse(ProceduralObjectCatalog.isProceduralKey("trees/oak"));
        assertTrue(ProceduralObjectCatalog.isProceduralKey(RED_CAP_KEY));
    }

    @Test
    public void anUnknownDefinitionGivesNothing() {
        assertTrue(catalog.object(ProceduralObjectCatalog.KEY_PREFIX + "fungi/missing").isEmpty());
    }

    @Test
    public void definitionsBakeOnceUntilInvalidated() {
        catalog.object(RED_CAP_KEY);
        catalog.object(RED_CAP_KEY + "#1");
        verify(definitions, times(1)).load(RED_CAP, false);

        catalog.invalidate();
        catalog.object(RED_CAP_KEY);
        verify(definitions, times(2)).load(RED_CAP, false);
    }
}

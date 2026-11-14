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
import art.arcane.iris.generation.procedural.part.ResolvedProceduralObject;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.spi.IrisPlatforms;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

/** The overworld pack's {@code procedural/} folder, read the way a pack is, over registries whose blocks behave like the game's. */
final class ProceduralFixture {
    private static final String RESOURCE = "/procedural-parts";
    private static final String DEFINITIONS = "procedural";

    private final Path pack;
    private final IrisData data;
    private final Gson gson;

    private ProceduralFixture(Path pack, IrisData data, Gson gson) {
        this.pack = pack;
        this.data = data;
        this.gson = gson;
    }

    static ProceduralFixture bind() throws URISyntaxException {
        new GameLikeBlocks().bind();
        Path pack = Path.of(ProceduralFixture.class.getResource(RESOURCE).toURI());
        IrisData data = mock(IrisData.class, withSettings().stubOnly().defaultAnswer(CALLS_REAL_METHODS));
        doReturn(pack.toFile()).when(data).getDataFolder();
        doReturn(mock(ResourceLoader.class)).when(data).getBlockLoader();
        doReturn(null).when(data).getEngine();
        Gson gson = new GsonBuilder().registerTypeAdapterFactory(data).create();
        return new ProceduralFixture(pack, data, gson);
    }

    static void unbind() {
        IrisPlatforms.unbind();
    }

    IrisData data() {
        return data;
    }

    ResolvedProceduralObject resolved(String path) throws IOException {
        return new ResolvedProceduralObject(path, definition(path), data);
    }

    ProceduralObject definition(String path) throws IOException {
        String json = Files.readString(pack.resolve(DEFINITIONS).resolve(path + ".json"));
        return gson.fromJson(json, ProceduralObject.class);
    }

    List<String> definitionPaths() throws IOException {
        Path folder = pack.resolve(DEFINITIONS);
        try (Stream<Path> files = Files.walk(folder)) {
            return files.filter(file -> file.toString().endsWith(".json"))
                    .map(file -> folder.relativize(file).toString().replace('\\', '/'))
                    .map(file -> file.substring(0, file.length() - ".json".length()))
                    .sorted()
                    .toList();
        }
    }
}

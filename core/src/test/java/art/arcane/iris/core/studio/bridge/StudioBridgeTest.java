package art.arcane.iris.core.studio.bridge;

import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.render.RenderType;
import art.arcane.iris.engine.object.world.IrisWorld;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.util.project.stream.ProceduralStream;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A bridge to a world of plains in one region, flat at height 70, asked over real HTTP. */
public class StudioBridgeTest {
    private static final Duration WAIT = Duration.ofSeconds(5);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(WAIT).build();
    private final AtomicReference<Engine> running = new AtomicReference<>();
    private StudioBridge bridge;

    @Before
    @SuppressWarnings("unchecked")
    public void openABridge() throws Exception {
        Engine engine = mock(Engine.class);
        IrisComplex complex = mock(IrisComplex.class);
        IrisBiome plains = mock(IrisBiome.class);
        when(plains.getLoadKey()).thenReturn("plains");
        when(plains.getName()).thenReturn("Plains");
        when(plains.getColor(eq(engine), any(RenderType.class))).thenReturn(java.awt.Color.GREEN);
        IrisRegion region = mock(IrisRegion.class);
        when(region.getLoadKey()).thenReturn("temperate");
        when(region.getName()).thenReturn("Temperate");
        when(region.getColor(complex, RenderType.REGION)).thenReturn(java.awt.Color.BLUE);
        ProceduralStream<IrisBiome> biomes = mock(ProceduralStream.class);
        when(biomes.get(anyDouble(), anyDouble())).thenReturn(plains);
        ProceduralStream<IrisRegion> regions = mock(ProceduralStream.class);
        when(regions.get(anyDouble(), anyDouble())).thenReturn(region);
        ProceduralStream<Double> heights = mock(ProceduralStream.class);
        when(heights.getDouble(anyDouble(), anyDouble())).thenReturn(70D);
        ProceduralStream<PlatformBiome> vanilla = mock(ProceduralStream.class);
        when(complex.getBaseBiomeStream()).thenReturn(biomes);
        when(complex.getCaveBiomeStream()).thenReturn(biomes);
        when(complex.getRegionStream()).thenReturn(regions);
        when(complex.getHeightStream()).thenReturn(heights);
        when(complex.getSlopeStream()).thenReturn(heights);
        when(complex.getTrueBiomeDerivativeStream()).thenReturn(vanilla);
        when(complex.getFocusRegion()).thenReturn(region);
        when(complex.getFocusBiome()).thenReturn(plains);
        when(plains.getChildren()).thenReturn(new art.arcane.volmlib.util.collection.KList<>());
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.getLoadKey()).thenReturn("overworld");
        when(dimension.getName()).thenReturn("Overworld");
        when(dimension.getFluidHeight()).thenReturn(63);
        IrisWorld world = mock(IrisWorld.class);
        when(world.getRawWorldSeed()).thenReturn(Long.MAX_VALUE);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getWorld()).thenReturn(world);
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getHeight()).thenReturn(384);
        running.set(engine);
        bridge = StudioBridge.start(new StudioWorld("world_iris", running::get, null));
    }

    @After
    public void closeTheBridge() {
        bridge.close();
    }

    @Test
    public void aClosedBridgeStillKnowsItsPortSoItsConnectionFileCanBeRemoved() {
        int openPort = bridge.port();

        bridge.close();

        assertEquals(openPort, bridge.port());
    }

    @Test
    public void aRequestWithoutTheTokenIsRefused() throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri("world")).timeout(WAIT).build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(401, response.statusCode());
    }

    @Test
    public void theWorldListsItsHeightsAndViews() throws Exception {
        JsonObject world = json(get("world"));

        assertEquals("world_iris", world.get("world").getAsString());
        assertEquals("overworld", world.get("dimension").getAsString());
        assertEquals(Long.toString(Long.MAX_VALUE), world.get("seed").getAsString());
        assertEquals(-64, world.get("minY").getAsInt());
        assertEquals(384, world.get("height").getAsInt());
        assertEquals(63, world.get("fluidHeight").getAsInt());
        assertEquals("biomes", world.getAsJsonArray("views").get(0).getAsJsonObject().get("id").getAsString());
    }

    @Test
    public void aTileIsItsHeaderThenAlignedGrids() throws Exception {
        HttpResponse<byte[]> response = http.send(authorized("tile?view=biomes&x=-1&z=2&scale=2&size=8&heights=1"),
                HttpResponse.BodyHandlers.ofByteArray());
        ByteBuffer body = ByteBuffer.wrap(response.body()).order(ByteOrder.LITTLE_ENDIAN);
        int headerLength = body.getInt();
        JsonObject header = JsonParser.parseString(new String(response.body(), 4, headerLength, StandardCharsets.UTF_8)).getAsJsonObject();
        int cells = 10 * 10;
        int subjects = (4 + headerLength + 3) / 4 * 4;
        int regions = subjects + cells * 2;
        int heights = regions + cells * 2;

        assertEquals(200, response.statusCode());
        assertEquals(8, header.get("size").getAsInt());
        assertTrue(header.get("heights").getAsBoolean());
        assertEquals(heights + cells * 4, response.body().length);
        String subjectKey = header.getAsJsonArray("labels").get(body.getShort(subjects)).getAsJsonObject().get("key").getAsString();
        String regionKey = header.getAsJsonArray("labels").get(body.getShort(regions)).getAsJsonObject().get("key").getAsString();
        assertEquals("plains", subjectKey);
        assertEquals("temperate", regionKey);
        assertEquals(70F, body.getFloat(heights), 1e-4F);
    }

    @Test
    public void aBadTileRequestSaysWhatIsWrong() throws Exception {
        HttpResponse<String> unknownView = http.send(authorized("tile?view=nothing&scale=1"), HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> hugeScale = http.send(authorized("tile?view=biomes&scale=1e9"), HttpResponse.BodyHandlers.ofString());

        assertEquals(400, unknownView.statusCode());
        assertTrue(unknownView.body().contains("Unknown view"));
        assertEquals(400, hugeScale.statusCode());
    }

    @Test
    public void theHoverDescribesTheColumnAndHowItWasPicked() throws Exception {
        JsonObject hover = json(get("hover?view=biomes&x=5&z=-3"));

        assertEquals("plains", hover.getAsJsonObject("biome").get("key").getAsString());
        assertEquals("temperate", hover.getAsJsonObject("region").get("key").getAsString());
        assertEquals(70D, hover.get("height").getAsDouble(), 0D);
        JsonObject firstStep = hover.getAsJsonArray("pickPath").get(0).getAsJsonObject();
        assertEquals("FOCUS", firstStep.get("kind").getAsString());
        assertEquals("temperate", firstStep.getAsJsonObject("picked").get("key").getAsString());
        assertTrue("nothing picked the focused region, which the studio reads as a null noise",
                firstStep.has("noise") && firstStep.get("noise").isJsonNull());
    }

    @Test
    public void aHoverNamesItsView() throws Exception {
        HttpResponse<String> response = http.send(authorized("hover?x=5&z=-3"), HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
    }

    @Test
    public void onlyTheRegionViewPreviewsARegionAndOnlyOneTheWorldPrepared() throws Exception {
        assertEquals(400, get("hover?view=biomes&preview=temperate&x=0&z=0").statusCode());
        assertEquals(400, get("hover?view=regions&preview=nowhere&x=0&z=0").statusCode());
    }

    @Test
    public void theHeightViewHasNoPicksToInspect() throws Exception {
        assertEquals(400, get("picks?view=height&minX=0&minZ=0&maxX=10&maxZ=10").statusCode());
    }

    @Test
    public void aWorldWithoutAnEngineIsUnavailableUntilItHasOneAgain() throws Exception {
        Engine engine = running.getAndSet(null);
        assertEquals(503, get("world").statusCode());

        running.set(engine);
        assertEquals(200, get("world").statusCode());
    }

    @Test
    public void aNewListenerHearsThePlayersRightAway() throws Exception {
        HttpResponse<InputStream> events = http.send(authorized("events"), HttpResponse.BodyHandlers.ofInputStream());

        try (BufferedReader lines = new BufferedReader(new InputStreamReader(events.body(), StandardCharsets.UTF_8))) {
            String line;
            do {
                line = lines.readLine();
            } while (line != null && !line.equals("event: players"));
            assertEquals("event: players", line);
            assertEquals("data: []", lines.readLine());
        }
    }

    private HttpResponse<String> get(String endpoint) throws Exception {
        return http.send(authorized(endpoint), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest authorized(String endpoint) {
        return HttpRequest.newBuilder(uri(endpoint)).timeout(WAIT).header("Authorization", "Bearer " + bridge.token()).build();
    }

    private URI uri(String endpoint) {
        return URI.create("http://127.0.0.1:" + bridge.port() + "/v1/" + endpoint);
    }

    private static JsonObject json(HttpResponse<String> response) {
        assertEquals(response.body(), 200, response.statusCode());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
}

package art.arcane.iris.generation.mantle;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.GenerationClosedException;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.LogLevel;
import art.arcane.iris.structure.nativegen.NativeStructureSuppression;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectRotation;
import art.arcane.iris.structure.object.ObjectPlaceMode;
import art.arcane.iris.structure.placement.IrisStructure;
import art.arcane.iris.structure.placement.IrisStructureLocator;
import art.arcane.iris.structure.placement.IrisStructurePlacement;
import art.arcane.iris.structure.placement.PlacedStructurePiece;
import art.arcane.iris.structure.placement.StructurePlacementScope;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisStructureReplacementFailureTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private final List<String> warnings = new ArrayList<>();
    private IrisSettings previousSettings;
    private IrisStructureComponent component;
    private Engine engine;
    private MantleWriter writer;
    private ChunkContext context;

    @Before
    public void setUp() {
        previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        IrisPlatforms.unbind();
        IrisPlatform platform = mock(IrisPlatform.class);
        doAnswer(invocation -> {
            if (invocation.getArgument(0) == LogLevel.WARN) {
                warnings.add(invocation.getArgument(1));
            }
            return null;
        }).when(platform).log(any(LogLevel.class), any(String.class));
        IrisPlatforms.bind(platform);
        EngineMantle engineMantle = mock(EngineMantle.class);
        engine = mock(Engine.class);
        IrisComplex complex = mock(IrisComplex.class);
        when(complex.allowsNewGenerationChunk(anyInt(), anyInt())).thenReturn(true);
        when(engineMantle.getEngine()).thenReturn(engine);
        when(engineMantle.getComplex()).thenReturn(complex);
        when(engineMantle.getData()).thenReturn(mock(IrisData.class));
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getHeight()).thenReturn(384);
        component = new IrisStructureComponent(engineMantle);
        writer = mock(MantleWriter.class);
        context = mock(ChunkContext.class);
        when(context.getComplex()).thenReturn(complex);
    }

    @After
    public void tearDown() {
        IrisSettings.settings = previousSettings;
        IrisPlatforms.unbind();
    }

    @Test
    public void rejectedPiecesWarnOnceAcrossPiecesAndChunks() {
        IrisObject rejected = object("objects/rejected");
        when(rejected.place(anyInt(), anyInt(), anyInt(), any(), any(), any(), any(), any(), any())).thenReturn(-1);
        IrisStructure structure = structure("test:rejected-city");

        assertEquals(3, component.placePieces(writer, replacement(), structure, pieces(rejected, 3), new RNG(1L)));
        assertEquals(3, component.placePieces(writer, replacement(), structure, pieces(rejected, 3), new RNG(2L)));

        assertEquals(warnings.toString(), 1, warnings.size());
        assertTrue(warnings.getFirst(), warnings.getFirst().contains("test:rejected-city"));
    }

    @Test
    public void rejectedPieceOfANonReplacementStaysSilent() {
        IrisObject rejected = object("objects/rejected");
        when(rejected.place(anyInt(), anyInt(), anyInt(), any(), any(), any(), any(), any(), any())).thenReturn(-1);

        assertEquals(1, component.placePieces(writer, new IrisStructurePlacement(), structure("test:quiet-city"),
                pieces(rejected, 1), new RNG(1L)));

        assertEquals(List.of(), warnings);
    }

    @Test
    public void throwingPiecesWarnOnceAndTheOtherPiecesStillPlace() {
        IrisObject broken = object("objects/broken");
        IrisObject sound = object("objects/sound");
        when(broken.place(anyInt(), anyInt(), anyInt(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("bad block"));
        IrisObject alsoBroken = object("objects/also-broken");
        when(alsoBroken.place(anyInt(), anyInt(), anyInt(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("bad block"));
        KList<PlacedStructurePiece> pieces = pieces(broken, 1);
        pieces.addAll(pieces(alsoBroken, 1));
        pieces.addAll(pieces(sound, 1));

        assertEquals(2, component.placePieces(writer, replacement(), structure("test:broken-city"), pieces, new RNG(1L)));

        assertEquals(warnings.toString(), 1, warnings.size());
        verify(sound, times(1)).place(anyInt(), anyInt(), anyInt(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void engineFailureFromAPieceEscapes() {
        IrisObject broken = object("objects/closed");
        GenerationClosedException closed = new GenerationClosedException("router closed");
        when(broken.place(anyInt(), anyInt(), anyInt(), any(), any(), any(), any(), any(), any())).thenThrow(closed);

        assertSame(closed, assertThrows(GenerationClosedException.class, () -> component.placePieces(writer, replacement(),
                structure("test:closed-city"), pieces(broken, 1), new RNG(1L))));
    }

    @Test
    public void aThrowingReplacementLeavesTheChunkGeneratingWithOneWarning() {
        IrisStructurePlacement placement = replacement();
        placement.getStructures().add("test:failing-city");
        try (MockedStatic<StructurePlacementScope> scope = mockStatic(StructurePlacementScope.class);
             MockedStatic<IrisStructureLocator> locator = mockStatic(IrisStructureLocator.class)) {
            scope.when(() -> StructurePlacementScope.placementsAt(any(), anyInt(), anyInt()))
                    .thenReturn(new KList<>(placement));
            locator.when(() -> IrisStructureLocator.resolvePlacement(any(), any(), anyInt(), anyInt()))
                    .thenThrow(new IllegalStateException("no viable graph"));

            component.generateLayer(writer, 4, -3, context);
            component.generateLayer(writer, 5, -3, context);
        }

        assertEquals(warnings.toString(), 1, warnings.size());
    }

    @Test
    public void anEngineFailureFromAReplacementEscapes() {
        IrisStructurePlacement placement = replacement();
        placement.getStructures().add("test:closed-city");
        GenerationClosedException closed = new GenerationClosedException("router closed");
        try (MockedStatic<StructurePlacementScope> scope = mockStatic(StructurePlacementScope.class);
             MockedStatic<IrisStructureLocator> locator = mockStatic(IrisStructureLocator.class)) {
            scope.when(() -> StructurePlacementScope.placementsAt(any(), anyInt(), anyInt()))
                    .thenReturn(new KList<>(placement));
            locator.when(() -> IrisStructureLocator.resolvePlacement(any(), any(), anyInt(), anyInt())).thenThrow(closed);

            assertSame(closed, assertThrows(GenerationClosedException.class,
                    () -> component.generateLayer(writer, 4, -3, context)));
        }
    }

    private static IrisStructurePlacement replacement() {
        return new IrisStructurePlacement().setNativeSuppression(NativeStructureSuppression.REPLACE_SOURCE);
    }

    private static IrisStructure structure(String key) {
        IrisStructure structure = new IrisStructure().setPlaceMode(ObjectPlaceMode.STRUCTURE_PIECE);
        structure.setLoadKey(key);
        return structure;
    }

    private static IrisObject object(String key) {
        IrisObject object = mock(IrisObject.class);
        when(object.getLoadKey()).thenReturn(key);
        return object;
    }

    private static KList<PlacedStructurePiece> pieces(IrisObject object, int count) {
        KList<PlacedStructurePiece> pieces = new KList<>();
        for (int index = 0; index < count; index++) {
            pieces.add(new PlacedStructurePiece(null, object, index * 16, 64, 0, IrisObjectRotation.of(0, 0, 0),
                    index * 16, 64, 0, index * 16 + 8, 70, 8));
        }
        return pieces;
    }
}

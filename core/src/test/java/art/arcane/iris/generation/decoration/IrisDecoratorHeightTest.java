package art.arcane.iris.generation.decoration;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisDecoratorHeightTest {
    @Test
    public void sampledHeightDoesNotExceedConfiguredMaximum() {
        CNG generator = mock(CNG.class);
        when(generator.fit(eq(1), eq(2), anyDouble(), anyDouble())).thenReturn(2);
        IrisDecorator decorator = new IrisDecorator() {
            @Override
            public CNG getHeightGenerator(RNG rng, IrisData data) {
                return generator;
            }
        };
        decorator.setStackMin(1);
        decorator.setStackMax(2);

        assertEquals(2, decorator.getHeight(new RNG(1L), 0, 0, mock(IrisData.class)));
    }
}

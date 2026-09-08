package art.arcane.iris.testsupport;

import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class PlatformBinding implements TestRule {
    private final Supplier<IrisPlatform> factory;
    private IrisPlatform platform;
    private PlatformRegistries registries;
    private PlatformBlockState block;

    private PlatformBinding(Supplier<IrisPlatform> factory) {
        this.factory = factory;
    }

    public static PlatformBinding mockPlatform() {
        return new PlatformBinding(null);
    }

    public static PlatformBinding of(Supplier<IrisPlatform> factory) {
        if (factory == null) {
            throw new IllegalArgumentException("A platform factory is required");
        }

        return new PlatformBinding(factory);
    }

    public IrisPlatform platform() {
        if (platform == null) {
            throw new IllegalStateException("The platform binding rule is not active");
        }

        return platform;
    }

    public PlatformRegistries registries() {
        if (registries == null) {
            registries = platform().registries();
        }

        return registries;
    }

    public PlatformBlockState block() {
        if (block == null) {
            block = registries().block("minecraft:stone");
        }

        return block;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        String scope = description.getDisplayName();

        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                PlatformLeakGuard.requireClean(scope, "start");
                bind();

                try {
                    base.evaluate();
                } finally {
                    IrisPlatforms.unbind();
                    platform = null;
                    registries = null;
                    block = null;
                    PlatformLeakGuard.requireClean(scope, "end");
                }
            }
        };
    }

    private void bind() {
        if (factory == null) {
            block = mock(PlatformBlockState.class);
            registries = mock(PlatformRegistries.class);
            when(registries.block(anyString())).thenReturn(block);
            platform = mock(IrisPlatform.class);
            when(platform.registries()).thenReturn(registries);
        } else {
            platform = factory.get();
        }

        IrisPlatforms.bind(platform);
    }
}

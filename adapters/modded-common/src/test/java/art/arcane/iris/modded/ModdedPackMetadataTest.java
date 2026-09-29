package art.arcane.iris.modded;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ModdedPackMetadataTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void metadataLoadsClientResourcesAndServerDataForCurrentMinecraft() throws IOException {
        try (InputStream resource = ModdedPackMetadataTest.class.getResourceAsStream("/pack.mcmeta")) {
            assertNotNull(resource);
            try (InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
                JsonObject metadata = JsonParser.parseReader(reader).getAsJsonObject();
                for (PackType type : PackType.values()) {
                    PackMetadataSection section = PackMetadataSection.forPackType(type).codec()
                            .parse(JsonOps.INSTANCE, metadata.get("pack")).getOrThrow();
                    assertEquals("Iris client resources and mod data", section.description().getString());
                    assertTrue(type.toString(), section.supportedFormats().isValueInRange(
                            SharedConstants.getCurrentVersion().packVersion(type)));
                }
            }
        }
    }
}

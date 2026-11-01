package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.iris.world.entity.IrisEffect;
import net.minecraft.SharedConstants;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class NativePlayerEffectsTest {
    private static final String SOUND = "minecraft:block.amethyst_block.chime";

    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void everyVanillaSoundSourceIsReachableByItsName() {
        for (SoundSource source : SoundSource.values()) {
            assertSame(source.getName(), source, sentSource(NativePlayerEffects.soundCategory(source.getName())));
        }
    }

    @Test
    public void effectCategoryNamesResolveIgnoringCase() {
        assertSame(SoundSource.AMBIENT, sentSource(category(" Ambient ")));
        assertSame(SoundSource.RECORDS, sentSource(category("RECORD")));
        assertSame(SoundSource.MASTER, sentSource(category(null)));
    }

    @Test
    public void unknownCategoryNamesDoNotResolve() {
        assertNull(category("ambiant"));
        assertNull(category("records"));
        assertNull(category("players"));
    }

    private static NativePlayerEffects.SoundCategory category(String authored) {
        return NativePlayerEffects.soundCategory(new IrisEffect().setSoundCategory(authored).getSoundCategoryKey());
    }

    private static SoundSource sentSource(NativePlayerEffects.SoundCategory category) {
        assertNotNull(category);
        NativePlayerEffects.Sound sound = NativePlayerEffects.sound(SOUND);
        assertNotNull(sound);
        ServerPlayer handle = mock(ServerPlayer.class);
        ServerGamePacketListenerImpl connection = mock(ServerGamePacketListenerImpl.class);
        handle.connection = connection;

        NativePlayerEffects.sound(NativeProtocolPlayer.fromHandle(handle), sound,
                new NativePlayerEffects.SoundEmission(category, 1.0D, 64.0D, -3.0D, 1.0F, 1.0F, 7L));

        ArgumentCaptor<ClientboundSoundPacket> packet = ArgumentCaptor.forClass(ClientboundSoundPacket.class);
        verify(connection).send(packet.capture());
        return packet.getValue().getSource();
    }
}

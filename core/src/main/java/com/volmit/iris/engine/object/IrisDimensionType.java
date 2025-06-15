package com.volmit.iris.engine.object;

import com.volmit.iris.core.nms.datapack.IDataFixer;
import com.volmit.iris.util.data.Varint;
import com.volmit.iris.util.io.IO;
import lombok.*;
import lombok.experimental.Accessors;

import java.io.*;

@Getter
@ToString
@Accessors(fluent = true, chain = true)
@EqualsAndHashCode
public final class IrisDimensionType {
    @NonNull
    private final String key;
    @NonNull
    private final IDataFixer.Dimension base;
    @NonNull
    private final IrisDimensionTypeOptions options;
    private final int logicalHeight;
    private final int height;
    private final int minY;

    public IrisDimensionType(
            @NonNull IDataFixer.Dimension base,
            @NonNull IrisDimensionTypeOptions options,
            int logicalHeight,
            int height,
            int minY
    ) {
        if (logicalHeight > height) throw new IllegalArgumentException("Logical height cannot be greater than height");
        if (logicalHeight < 0) throw new IllegalArgumentException("Logical height cannot be less than zero");
        if (height < 16 || height > 4064 ) throw new IllegalArgumentException("Height must be between 16 and 4064");
        if ((height & 15) != 0) throw new IllegalArgumentException("Height must be a multiple of 16");
        if (minY < -2032 || minY > 2031) throw new IllegalArgumentException("Min Y must be between -2032 and 2031");
        if ((minY & 15) != 0) throw new IllegalArgumentException("Min Y must be a multiple of 16");

        this.base = base;
        this.options = options;
        this.logicalHeight = logicalHeight;
        this.height = height;
        this.minY = minY;
        this.key = createKey();
    }

    public static IrisDimensionType fromKey(String key) {
        var stream = new ByteArrayInputStream(IO.decode(key.replace(".", "=").toUpperCase()));
        try (var din = new DataInputStream(stream)) {
            return new IrisDimensionType(
                    IDataFixer.Dimension.values()[din.readUnsignedByte()],
                    new IrisDimensionTypeOptions().read(din),
                    Varint.readUnsignedVarInt(din),
                    Varint.readUnsignedVarInt(din),
                    Varint.readSignedVarInt(din)
            );
        } catch (IOException e) {
            throw new RuntimeException("This is impossible", e);
        }
    }

    public String toJson(IDataFixer fixer) {
        return fixer.createDimension(
                base,
                minY,
                height,
                logicalHeight,
                options.copy()
        ).toString(4);
    }

    private String createKey() {
        var stream = new ByteArrayOutputStream(41);
        try (var dos = new DataOutputStream(stream)) {
            dos.writeByte(base.ordinal());
            options.write(dos);
            Varint.writeUnsignedVarInt(logicalHeight, dos);
            Varint.writeUnsignedVarInt(height, dos);
            Varint.writeSignedVarInt(minY, dos);
        } catch (IOException e) {
            throw new RuntimeException("This is impossible", e);
        }

        return IO.encode(stream.toByteArray()).replace("=", ".").toLowerCase();
    }

    public IrisDimensionTypeOptions options() {
        return options.copy();
    }
}

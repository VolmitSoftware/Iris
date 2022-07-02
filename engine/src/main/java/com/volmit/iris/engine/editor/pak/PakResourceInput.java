package com.volmit.iris.engine.editor.pak;

import com.volmit.iris.engine.editor.Resolvable;
import com.volmit.iris.platform.PlatformNamespaceKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Consumer;
import java.util.function.Supplier;

@NoArgsConstructor
@Builder
@Data
@AllArgsConstructor
public class PakResourceInput {
    private Class<? extends Resolvable> type;
    private PlatformNamespaceKey key;
    private Supplier<InputStream> reader;
    private long size;

    public long write(OutputStream out) throws IOException {
        InputStream in = reader.get();
        long w = in.transferTo(out);
        in.close();
        return w;
    }

    public static PakResourceInput file(PlatformNamespaceKey key, Class<? extends Resolvable> type, File f) {
        return PakResourceInput.builder()
            .size(f.length())
            .key(key)
            .type(type)
            .reader(() -> {
                try {
                    return new FileInputStream(f);
                } catch(FileNotFoundException e) {
                    throw new RuntimeException(e);
                }
            })
            .build();
    }
}

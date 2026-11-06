package com.volmit.tooling;

import com.volmit.object.GeneratedObject;
import java.io.IOException;

/** The host's object file format, so tools can stage object files inside their own transactions. */
public interface ObjectCodec {
    /** The file name extension the host reads below the folder of {@link com.volmit.resource.PackResources#OBJECTS}, such as {@code .iob}. */
    String extension();

    byte[] encode(GeneratedObject object) throws IOException;

    GeneratedObject decode(byte[] bytes) throws IOException;
}

package com.volmit.iris.server.util;

import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

public class ByteBufUtil {

    public static String readString(ByteBuf byteBuf) {
        return new String(readBytes(byteBuf), StandardCharsets.UTF_8);
    }

    public static void writeString(ByteBuf byteBuf, String s) {
        writeBytes(byteBuf, s.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] readBytes(ByteBuf byteBuf) {
        byte[] bytes = new byte[byteBuf.readInt()];
        byteBuf.readBytes(bytes);
        return bytes;
    }

    public static void writeBytes(ByteBuf byteBuf, byte[] bytes) {
        byteBuf.writeInt(bytes.length);
        byteBuf.writeBytes(bytes);
    }
}

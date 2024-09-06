package com.volmit.iris.server.util;

import com.volmit.iris.server.packet.Packet;
import io.netty.buffer.ByteBuf;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Data
@NoArgsConstructor
public class ErrorPacket implements Packet {
    private String message;
    private String stackTrace;

    public ErrorPacket(String message) {
        this.message = message;
    }

    public ErrorPacket(String message, Throwable cause) {
        this.message = message;
        StringWriter writer = new StringWriter();
        cause.printStackTrace(new PrintWriter(writer));
        stackTrace = writer.toString();
    }

    @Override
    public void read(ByteBuf byteBuf) throws IOException {
        message = ByteBufUtil.readString(byteBuf);
        if (byteBuf.readBoolean()) {
            stackTrace = ByteBufUtil.readString(byteBuf);
        }
    }

    @Override
    public void write(ByteBuf byteBuf) throws IOException {
        ByteBufUtil.writeString(byteBuf, message);
        byteBuf.writeBoolean(stackTrace != null);
        if (stackTrace != null) {
            ByteBufUtil.writeString(byteBuf, stackTrace);
        }
    }

    public void log(Logger logger, Level level) {
        if (stackTrace == null) {
            logger.log(level, message);
            return;
        }
        logger.log(level, message + "\n" + stackTrace);
    }
}

package com.volmit.iris.server;

import com.volmit.iris.server.execption.RejectedException;
import com.volmit.iris.server.packet.Packet;
import com.volmit.iris.server.packet.handle.Decoder;
import com.volmit.iris.server.packet.handle.Encoder;
import com.volmit.iris.server.packet.handle.Prepender;
import com.volmit.iris.server.packet.handle.Splitter;
import com.volmit.iris.server.util.ErrorPacket;
import com.volmit.iris.server.util.ConnectionHolder;
import com.volmit.iris.server.util.PacketListener;
import com.volmit.iris.server.util.PacketSendListener;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;
import java.util.logging.Level;

@RequiredArgsConstructor
@Log(topic = "IrisConnection")
public class IrisConnection extends SimpleChannelInboundHandler<Packet> {
    private static EventLoopGroup WORKER;

    private Channel channel;
    private SocketAddress address;
    private final PacketListener listener;
    private final Queue<PacketHolder> queue = new ConcurrentLinkedQueue<>();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        if (!channel.isOpen() || listener == null || !listener.isAccepting()) return;

        try {
            listener.onPacket(packet);
        } catch (RejectedException e) {
            send(new ErrorPacket("Rejected: " + e.getMessage()));
        }
    }

    public void send(Packet packet) {
        this.send(packet, null);
    }

    public void send(Packet packet, @Nullable PacketSendListener listener) {
        if (!isConnected()) {
            queue.add(new PacketHolder(packet, listener));
            return;
        }

        flushQueue();
        sendPacket(packet, listener);
    }

    public boolean isConnected() {
        return channel != null && channel.isOpen();
    }

    public void disconnect() {
        try {
            if (channel != null && channel.isOpen()) {
                log.info("Closed on " + address);
                channel.close();
            }
            if (listener != null)
                listener.onDisconnect();
        } catch (Throwable e) {
            log.log(Level.SEVERE, "Failed to close on " + address, e);
        }
    }

    public void execute(Runnable runnable) {
        if (channel == null || !channel.isOpen()) return;
        channel.eventLoop().execute(runnable);
    }

    private void flushQueue() {
        if (this.channel != null && this.channel.isOpen()) {
            synchronized(this.queue) {
                PacketHolder packetHolder;
                while((packetHolder = this.queue.poll()) != null) {
                    sendPacket(packetHolder.packet, packetHolder.listener);
                }
            }
        }
    }

    private void sendPacket(Packet packet, @Nullable PacketSendListener listener) {
        if (!channel.eventLoop().inEventLoop()) {
            channel.eventLoop().execute(() -> sendPacket(packet, listener));
            return;
        }

        ChannelFuture channelFuture = channel.writeAndFlush(packet);

        if (listener != null) {
            channelFuture.addListener(future -> {
                if (future.isSuccess()) {
                    listener.onSuccess();
                } else {
                    Packet fallback = listener.onFailure();
                    if (fallback == null) return;
                    channel.writeAndFlush(fallback)
                            .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                }
            });
        }
        channelFuture.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);

        channel = ctx.channel();
        address = channel.remoteAddress();
        log.info("Opened on " + channel.remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        disconnect();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (!channel.isOpen()) return;
        ErrorPacket error;
        if (cause instanceof TimeoutException) {
            error = new ErrorPacket("Timed out");
        } else {
            error = new ErrorPacket("Internal Exception: " + cause.getMessage());
            log.log(Level.SEVERE, "Failed to send packet", cause);
        }

        sendPacket(error, PacketSendListener.thenRun(this::disconnect));
        channel.config().setAutoRead(false);
    }

    @Override
    public String toString() {
        return "IrisConnection{address=%s}".formatted(address);
    }

    public static void configureSerialization(Channel channel, ConnectionHolder holder) {
        channel.pipeline()
                .addLast("timeout", new ReadTimeoutHandler(30))
                .addLast("splitter", new Splitter())
                .addLast("decoder", new Decoder())
                .addLast("prepender", new Prepender())
                .addLast("encoder", new Encoder())
                .addLast("packet_handler", holder.getConnection());
    }

    public static <T extends ConnectionHolder> T connect(InetSocketAddress address, T holder) throws InterruptedException {
        new Bootstrap()
                .group(getWorker())
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel channel) {
                        channel.config().setOption(ChannelOption.TCP_NODELAY, true);
                        IrisConnection.configureSerialization(channel, holder);
                    }
                })
                .channel(NioSocketChannel.class)
                .connect(address)
                .sync();
        return holder;
    }

    private static class PacketHolder {
        private final Packet packet;
        @Nullable
        private final PacketSendListener listener;

        public PacketHolder(Packet packet, @Nullable PacketSendListener listener) {
            this.packet = packet;
            this.listener = listener;
        }
    }

    private static EventLoopGroup getWorker() {
        if (WORKER == null) {
            WORKER = new NioEventLoopGroup();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> WORKER.shutdownGracefully()));
        }
        return WORKER;
    }
}

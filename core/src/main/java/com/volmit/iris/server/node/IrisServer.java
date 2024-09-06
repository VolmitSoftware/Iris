package com.volmit.iris.server.node;

import com.volmit.iris.server.IrisConnection;
import com.volmit.iris.server.util.ConnectionHolder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

import java.util.function.Supplier;
import java.util.logging.Logger;

@Log(topic = "Iris-Server")
public class IrisServer implements AutoCloseable {
    private final NioEventLoopGroup bossGroup, workerGroup;
    private final Channel channel;
    private @Getter boolean running = true;

    public IrisServer(int port) throws InterruptedException {
        this("Iris-Server", port, IrisSession::new);
    }

    protected IrisServer(String name, int port, Supplier<ConnectionHolder> factory) throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(name, LogLevel.DEBUG))
                .childHandler(new Initializer(factory));

        channel = bootstrap.bind(port).sync().channel();

        getLogger().info("Started on port " + port);
    }

    @Override
    public void close() throws Exception {
        if (!running) return;
        running = false;
        channel.close().sync();
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();

        getLogger().info("Stopped");
    }

    protected Logger getLogger() {
        return log;
    }

    @RequiredArgsConstructor
    private static class Initializer extends ChannelInitializer<Channel> {
        private final Supplier<ConnectionHolder> factory;

        @Override
        protected void initChannel(Channel ch) {
            IrisConnection.configureSerialization(ch, factory.get());
        }
    }
}

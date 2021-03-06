package pro.gravit.launchserver.websocket;

import java.net.InetSocketAddress;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.logging.LoggingHandler;
import pro.gravit.launcher.request.Request;
import pro.gravit.launcher.request.auth.AuthRequest;
import pro.gravit.launcher.request.websockets.StandartClientWebSocketService;
import pro.gravit.launchserver.LaunchServer;
import pro.gravit.launchserver.websocket.fileserver.FileServerHandler;
import pro.gravit.utils.helper.LogHelper;

public class LauncherNettyServer implements AutoCloseable {
    public final ServerBootstrap serverBootstrap;
    public final EventLoopGroup bossGroup;
    public final EventLoopGroup workerGroup;
    public WebSocketFrameHandler frameHandler = null;
    private static final String WEBSOCKET_PATH = "/api";

    public LauncherNettyServer(LaunchServer server) {
        LaunchServer.NettyConfig config = server.config.netty;
        bossGroup = new NioEventLoopGroup(config.performance.bossThread);
        workerGroup = new NioEventLoopGroup(config.performance.workerThread);
        serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(config.logLevel))
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    public void initChannel(NioSocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        NettyConnectContext context = new NettyConnectContext();
                        //p.addLast(new LoggingHandler(LogLevel.INFO));
                        pipeline.addLast(new HttpServerCodec());
                        pipeline.addLast(new HttpObjectAggregator(65536));
                        if (server.config.netty.ipForwarding)
                            pipeline.addLast(new NettyIpForwardHandler(context));
                        pipeline.addLast(new WebSocketServerCompressionHandler());
                        pipeline.addLast(new WebSocketServerProtocolHandler(WEBSOCKET_PATH, null, true));
                        if (server.config.netty.fileServerEnabled)
                            pipeline.addLast(new FileServerHandler(server.updatesDir, true));
                        frameHandler = new WebSocketFrameHandler(context, server);
                        pipeline.addLast(frameHandler);
                    }
                });
        if (config.proxy != null && config.proxy.enabled) {
            LogHelper.info("Connect to main server %s");
            Request.service = StandartClientWebSocketService.initWebSockets(config.proxy.address, false);
            AuthRequest authRequest = new AuthRequest(config.proxy.login, config.proxy.password, config.proxy.auth_id, AuthRequest.ConnectTypes.PROXY);
            authRequest.initProxy = true;
            try {
                authRequest.request();
            } catch (Exception e) {
                LogHelper.error(e);
            }
        }
    }

    public ChannelFuture bind(InetSocketAddress address) {
        return serverBootstrap.bind(address);
    }

    @Override
    public void close() {
        workerGroup.shutdownGracefully();
        bossGroup.shutdownGracefully();
    }
}

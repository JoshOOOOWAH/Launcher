package pro.gravit.launchserver.websocket.json.auth;

import io.netty.channel.ChannelHandlerContext;
import pro.gravit.launcher.LauncherNetworkAPI;
import pro.gravit.launcher.events.request.RestoreSessionRequestEvent;
import pro.gravit.launchserver.socket.Client;
import pro.gravit.launchserver.websocket.WebSocketFrameHandler;
import pro.gravit.launchserver.websocket.json.SimpleResponse;

public class RestoreSessionResponse extends SimpleResponse {
    @LauncherNetworkAPI
    public long session;

    @Override
    public String getType() {
        return "restoreSession";
    }

    @Override
    public void execute(ChannelHandlerContext ctx, Client client) {
        Client rClient = server.sessionManager.getClient(session);
        if (rClient == null) {
            sendError("Session invalid");
        }
        WebSocketFrameHandler frameHandler = ctx.pipeline().get(WebSocketFrameHandler.class);
        frameHandler.setClient(rClient);
        sendResult(new RestoreSessionRequestEvent());
    }
}

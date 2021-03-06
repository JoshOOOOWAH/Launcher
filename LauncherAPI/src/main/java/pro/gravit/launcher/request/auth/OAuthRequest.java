package pro.gravit.launcher.request.auth;

import pro.gravit.launcher.HWID;
import pro.gravit.launcher.LauncherAPI;
import pro.gravit.launcher.LauncherNetworkAPI;
import pro.gravit.launcher.events.request.OAuthRequestEvent;
import pro.gravit.launcher.request.Request;
import pro.gravit.launcher.request.websockets.RequestInterface;

public class OAuthRequest extends Request<OAuthRequestEvent> implements RequestInterface {

    @LauncherNetworkAPI
    private final HWID hwid;
    @LauncherNetworkAPI
    private final String customText;

    @LauncherAPI
    public OAuthRequest(HWID hwid) {
        this.hwid = hwid;
        customText = "";
    }

    @Override
    public String getType() {
        return "oAuth";
    }
}
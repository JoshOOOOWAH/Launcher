package pro.gravit.launchserver.manangers.hook;

import pro.gravit.launchserver.socket.Client;
import pro.gravit.launchserver.websocket.json.auth.AuthResponse;
import pro.gravit.launchserver.websocket.json.auth.CheckServerResponse;
import pro.gravit.launchserver.websocket.json.auth.JoinServerResponse;
import pro.gravit.launchserver.websocket.json.auth.SetProfileResponse;
import pro.gravit.utils.BiHookSet;

public class AuthHookManager {
    public BiHookSet<AuthResponse.AuthContext, Client> preHook = new BiHookSet<>();
    public BiHookSet<AuthResponse.AuthContext, Client> postHook = new BiHookSet<>();
    public BiHookSet<CheckServerResponse, Client> checkServerHook = new BiHookSet<>();
    public BiHookSet<JoinServerResponse, Client> joinServerHook = new BiHookSet<>();
    public BiHookSet<SetProfileResponse, Client> setProfileHook = new BiHookSet<>();
}

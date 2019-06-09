package pro.gravit.launchserver.auth.provider;

import java.io.IOException;
import java.net.URL;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import pro.gravit.launcher.ClientPermissions;
import pro.gravit.utils.HTTPRequest;
import pro.gravit.utils.helper.SecurityHelper;

public final class JsonAuthProvider extends AuthProvider {
    private static Gson gson = new Gson();
    private URL url;
    private String apiKey;

    public class authResult {
        String username;
        String error;
        long permissions;
    }

    public class authRequest {
        public authRequest(String username, String password, String ip) {
            this.username = username;
            this.password = password;
            this.ip = ip;
        }

        public authRequest(String username, String password, String ip, String apiKey) {
            this.username = username;
            this.password = password;
            this.ip = ip;
            this.apiKey = apiKey;
        }

        String username;
        String password;
        String ip;
        String apiKey;
    }

    @Override
    public AuthProviderResult auth(String login, String password, String ip) throws IOException {
        authRequest authRequest = new authRequest(login, password, ip, apiKey);
        JsonElement request = gson.toJsonTree(authRequest);
        JsonElement content = HTTPRequest.jsonRequest(request, url);
        if (!content.isJsonObject())
            return authError("Authentication server response is malformed");

        authResult result = gson.fromJson(content, authResult.class);
        if (result.username != null)
            return new AuthProviderResult(result.username, SecurityHelper.randomStringToken(), new ClientPermissions(result.permissions));
        else if (result.error != null)
            return authError(result.error);
        else
            return authError("Authentication server response is malformed");
    }

    @Override
    public AuthProviderResult oauth(String id) throws Exception {
        return null;
    }

    @Override
    public void close() {
        // pass
    }
}

package pro.gravit.launchserver.auth.provider;

import java.io.IOException;
import java.util.Objects;

import pro.gravit.utils.helper.VerifyHelper;

public final class NullAuthProvider extends AuthProvider {
    private volatile AuthProvider provider;

    @Override
    public AuthProviderResult auth(String login, String password, String ip) throws Exception {
        return getProvider().auth(login, password, ip);
    }

    @Override
    public AuthProviderResult oauth(String id) throws Exception {
        return null;
    }

    @Override
    public void close() throws IOException {
        AuthProvider provider = this.provider;
        if (provider != null)
            provider.close();
    }

    private AuthProvider getProvider() {
        return VerifyHelper.verify(provider, Objects::nonNull, "Backend auth provider wasn't set");
    }


    public void setBackend(AuthProvider provider) {
        this.provider = provider;
    }
}

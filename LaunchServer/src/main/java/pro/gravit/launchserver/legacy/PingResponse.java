package pro.gravit.launchserver.legacy;

import java.io.IOException;

import pro.gravit.launcher.serialize.HInput;
import pro.gravit.launcher.serialize.HOutput;
import pro.gravit.launcher.serialize.SerializeLimits;
import pro.gravit.launchserver.LaunchServer;
import pro.gravit.launchserver.socket.Client;

public final class PingResponse extends Response {
    public PingResponse(LaunchServer server, long id, HInput input, HOutput output, String ip, Client clientData) {
        super(server, id, input, output, ip, clientData);
    }

    @Override
    public void reply() throws IOException {
        output.writeUnsignedByte(SerializeLimits.EXPECTED_BYTE);
    }
}

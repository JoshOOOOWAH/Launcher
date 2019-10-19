package pro.gravit.launchserver.components;

import java.util.HashMap;
import java.util.Map;

import pro.gravit.launchserver.LaunchServer;
import pro.gravit.utils.command.Command;

public class CommandRemoverComponent extends Component implements AutoCloseable {
    public final String[] removeList = new String[]{};
    public final transient Map<String, Command> commandsList = new HashMap<>();
    private transient LaunchServer server = null;

    @Override
    public void preInit(LaunchServer launchServer) {
    	server = launchServer;
    }

    @Override
    public void init(LaunchServer launchServer) {

    }

    @Override
    public void postInit(LaunchServer launchServer) {
        for (String cmd : removeList) {
            Command removedCmd = launchServer.commandHandler.unregisterCommand(cmd);
            if (removedCmd != null)
                commandsList.put(cmd, removedCmd);
        }
    }

    @Override
    public void close() {
        for (Map.Entry<String, Command> e : commandsList.entrySet()) {
            server.commandHandler.registerCommand(e.getKey(), e.getValue());
        }
    }
}

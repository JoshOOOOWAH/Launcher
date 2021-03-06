package pro.gravit.launcher.server;

import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;

import pro.gravit.launcher.managers.SimpleModuleManager;
import pro.gravit.launcher.managers.SimpleModulesConfigManager;
import pro.gravit.utils.PublicURLClassLoader;

public class ModulesManager extends SimpleModuleManager {
    public SimpleModulesConfigManager modulesConfigManager;

    public ModulesManager(ServerWrapper wrapper) {
        modules = new ArrayList<>();
        modulesConfigManager = new SimpleModulesConfigManager(Paths.get("modules-config"));
        classloader = new PublicURLClassLoader(new URL[0], ClassLoader.getSystemClassLoader());
        context = new ServerModuleContext(wrapper, classloader, modulesConfigManager);
    }
}

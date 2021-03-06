package pro.gravit.launchserver.modules;

import pro.gravit.launcher.modules.Module;
import pro.gravit.launcher.modules.ModuleContext;
import pro.gravit.utils.Version;

public class SimpleModule implements Module {
    @Override
    public void close() {
        // on stop
    }

    @Override
    public String getName() {
        return "SimpleModule";
    }

    @Override
    public Version getVersion() {
        return new Version(1, 0, 0, 0, Version.Type.UNKNOWN);
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public void init(ModuleContext context) {

    }

    @Override
    public void postInit(ModuleContext context) {

    }


    @Override
    public void preInit(ModuleContext context) {

    }
}

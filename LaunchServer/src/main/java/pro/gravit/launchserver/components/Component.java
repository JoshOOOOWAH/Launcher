package pro.gravit.launchserver.components;

import pro.gravit.launchserver.LaunchServer;
import pro.gravit.utils.ProviderMap;

public abstract class Component {
    public static ProviderMap<Component> providers = new ProviderMap<>();
    private static boolean registredComp = false;

    public static void registerComponents() {
        if (!registredComp) {
            providers.register("authLimiter", AuthLimiterComponent.class);
            providers.register("commandRemover", CommandRemoverComponent.class);
            providers.register("hibernate", HibernateConfiguratorComponent.class);
            registredComp = true;
        }
    }

    public abstract void preInit(LaunchServer launchServer);

    public abstract void init(LaunchServer launchServer);

    public abstract void postInit(LaunchServer launchServer);
}

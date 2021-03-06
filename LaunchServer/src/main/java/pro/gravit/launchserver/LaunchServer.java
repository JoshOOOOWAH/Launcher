package pro.gravit.launchserver;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.lang.ProcessBuilder.Redirect;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Timer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;

import io.netty.handler.logging.LogLevel;
import pro.gravit.launcher.Launcher;
import pro.gravit.launcher.LauncherConfig;
import pro.gravit.launcher.NeedGarbageCollection;
import pro.gravit.launcher.config.JsonConfigurable;
import pro.gravit.launcher.hasher.HashedDir;
import pro.gravit.launcher.managers.ConfigManager;
import pro.gravit.launcher.managers.GarbageManager;
import pro.gravit.launcher.profiles.ClientProfile;
import pro.gravit.launcher.serialize.signed.SignedObjectHolder;
import pro.gravit.launchserver.auth.AuthProviderPair;
import pro.gravit.launchserver.auth.handler.AuthHandler;
import pro.gravit.launchserver.auth.handler.MemoryAuthHandler;
import pro.gravit.launchserver.auth.hwid.AcceptHWIDHandler;
import pro.gravit.launchserver.auth.hwid.HWIDHandler;
import pro.gravit.launchserver.auth.permissions.DefaultPermissionsHandler;
import pro.gravit.launchserver.auth.permissions.JsonFilePermissionsHandler;
import pro.gravit.launchserver.auth.permissions.PermissionsHandler;
import pro.gravit.launchserver.auth.protect.ProtectHandler;
import pro.gravit.launchserver.auth.protect.StdProtectHandler;
import pro.gravit.launchserver.auth.provider.AuthProvider;
import pro.gravit.launchserver.auth.provider.RejectAuthProvider;
import pro.gravit.launchserver.auth.texture.RequestTextureProvider;
import pro.gravit.launchserver.auth.texture.TextureProvider;
import pro.gravit.launchserver.binary.EXEL4JLauncherBinary;
import pro.gravit.launchserver.binary.EXELauncherBinary;
import pro.gravit.launchserver.binary.JARLauncherBinary;
import pro.gravit.launchserver.binary.LauncherBinary;
import pro.gravit.launchserver.binary.ProguardConf;
import pro.gravit.launchserver.components.AuthLimiterComponent;
import pro.gravit.launchserver.components.Component;
import pro.gravit.launchserver.config.LaunchServerRuntimeConfig;
import pro.gravit.launchserver.dao.UserService;
import pro.gravit.launchserver.legacy.Response;
import pro.gravit.launchserver.manangers.*;
import pro.gravit.launchserver.manangers.hook.AuthHookManager;
import pro.gravit.launchserver.manangers.hook.BuildHookManager;
import pro.gravit.launchserver.manangers.hook.SocketHookManager;
import pro.gravit.launchserver.socket.ServerSocketHandler;
import pro.gravit.launchserver.websocket.NettyServerSocketHandler;
import pro.gravit.utils.Version;
import pro.gravit.utils.command.CommandHandler;
import pro.gravit.utils.command.JLineCommandHandler;
import pro.gravit.utils.command.StdCommandHandler;
import pro.gravit.utils.helper.CommonHelper;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.JVMHelper;
import pro.gravit.utils.helper.LogHelper;
import pro.gravit.utils.helper.SecurityHelper;
import pro.gravit.utils.helper.VerifyHelper;

public final class LaunchServer implements Runnable, AutoCloseable, Reloadable {
    @Override
    public void reload() throws Exception {
        config.close();
        LogHelper.info("Reading LaunchServer config file");
        try (BufferedReader reader = IOHelper.newReader(configFile)) {
            config = Launcher.gsonManager.gson.fromJson(reader, Config.class);
        }
        config.server = this;
        config.verify();
        config.init();
    }

    public static final class Config {
    	private transient LaunchServer server = null;
        public int legacyPort;

        private String legacyAddress;

        private String legacyBindAddress;

        public String projectName;

        public String[] mirrors;

        public String binaryName;

        public OAuthSetting OAuth;

        public boolean copyBinaries = true;

        public LauncherConfig.LauncherEnvironment env;

        // Handlers & Providers

        public AuthProviderPair[] auth;

        private transient AuthProviderPair authDefault;

        public AuthProviderPair getAuthProviderPair(String name) {
            for (AuthProviderPair pair : auth) {
                if (pair.name.equals(name)) return pair;
            }
            return null;
        }

        public ProtectHandler protectHandler;

        public PermissionsHandler permissionsHandler;

        public AuthProviderPair getAuthProviderPair() {
            if (authDefault != null) return authDefault;
            for (AuthProviderPair pair : auth) {
                if (pair.isDefault) {
                    authDefault = pair;
                    return pair;
                }
            }
            return null;
        }

        public HWIDHandler hwidHandler;

        public Map<String, Component> components;

        // Misc options
        public int threadCount;

        public int threadCoreCount;

        public ExeConf launch4j;
        public NettyConfig netty;
        public GuardLicenseConf guardLicense;

        public String whitelistRejectString;

        public boolean genMappings;
        public LauncherConf launcher;

        public boolean isWarningMissArchJava;
        public boolean enabledProGuard;
        public boolean enabledRadon;
        public boolean stripLineNumbers;
        public boolean deleteTempFiles;

        public String startScript;

        public String getLegacyAddress() {
            return legacyAddress;
        }


        public String getLegacyBindAddress() {
            return legacyBindAddress;
        }

        public void setProjectName(String projectName) {
            this.projectName = projectName;
        }

        public void setBinaryName(String binaryName) {
            this.binaryName = binaryName;
        }

        public void setEnv(LauncherConfig.LauncherEnvironment env) {
            this.env = env;
        }


        public SocketAddress getSocketAddress() {
            return new InetSocketAddress(legacyBindAddress, legacyPort);
        }


        public void setLegacyAddress(String legacyAddress) {
            this.legacyAddress = legacyAddress;
        }


        public void verify() {
            VerifyHelper.verify(getLegacyAddress(), VerifyHelper.NOT_EMPTY, "LaunchServer address can't be empty");
            if (auth == null || auth[0] == null) {
                throw new NullPointerException("AuthHandler must not be null");
            }
            boolean isOneDefault = false;
            for (AuthProviderPair pair : auth) {
                if (pair.isDefault) {
                    isOneDefault = true;
                    break;
                }
            }
            if (protectHandler == null) {
                throw new NullPointerException("ProtectHandler must not be null");
            }
            if (!isOneDefault) {
                throw new IllegalStateException("No auth pairs declared by default.");
            }
            if (permissionsHandler == null) {
                throw new NullPointerException("PermissionsHandler must not be null");
            }
            if (OAuth.ID == 0 || OAuth.BackURL == null){
                LogHelper.error("OAuthSetting must not be null");
            }
            if (env == null) {
                throw new NullPointerException("Env must not be null");
            }
            if (netty == null) {
                throw new NullPointerException("Netty must not be null");
            }
        }

        public void init() {
            Launcher.applyLauncherEnv(env);
            for (AuthProviderPair provider : auth) {
                provider.init(server);
            }
            permissionsHandler.init(server);
            hwidHandler.init();
            if (protectHandler != null) {
                protectHandler.checkLaunchServerLicense();
            }
            server.registerObject("permissionsHandler", permissionsHandler);
            for (AuthProviderPair pair : auth) {
                server.registerObject("auth.".concat(pair.name).concat(".provider"), pair.provider);
                server.registerObject("auth.".concat(pair.name).concat(".handler"), pair.handler);
                server.registerObject("auth.".concat(pair.name).concat(".texture"), pair.textureProvider);
            }

            Arrays.stream(mirrors).forEach(server.mirrorManager::addMirror);
        }

        public void close() {
            try {
                server.unregisterObject("permissionsHandler", permissionsHandler);
                for (AuthProviderPair pair : auth) {
                    server.unregisterObject("auth.".concat(pair.name).concat(".provider"), pair.provider);
                    server.unregisterObject("auth.".concat(pair.name).concat(".handler"), pair.handler);
                    server.unregisterObject("auth.".concat(pair.name).concat(".texture"), pair.textureProvider);
                }
            } catch (Exception e) {
                LogHelper.error(e);
            }
            try {
                for (AuthProviderPair p : auth) p.close();
            } catch (IOException e) {
                LogHelper.error(e);
            }
            try {
                hwidHandler.close();
            } catch (Exception e) {
                LogHelper.error(e);
            }
            try {
                permissionsHandler.close();
            } catch (Exception e) {
                LogHelper.error(e);
            }
        }
    }

    public static class OAuthSetting{

        public int ID;
        public String Secret;
        public String BackURL;

    }

    public static class ExeConf {
        public boolean enabled;
        public boolean setMaxVersion;
        public String maxVersion;
        public String productName;
        public String productVer;
        public String fileDesc;
        public String fileVer;
        public String internalName;
        public String copyright;
        public String trademarks;

        public String txtFileVersion;
        public String txtProductVersion;
    }

    public static class NettyUpdatesBind {
        public String url;
        public boolean zip;
    }

    public class LauncherConf {
        public String guardType;
        public boolean attachLibraryBeforeProGuard;
    }

    public class NettyConfig {
        public boolean fileServerEnabled;
        public boolean sendExceptionEnabled;
        public boolean ipForwarding;
        public String launcherURL;
        public String downloadURL;
        public String launcherEXEURL;
        public String address;
        public Map<String, NettyUpdatesBind> bindings = new HashMap<>();
        public NettyPerformanceConfig performance;
        public NettyBindAddress[] binds;
        public LogLevel logLevel = LogLevel.DEBUG;
        public NettyProxyConfig proxy = new NettyProxyConfig();
    }

    public class NettyPerformanceConfig {
        public int bossThread;
        public int workerThread;
    }

    public class NettyProxyConfig {
        public boolean enabled;
        public String address = "ws://localhost:9275/api";
        public String login = "login";
        public String password = "password";
        public String auth_id = "std";
        public ArrayList<String> requests = new ArrayList<>();
    }

    public class NettyBindAddress {
        public String address;
        public int port;

        public NettyBindAddress(String address, int port) {
            this.address = address;
            this.port = port;
        }
    }

    public class GuardLicenseConf {
        public String name;
        public String key;
        public String encryptKey;
    }

    private final class ProfilesFileVisitor extends SimpleFileVisitor<Path> {
        private final Collection<ClientProfile> result;

        private ProfilesFileVisitor(Collection<ClientProfile> result) {
            this.result = result;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            LogHelper.info("Syncing '%s' profile", IOHelper.getFileName(file));

            // Read profile
            ClientProfile profile;
            try (BufferedReader reader = IOHelper.newReader(file)) {
                profile = Launcher.gsonManager.gson.fromJson(reader, ClientProfile.class);
            }
            profile.verify();

            // Add SIGNED profile to result list
            result.add(profile);
            return super.visitFile(file, attrs);
        }
    }

    public static void main(String... args) throws Throwable {
        JVMHelper.checkStackTrace(LaunchServer.class);
        JVMHelper.verifySystemProperties(LaunchServer.class, true);
        LogHelper.addOutput(IOHelper.WORKING_DIR.resolve("LaunchServer.log"));
        LogHelper.printVersion("LaunchServer");
        LogHelper.printLicense("LaunchServer");
        if (!StarterAgent.isAgentStarted()) {
            LogHelper.error("StarterAgent is not started!");
            LogHelper.error("Your should add to JVM options this option: `-javaagent:LaunchServer.jar`");
        }

        // Start LaunchServer
        long startTime = System.currentTimeMillis();
        try {
            @SuppressWarnings("resource")
            LaunchServer launchserver = new LaunchServer(IOHelper.WORKING_DIR, false, args);
            if (args.length == 0) launchserver.run();
            else { //Обработка команды
                launchserver.commandHandler.eval(args, false);
            }
        } catch (Throwable exc) {
            LogHelper.error(exc);
            return;
        }
        long endTime = System.currentTimeMillis();
        LogHelper.debug("LaunchServer started in %dms", endTime - startTime);
    }

    // Constant paths

    public final Path dir;

    public final boolean testEnv;

    public final Path launcherLibraries;

    public final Path launcherLibrariesCompile;

    public final List<String> args;

    public final Path configFile;
    public final Path runtimeConfigFile;

    public final Path publicKeyFile;

    public final Path privateKeyFile;

    public final Path updatesDir;

    //public static LaunchServer server = null;

    public final Path profilesDir;
    // Server config

    public Config config;
    public LaunchServerRuntimeConfig runtime;


    public final RSAPublicKey publicKey;

    public final RSAPrivateKey privateKey;
    // Launcher binary

    public final JARLauncherBinary launcherBinary;

    public Class<? extends LauncherBinary> launcherEXEBinaryClass;

    public final LauncherBinary launcherEXEBinary;
    // HWID ban + anti-brutforce

    public final SessionManager sessionManager;

    public final SocketHookManager socketHookManager;

    public final AuthHookManager authHookManager;
    // Server

    public final ModulesManager modulesManager;

    public final UserService userService;

    public final MirrorManager mirrorManager;

    public final ReloadManager reloadManager;

    public final ReconfigurableManager reconfigurableManager;

    public final ConfigManager configManager;


    public final BuildHookManager buildHookManager;

    public final ProguardConf proguardConf;


    public final CommandHandler commandHandler;

    public final OAuthManager cacheManager;

    public final ServerSocketHandler serverSocketHandler;

    public final NettyServerSocketHandler nettyServerSocketHandler;

    private final AtomicBoolean started = new AtomicBoolean(false);

    // Updates and profiles
    private volatile List<ClientProfile> profilesList;
    public volatile Map<String, SignedObjectHolder<HashedDir>> updatesDirMap;

    public final Timer taskPool;

    public static Class<? extends LauncherBinary> defaultLauncherEXEBinaryClass = null;

    public LaunchServer(Path dir, boolean testEnv, String[] args) throws IOException, InvalidKeySpecException {
        this.dir = dir;
        this.testEnv = testEnv;
        taskPool = new Timer("Timered task worker thread", true);
        launcherLibraries = dir.resolve("launcher-libraries");
        launcherLibrariesCompile = dir.resolve("launcher-libraries-compile");
        this.args = Arrays.asList(args);
        configFile = dir.resolve("LaunchServer.conf");
        runtimeConfigFile = dir.resolve("RuntimeLaunchServer.conf");
        publicKeyFile = dir.resolve("public.key");
        privateKeyFile = dir.resolve("private.key");
        updatesDir = dir.resolve("updates");
        profilesDir = dir.resolve("profiles");

        //Registration handlers and providers
        AuthHandler.registerHandlers();
        AuthProvider.registerProviders();
        TextureProvider.registerProviders();
        HWIDHandler.registerHandlers();
        PermissionsHandler.registerHandlers();
        Response.registerResponses();
        Component.registerComponents();
        ProtectHandler.registerHandlers();
        //LaunchServer.server = this;

        // Set command handler
        CommandHandler localCommandHandler;
        if (testEnv)
            localCommandHandler = new StdCommandHandler(false);
        else
            try {
                Class.forName("org.jline.terminal.Terminal");

                // JLine2 available
                localCommandHandler = new JLineCommandHandler();
                LogHelper.info("JLine2 terminal enabled");
            } catch (ClassNotFoundException ignored) {
                localCommandHandler = new StdCommandHandler(true);
                LogHelper.warning("JLine2 isn't in classpath, using std");
            }
        pro.gravit.launchserver.command.handler.CommandHandler.registerCommands(localCommandHandler, this);
        commandHandler = localCommandHandler;

        // Set key pair
        if (IOHelper.isFile(publicKeyFile) && IOHelper.isFile(privateKeyFile)) {
            LogHelper.info("Reading RSA keypair");
            publicKey = SecurityHelper.toPublicRSAKey(IOHelper.read(publicKeyFile));
            privateKey = SecurityHelper.toPrivateRSAKey(IOHelper.read(privateKeyFile));
            if (!publicKey.getModulus().equals(privateKey.getModulus()))
                throw new IOException("Private and public key modulus mismatch");
        } else {
            LogHelper.info("Generating RSA keypair");
            KeyPair pair = SecurityHelper.genRSAKeyPair();
            publicKey = (RSAPublicKey) pair.getPublic();
            privateKey = (RSAPrivateKey) pair.getPrivate();

            // Write key pair list
            LogHelper.info("Writing RSA keypair list");
            IOHelper.write(publicKeyFile, publicKey.getEncoded());
            IOHelper.write(privateKeyFile, privateKey.getEncoded());
        }

        // Print keypair fingerprints
        CRC32 crc = new CRC32();
        crc.update(publicKey.getModulus().toByteArray()); // IDEA говорит, что это Java 9 API. WTF?
        LogHelper.subInfo("Modulus CRC32: 0x%08x", crc.getValue());

        // Load class bindings.
        launcherEXEBinaryClass = defaultLauncherEXEBinaryClass;

        // pre init modules
        modulesManager = new ModulesManager(this);
        modulesManager.autoload(dir.resolve("modules"));
        modulesManager.preInitModules();
        initGson();

        // Read LaunchServer config
        generateConfigIfNotExists(testEnv);
        LogHelper.info("Reading LaunchServer config file");
        try (BufferedReader reader = IOHelper.newReader(configFile)) {
            config = Launcher.gsonManager.gson.fromJson(reader, Config.class);
        }
        config.server = this;
        if (!Files.exists(runtimeConfigFile)) {
            LogHelper.info("Reset LaunchServer runtime config file");
            runtime = new LaunchServerRuntimeConfig();
            runtime.reset();
        } else {
            LogHelper.info("Reading LaunchServer runtime config file");
            try (BufferedReader reader = IOHelper.newReader(runtimeConfigFile)) {
                runtime = Launcher.gsonManager.gson.fromJson(reader, LaunchServerRuntimeConfig.class);
            }
        }
        runtime.verify();
        config.verify();
        Launcher.applyLauncherEnv(config.env);
        for (AuthProviderPair provider : config.auth) {
            provider.init(this);
        }
        config.permissionsHandler.init(this);
        config.hwidHandler.init();
        if (config.protectHandler != null) {
            config.protectHandler.checkLaunchServerLicense();
        }
        if (config.components != null) {
            LogHelper.debug("PreInit components");
            config.components.forEach((k, v) -> {
                LogHelper.subDebug("PreInit component %s", k);
                v.preInit(this);
            });
            LogHelper.debug("PreInit components successful");
        }

        // build hooks, anti-brutforce and other
        buildHookManager = new BuildHookManager();
        proguardConf = new ProguardConf(this);
        sessionManager = new SessionManager();
        cacheManager = new OAuthManager(this);
        mirrorManager = new MirrorManager();
        reloadManager = new ReloadManager();
        reconfigurableManager = new ReconfigurableManager();
        socketHookManager = new SocketHookManager();
        authHookManager = new AuthHookManager();
        configManager = new ConfigManager();
        userService = new UserService(this);
        GarbageManager.registerNeedGC(sessionManager);
        GarbageManager.registerNeedGC(cacheManager);
        reloadManager.registerReloadable("launchServer", this);
        registerObject("permissionsHandler", config.permissionsHandler);
        for (int i = 0; i < config.auth.length; ++i) {
            AuthProviderPair pair = config.auth[i];
            registerObject("auth.".concat(pair.name).concat(".provider"), pair.provider);
            registerObject("auth.".concat(pair.name).concat(".handler"), pair.handler);
            registerObject("auth.".concat(pair.name).concat(".texture"), pair.textureProvider);
        }

        Arrays.stream(config.mirrors).forEach(mirrorManager::addMirror);

        // init modules
        modulesManager.initModules();
        if (config.components != null) {
            LogHelper.debug("Init components");
            config.components.forEach((k, v) -> {
                LogHelper.subDebug("Init component %s", k);
                registerObject("component.".concat(k), v);
                v.init(this);
            });
            LogHelper.debug("Init components successful");
        }

        // Set launcher EXE binary
        launcherBinary = new JARLauncherBinary(this);
        launcherEXEBinary = binary();

        launcherBinary.init();
        launcherEXEBinary.init();
        syncLauncherBinaries();

        // Sync updates dir
        if (!IOHelper.isDir(updatesDir))
            Files.createDirectory(updatesDir);
        syncUpdatesDir(null);

        // Sync profiles dir
        if (!IOHelper.isDir(profilesDir))
            Files.createDirectory(profilesDir);
        syncProfilesDir();


        // Set server socket thread
        serverSocketHandler = new ServerSocketHandler(this, sessionManager);

        // post init modules
        modulesManager.postInitModules();
        if (config.components != null) {
            LogHelper.debug("PostInit components");
            config.components.forEach((k, v) -> {
                LogHelper.subDebug("PostInit component %s", k);
                v.postInit(this);
            });
            LogHelper.debug("PostInit components successful");
        }
        // start updater
        if (config.netty != null)
            nettyServerSocketHandler = new NettyServerSocketHandler(this);
        else
            nettyServerSocketHandler = null;
    }

    public static void initGson() {
        Launcher.gsonManager = new LaunchServerGsonManager();
        Launcher.gsonManager.initGson();
    }

    private LauncherBinary binary() {
        if (launcherEXEBinaryClass != null) {
            try {
                return launcherEXEBinaryClass.getConstructor(LaunchServer.class).newInstance(this);
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                LogHelper.error(e);
            }
        }
        try {
            Class.forName("net.sf.launch4j.Builder");
            if (config.launch4j.enabled) return new EXEL4JLauncherBinary(this);
        } catch (ClassNotFoundException ignored) {
            LogHelper.warning("Launch4J isn't in classpath.");
        }
        return new EXELauncherBinary(this);
    }


    public void buildLauncherBinaries() throws IOException {
        launcherBinary.build();
        launcherEXEBinary.build();
    }

    public void close() {
        serverSocketHandler.close();

        // Close handlers & providers
        config.close();
        modulesManager.close();
        LogHelper.info("Save LaunchServer runtime config");
        try (Writer writer = IOHelper.newWriter(runtimeConfigFile)) {
            if (Launcher.gsonManager.configGson != null) {
                Launcher.gsonManager.configGson.toJson(runtime, writer);
            } else {
                LogHelper.error("Error writing LaunchServer runtime config file. Gson is null");
            }
        } catch (IOException e) {
            LogHelper.error(e);
        }
        // Print last message before death :(
        LogHelper.info("LaunchServer stopped");
    }

    private void generateConfigIfNotExists(boolean testEnv) throws IOException {
        if (IOHelper.isFile(configFile))
            return;

        // Create new config
        LogHelper.info("Creating LaunchServer config");
        Config newConfig = new Config();
        newConfig.mirrors = new String[]{"http://mirror.gravitlauncher.ml/", "https://mirror.gravit.pro/"};
        newConfig.launch4j = new ExeConf();
        newConfig.launch4j.enabled = true;
        newConfig.launch4j.copyright = "© GravitLauncher Team";
        newConfig.launch4j.fileDesc = "GravitLauncher ".concat(Version.getVersion().getVersionString());
        newConfig.launch4j.fileVer = Version.getVersion().getVersionString().concat(".").concat(String.valueOf(Version.getVersion().patch));
        newConfig.launch4j.internalName = "Launcher";
        newConfig.launch4j.trademarks = "This product is licensed under GPLv3";
        newConfig.launch4j.txtFileVersion = "%s, build %d";
        newConfig.launch4j.txtProductVersion = "%s, build %d";
        newConfig.launch4j.productName = "GravitLauncher";
        newConfig.launch4j.productVer = newConfig.launch4j.fileVer;
        newConfig.launch4j.maxVersion = "1.8.999";
        newConfig.env = LauncherConfig.LauncherEnvironment.STD;
        newConfig.startScript = JVMHelper.OS_TYPE.equals(JVMHelper.OS.MUSTDIE) ? "." + File.separator + "start.bat" : "." + File.separator + "start.sh";
        newConfig.hwidHandler = new AcceptHWIDHandler();
        newConfig.auth = new AuthProviderPair[]{new AuthProviderPair(new RejectAuthProvider("Настройте authProvider"),
                new MemoryAuthHandler(),
                new RequestTextureProvider("http://example.com/skins/%username%.png", "http://example.com/cloaks/%username%.png")
                , "std")};
        newConfig.auth[0].displayName = "Default";
        newConfig.protectHandler = new StdProtectHandler();
        if (testEnv) newConfig.permissionsHandler = new DefaultPermissionsHandler();
        else newConfig.permissionsHandler = new JsonFilePermissionsHandler();
        newConfig.legacyPort = 7240;
        newConfig.legacyBindAddress = "0.0.0.0";
        newConfig.binaryName = "Launcher";
        newConfig.whitelistRejectString = "Вас нет в белом списке";

        newConfig.OAuth = new OAuthSetting();
        newConfig.OAuth.ID = 0;
        newConfig.OAuth.Secret = "xxx";

        newConfig.netty = new NettyConfig();
        newConfig.netty.fileServerEnabled = true;
        newConfig.netty.binds = new NettyBindAddress[]{new NettyBindAddress("0.0.0.0", 9274)};
        newConfig.netty.performance = new NettyPerformanceConfig();
        newConfig.netty.performance.bossThread = 2;
        newConfig.netty.performance.workerThread = 8;

        newConfig.launcher = new LauncherConf();
        newConfig.launcher.guardType = "no";

        newConfig.threadCoreCount = 0; // on your own
        newConfig.threadCount = JVMHelper.OPERATING_SYSTEM_MXBEAN.getAvailableProcessors() >= 4 ? JVMHelper.OPERATING_SYSTEM_MXBEAN.getAvailableProcessors() / 2 : JVMHelper.OPERATING_SYSTEM_MXBEAN.getAvailableProcessors();

        newConfig.enabledRadon = true;
        newConfig.genMappings = true;
        newConfig.enabledProGuard = true;
        newConfig.stripLineNumbers = true;
        newConfig.deleteTempFiles = true;
        newConfig.isWarningMissArchJava = true;

        newConfig.components = new HashMap<>();
        AuthLimiterComponent authLimiterComponent = new AuthLimiterComponent();
        authLimiterComponent.rateLimit = 3;
        authLimiterComponent.rateLimitMilis = 8000;
        authLimiterComponent.message = "Превышен лимит авторизаций";
        newConfig.components.put("authLimiter", authLimiterComponent);

        // Set server address
        String address;
        if (testEnv) {
            address = "localhost";
            newConfig.setProjectName("test");
        } else {
            System.out.println("LaunchServer address(default: localhost): ");
            address = commandHandler.readLine();
            System.out.println("LaunchServer projectName: ");
            newConfig.setProjectName(commandHandler.readLine());
        }
        if (address == null || address.isEmpty()) {
            LogHelper.error("Address null. Using localhost");
            address = "localhost";
        }
        if (newConfig.projectName == null || newConfig.projectName.isEmpty()) {
            LogHelper.error("ProjectName null. Using MineCraft");
            newConfig.projectName = "MineCraft";
        }

        newConfig.legacyAddress = address;
        newConfig.netty.address = "ws://" + address + ":9274/api";
        newConfig.netty.downloadURL = "http://" + address + ":9274/%dirname%/";
        newConfig.netty.launcherURL = "http://" + address + ":9274/Launcher.jar";
        newConfig.netty.launcherEXEURL = "http://" + address + ":9274/Launcher.exe";
        newConfig.netty.sendExceptionEnabled = true;

        newConfig.OAuth.BackURL = "http://"+ address + "/OAuth.html";

        // Write LaunchServer config
        LogHelper.info("Writing LaunchServer config file");
        try (BufferedWriter writer = IOHelper.newWriter(configFile)) {
            Launcher.gsonManager.configGson.toJson(newConfig, writer);
        }
    }

    public List<ClientProfile> getProfiles() {
        return profilesList;
    }

    public void setProfiles(List<ClientProfile> profilesList) {
        this.profilesList = Collections.unmodifiableList(profilesList);
    }

    public SignedObjectHolder<HashedDir> getUpdateDir(String name) {
        return updatesDirMap.get(name);
    }


    public Set<Entry<String, SignedObjectHolder<HashedDir>>> getUpdateDirs() {
        return updatesDirMap.entrySet();
    }


    public void rebindServerSocket() {
        serverSocketHandler.close();
        CommonHelper.newThread("Server Socket Thread", false, serverSocketHandler).start();
    }

    public void rebindNettyServerSocket() {
        nettyServerSocketHandler.close();
        CommonHelper.newThread("Netty Server Socket Thread", false, nettyServerSocketHandler).start();
    }

    @Override
    public void run() {
        if (started.getAndSet(true))
            throw new IllegalStateException("LaunchServer has been already started");

        // Add shutdown hook, then start LaunchServer
        if (!this.testEnv) {
            JVMHelper.RUNTIME.addShutdownHook(CommonHelper.newThread(null, false, this::close));
            CommonHelper.newThread("Command Thread", true, commandHandler).start();
        }
        rebindServerSocket();
        if (config.netty != null)
            rebindNettyServerSocket();
        modulesManager.finishModules();
    }


    public void syncLauncherBinaries() throws IOException {
        LogHelper.info("Syncing launcher binaries");

        // Syncing launcher binary
        LogHelper.info("Syncing launcher binary file");
        if (!launcherBinary.sync()) LogHelper.warning("Missing launcher binary file");

        // Syncing launcher EXE binary
        LogHelper.info("Syncing launcher EXE binary file");
        if (!launcherEXEBinary.sync() && config.launch4j.enabled)
            LogHelper.warning("Missing launcher EXE binary file");

    }


    public void syncProfilesDir() throws IOException {
        LogHelper.info("Syncing profiles dir");
        List<ClientProfile> newProfies = new LinkedList<>();
        IOHelper.walk(profilesDir, new ProfilesFileVisitor(newProfies), false);

        // Sort and set new profiles
        newProfies.sort(Comparator.comparing(a -> a));
        profilesList = Collections.unmodifiableList(newProfies);
    }


    public void syncUpdatesDir(Collection<String> dirs) throws IOException {
        LogHelper.info("Syncing updates dir");
        Map<String, SignedObjectHolder<HashedDir>> newUpdatesDirMap = new HashMap<>(16);
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(updatesDir)) {
            for (final Path updateDir : dirStream) {
                if (Files.isHidden(updateDir))
                    continue; // Skip hidden

                // Resolve name and verify is dir
                String name = IOHelper.getFileName(updateDir);
                if (!IOHelper.isDir(updateDir)) {
                    if (!IOHelper.isFile(updateDir) && Arrays.asList(".jar", ".exe", ".hash").stream().noneMatch(e -> updateDir.toString().endsWith(e)))
                        LogHelper.warning("Not update dir: '%s'", name);
                    continue;
                }

                // Add from previous map (it's guaranteed to be non-null)
                if (dirs != null && !dirs.contains(name)) {
                    SignedObjectHolder<HashedDir> hdir = updatesDirMap.get(name);
                    if (hdir != null) {
                        newUpdatesDirMap.put(name, hdir);
                        continue;
                    }
                }

                // Sync and sign update dir
                LogHelper.info("Syncing '%s' update dir", name);
                HashedDir updateHDir = new HashedDir(updateDir, null, true, true);
                newUpdatesDirMap.put(name, new SignedObjectHolder<>(updateHDir, privateKey));
            }
        }
        updatesDirMap = Collections.unmodifiableMap(newUpdatesDirMap);
    }

    public void restart() {
        ProcessBuilder builder = new ProcessBuilder();
        if (config.startScript != null) builder.command(Collections.singletonList(config.startScript));
        else throw new IllegalArgumentException("Please create start script and link it as startScript in config.");
        builder.directory(this.dir.toFile());
        builder.inheritIO();
        builder.redirectErrorStream(true);
        builder.redirectOutput(Redirect.PIPE);
        try {
            builder.start();
        } catch (IOException e) {
            LogHelper.error(e);
        }
    }

    public void registerObject(String name, Object object) {
        if (object instanceof Reloadable) {
            reloadManager.registerReloadable(name, (Reloadable) object);
        }
        if (object instanceof Reconfigurable) {
            reconfigurableManager.registerReconfigurable(name, (Reconfigurable) object);
        }
        if (object instanceof NeedGarbageCollection) {
            GarbageManager.registerNeedGC((NeedGarbageCollection) object);
        }
        if (object instanceof JsonConfigurable) {

        }
    }

    public void unregisterObject(String name, Object object) {
        if (object instanceof Reloadable) {
            reloadManager.unregisterReloadable(name);
        }
        if (object instanceof Reconfigurable) {
            reconfigurableManager.unregisterReconfigurable(name);
        }
        if (object instanceof NeedGarbageCollection) {
            GarbageManager.unregisterNeedGC((NeedGarbageCollection) object);
        }
        if (object instanceof JsonConfigurable) {

        }
    }

    public void fullyRestart() {
        restart();
        JVMHelper.RUNTIME.exit(0);
    }
}

package me.psikuvit.betterWarden.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed, injectable view of config.yml's {@code warden.*} tree. */
@ConfigurationProperties(prefix = "warden")
public class CoreConfig {

    private int configVersion = 1;
    private String serverName = "My Server";
    private Storage storage = new Storage();
    private Panel panel = new Panel();
    private LoginGate loginGate = new LoginGate();
    private AltDetection altDetection = new AltDetection();
    private Security security = new Security();

    public int getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(int configVersion) {
        this.configVersion = configVersion;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    public Panel getPanel() {
        return panel;
    }

    public void setPanel(Panel panel) {
        this.panel = panel;
    }

    public LoginGate getLoginGate() {
        return loginGate;
    }

    public void setLoginGate(LoginGate loginGate) {
        this.loginGate = loginGate;
    }

    public AltDetection getAltDetection() {
        return altDetection;
    }

    public void setAltDetection(AltDetection altDetection) {
        this.altDetection = altDetection;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public static class Storage {
        private String type = "sqlite";
        private Mysql mysql = new Mysql();

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Mysql getMysql() {
            return mysql;
        }

        public void setMysql(Mysql mysql) {
            this.mysql = mysql;
        }
    }

    public static class Mysql {
        private String host = "localhost";
        private int port = 3306;
        private String database = "warden";
        private String username = "warden";
        private String password = "";

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class Panel {
        private int port = 8095;

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }

    public static class LoginGate {
        private boolean failOpen = true;

        public boolean isFailOpen() {
            return failOpen;
        }

        public void setFailOpen(boolean failOpen) {
            this.failOpen = failOpen;
        }
    }

    public static class AltDetection {
        private boolean banEvasionAutoAction = false;

        public boolean isBanEvasionAutoAction() {
            return banEvasionAutoAction;
        }

        public void setBanEvasionAutoAction(boolean banEvasionAutoAction) {
            this.banEvasionAutoAction = banEvasionAutoAction;
        }
    }

    public static class Security {
        private String ipSalt = "";

        public String getIpSalt() {
            return ipSalt;
        }

        public void setIpSalt(String ipSalt) {
            this.ipSalt = ipSalt;
        }
    }
}

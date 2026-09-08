package me.psikuvit.betterWarden.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** Typed, injectable view of config.yml's {@code warden.*} tree. */
@ConfigurationProperties(prefix = "warden")
public class CoreConfig {

    private int configVersion = 1;
    private String serverName = "My Server";
    /** HOST (default) boots a local core; CLIENT skips it - see ConfigBootstrap.readMode. */
    private String mode = "HOST";
    private Storage storage = new Storage();
    private Panel panel = new Panel();
    private LoginGate loginGate = new LoginGate();
    private AltDetection altDetection = new AltDetection();
    private Security security = new Security();
    private Maintenance maintenance = new Maintenance();
    private Node node = new Node();
    private ChatFilter chatFilter = new ChatFilter();
    private Discord discord = new Discord();
    private Branding branding = new Branding();

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

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
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

    public Maintenance getMaintenance() {
        return maintenance;
    }

    public void setMaintenance(Maintenance maintenance) {
        this.maintenance = maintenance;
    }

    public Node getNode() {
        return node;
    }

    public void setNode(Node node) {
        this.node = node;
    }

    public ChatFilter getChatFilter() {
        return chatFilter;
    }

    public void setChatFilter(ChatFilter chatFilter) {
        this.chatFilter = chatFilter;
    }

    public Discord getDiscord() {
        return discord;
    }

    public void setDiscord(Discord discord) {
        this.discord = discord;
    }

    public Branding getBranding() {
        return branding;
    }

    public void setBranding(Branding branding) {
        this.branding = branding;
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
        /** Empty = bind all interfaces (Spring Boot's own default). Set to "127.0.0.1" when a reverse proxy on the same host is the only intended way in. */
        private String bindAddress = "";
        /** Off by default: blindly trusting X-Forwarded-* lets a client spoof its own IP unless something in front actually strips/sets them. Turn on only when a reverse proxy or tunnel (nginx/Caddy/Cloudflare Tunnel) is the sole way in. */
        private boolean trustForwardedHeaders = false;
        /** Externally-reachable panel URL (e.g. behind a Cloudflare Tunnel or reverse proxy). Blank = not configured - anything that would otherwise deep-link into the panel (the Discord report feed's "Open in panel" button) just omits that link instead of guessing a wrong one. */
        private String publicUrl = "";

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getBindAddress() {
            return bindAddress;
        }

        public void setBindAddress(String bindAddress) {
            this.bindAddress = bindAddress;
        }

        public boolean isTrustForwardedHeaders() {
            return trustForwardedHeaders;
        }

        public void setTrustForwardedHeaders(boolean trustForwardedHeaders) {
            this.trustForwardedHeaders = trustForwardedHeaders;
        }

        public String getPublicUrl() {
            return publicUrl;
        }

        public void setPublicUrl(String publicUrl) {
            this.publicUrl = publicUrl;
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
        private String nodeToken = "";

        public String getIpSalt() {
            return ipSalt;
        }

        public void setIpSalt(String ipSalt) {
            this.ipSalt = ipSalt;
        }

        public String getNodeToken() {
            return nodeToken;
        }

        public void setNodeToken(String nodeToken) {
            this.nodeToken = nodeToken;
        }
    }

    /** Proxy-side only for now (Stage 3 handshake) - meaningless on a HOST-mode Paper server. */
    public static class Node {
        private String advertiseHost = "localhost";

        public String getAdvertiseHost() {
            return advertiseHost;
        }

        public void setAdvertiseHost(String advertiseHost) {
            this.advertiseHost = advertiseHost;
        }
    }

    public static class Maintenance {
        private int reportAutoCloseHours = 72;
        private int ticketAutoCloseHours = 168;

        public int getReportAutoCloseHours() {
            return reportAutoCloseHours;
        }

        public void setReportAutoCloseHours(int reportAutoCloseHours) {
            this.reportAutoCloseHours = reportAutoCloseHours;
        }

        public int getTicketAutoCloseHours() {
            return ticketAutoCloseHours;
        }

        public void setTicketAutoCloseHours(int ticketAutoCloseHours) {
            this.ticketAutoCloseHours = ticketAutoCloseHours;
        }
    }

    /** docs/spec/01-CORE.txt ChatFilterService. blockedWords entries are plain words matched against the normalized form by default, or a real regex against the raw message if prefixed "regex:" (see ChatFilterService). */
    public static class ChatFilter {
        private boolean enabled = true;
        private List<String> blockedWords = new ArrayList<>();
        private boolean adDetection = true;
        private int capsThresholdPercent = 70;
        private int capsMinLength = 10;
        private int spamMessageLimit = 5;
        private int spamWindowSeconds = 8;
        private int repeatMessageLimit = 3;
        private int autoPunishThreshold = 5;
        private int autoPunishWindowMinutes = 10;
        private int autoPunishDurationMinutes = 30;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getBlockedWords() {
            return blockedWords;
        }

        public void setBlockedWords(List<String> blockedWords) {
            this.blockedWords = blockedWords;
        }

        public boolean isAdDetection() {
            return adDetection;
        }

        public void setAdDetection(boolean adDetection) {
            this.adDetection = adDetection;
        }

        public int getCapsThresholdPercent() {
            return capsThresholdPercent;
        }

        public void setCapsThresholdPercent(int capsThresholdPercent) {
            this.capsThresholdPercent = capsThresholdPercent;
        }

        public int getCapsMinLength() {
            return capsMinLength;
        }

        public void setCapsMinLength(int capsMinLength) {
            this.capsMinLength = capsMinLength;
        }

        public int getSpamMessageLimit() {
            return spamMessageLimit;
        }

        public void setSpamMessageLimit(int spamMessageLimit) {
            this.spamMessageLimit = spamMessageLimit;
        }

        public int getSpamWindowSeconds() {
            return spamWindowSeconds;
        }

        public void setSpamWindowSeconds(int spamWindowSeconds) {
            this.spamWindowSeconds = spamWindowSeconds;
        }

        public int getRepeatMessageLimit() {
            return repeatMessageLimit;
        }

        public void setRepeatMessageLimit(int repeatMessageLimit) {
            this.repeatMessageLimit = repeatMessageLimit;
        }

        public int getAutoPunishThreshold() {
            return autoPunishThreshold;
        }

        public void setAutoPunishThreshold(int autoPunishThreshold) {
            this.autoPunishThreshold = autoPunishThreshold;
        }

        public int getAutoPunishWindowMinutes() {
            return autoPunishWindowMinutes;
        }

        public void setAutoPunishWindowMinutes(int autoPunishWindowMinutes) {
            this.autoPunishWindowMinutes = autoPunishWindowMinutes;
        }

        public int getAutoPunishDurationMinutes() {
            return autoPunishDurationMinutes;
        }

        public void setAutoPunishDurationMinutes(int autoPunishDurationMinutes) {
            this.autoPunishDurationMinutes = autoPunishDurationMinutes;
        }
    }

    /**
     * docs/spec/05-DISCORD-BOT.txt. Buyer's own bot token, never a shared one - see DiscordBotService.
     * Bot is entirely optional: blank token or enabled=false both mean "don't connect".
     * staffRoleIds is a flat allowlist for MVP - the spec's full Discord-role -> panel-role tiered
     * mapping (§1 "Role picker for staff permissions") is deferred, see PLAN.md.
     */
    public static class Discord {
        private boolean enabled = false;
        private String token = "";
        private String guildId = "";
        private List<String> staffRoleIds = new ArrayList<>();
        private Feeds feeds = new Feeds();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getGuildId() {
            return guildId;
        }

        public void setGuildId(String guildId) {
            this.guildId = guildId;
        }

        public List<String> getStaffRoleIds() {
            return staffRoleIds;
        }

        public void setStaffRoleIds(List<String> staffRoleIds) {
            this.staffRoleIds = staffRoleIds;
        }

        public Feeds getFeeds() {
            return feeds;
        }

        public void setFeeds(Feeds feeds) {
            this.feeds = feeds;
        }

        /** Channel IDs, one per feed - blank means that feed is off. docs/spec §4 lists more feeds (tickets, appeals, node-status, changelog) than are wired up yet, see PLAN.md. */
        public static class Feeds {
            private String punishmentLog = "";
            private String digest = "";
            private String reports = "";

            public String getPunishmentLog() {
                return punishmentLog;
            }

            public void setPunishmentLog(String punishmentLog) {
                this.punishmentLog = punishmentLog;
            }

            public String getDigest() {
                return digest;
            }

            public void setDigest(String digest) {
                this.digest = digest;
            }

            public String getReports() {
                return reports;
            }

            public void setReports(String reports) {
                this.reports = reports;
            }
        }
    }

    /** docs/spec/04-PANEL.txt §5 DESIGN: "Server logo in the header, from config" + "single accent colour driven by a config value (buyer branding)". Both blank = the panel's own defaults (no logo image, the built-in accent). */
    public static class Branding {
        private String logoUrl = "";
        private String accentColor = "";

        public String getLogoUrl() {
            return logoUrl;
        }

        public void setLogoUrl(String logoUrl) {
            this.logoUrl = logoUrl;
        }

        public String getAccentColor() {
            return accentColor;
        }

        public void setAccentColor(String accentColor) {
            this.accentColor = accentColor;
        }
    }
}

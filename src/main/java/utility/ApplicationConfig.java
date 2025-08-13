package utility;

import com.google.protobuf.ByteString;
import com.salesforce.eventbus.protobuf.ReplayPreset;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

/**
 * The ApplicationConfig class is used for setting up the configurations for running the examples.
 * The configurations can be read from a YAML file or created directly via an object. It also sets
 * default values when an optional configuration is not specified.
 */
public class ApplicationConfig {
    private String username;
    private String password;
    private String loginUrl;
    private String tenantId;
    private String accessToken;
    private String pubsubHost;
    private Integer pubsubPort;
    private String topic;
    private Boolean plaintextChannel;
    private Boolean providedLoginUrl;
    private ReplayPreset replayPreset;
    private ByteString replayId;
    private String userId;
    private String kodyHostname;
    private String kodyApiKey;
    private String kodyStoreId;

    public ApplicationConfig() {
        this(null, null, null, null, null,
                null, null, null,
                false, false, ReplayPreset.LATEST, null, null);
    }
    
    /**
     * Create ApplicationConfig with environment-first loading
     * @param environmentName Environment name for fallback YAML file
     * @return ApplicationConfig instance
     * @throws IOException if neither environment variables nor YAML file can be loaded
     */
    public static ApplicationConfig createWithEnvironmentFirst(String environmentName) throws IOException {
        ApplicationConfig config = new ApplicationConfig();
        
        // Try to load from environment variables first
        if (config.loadFromEnvironment()) {
            System.out.println("✅ Configuration loaded from environment variables");
            return config;
        }
        
        // Fall back to YAML file if environment variables are incomplete
        System.out.println("📄 Loading configuration from YAML file: arguments-" + environmentName + ".yaml");
        return new ApplicationConfig("arguments-" + environmentName + ".yaml");
    }
    public ApplicationConfig(String filename) throws IOException {
        // First try to load from environment variables
        if (loadFromEnvironment()) {
            System.out.println("✅ Configuration loaded from environment variables");
            return;
        }
        
        // Fall back to YAML file if environment variables are not complete
        System.out.println("📄 Loading configuration from YAML file: " + filename);
        
        Yaml yaml = new Yaml();
        InputStream inputStream = null;
        
        // Try Docker/production path first, then fallback to local config directory
        String[] possiblePaths = {
            "/app/config/" + filename,      // Docker mounted volume
            "config/" + filename            // Local config directory
        };
        
        for (String path : possiblePaths) {
            try {
                inputStream = new FileInputStream(path);
                break;
            } catch (IOException e) {
                // Continue to next path
            }
        }
        
        if (inputStream == null) {
            throw new IOException("Configuration file not found: " + filename + 
                ". Searched paths: " + String.join(", ", possiblePaths));
        }
        
        HashMap<String, Object> obj = yaml.load(inputStream);

        // Reading Required Parameters
        this.loginUrl = obj.get("LOGIN_URL").toString();
        this.pubsubHost = obj.get("PUBSUB_HOST").toString();
        this.pubsubPort = Integer.parseInt(obj.get("PUBSUB_PORT").toString());

        // Reading Optional Parameters
        this.username = obj.get("USERNAME") == null ? null : obj.get("USERNAME").toString();
        this.password = obj.get("PASSWORD") == null ? null : obj.get("PASSWORD").toString();
        this.topic = obj.get("TOPIC") == null ? null : obj.get("TOPIC").toString();
        if (this.topic == null || this.topic.isEmpty()) {
            throw new IllegalArgumentException("TOPIC is required in configuration");
        }
        this.tenantId = obj.get("TENANT_ID") == null ? null : obj.get("TENANT_ID").toString();
        this.accessToken = obj.get("ACCESS_TOKEN") == null ? null : obj.get("ACCESS_TOKEN").toString();
        this.plaintextChannel = obj.get("USE_PLAINTEXT_CHANNEL") != null && Boolean.parseBoolean(obj.get("USE_PLAINTEXT_CHANNEL").toString());
        this.providedLoginUrl = obj.get("USE_PROVIDED_LOGIN_URL") != null && Boolean.parseBoolean(obj.get("USE_PROVIDED_LOGIN_URL").toString());
        this.userId = obj.get("USER_ID") == null? null : obj.get("USER_ID").toString();
        if (obj.get("REPLAY_PRESET") != null) {
            if (obj.get("REPLAY_PRESET").toString().equals("EARLIEST")) {
                this.replayPreset = ReplayPreset.EARLIEST;
            } else if (obj.get("REPLAY_PRESET").toString().equals("CUSTOM")) {
                this.replayPreset = ReplayPreset.CUSTOM;
                this.replayId = ReplayIdParser.getByteStringFromReplayIdString(obj.get("REPLAY_ID").toString());
            } else {
                this.replayPreset = ReplayPreset.LATEST;
            }
        } else {
            this.replayPreset = ReplayPreset.LATEST;
        }

        
        // Reading Kody configuration
        this.kodyHostname = obj.get("KODY_HOSTNAME") == null ? null : obj.get("KODY_HOSTNAME").toString();
        this.kodyApiKey = obj.get("KODY_API_KEY") == null ? null : obj.get("KODY_API_KEY").toString();
        this.kodyStoreId = obj.get("KODY_STORE_ID") == null ? null : obj.get("KODY_STORE_ID").toString();
    }
    
    /**
     * Load configuration from environment variables
     * @return true if all required environment variables are present, false otherwise
     */
    private boolean loadFromEnvironment() {
        String envLoginUrl = System.getenv("LOGIN_URL");
        String envPubsubHost = System.getenv("PUBSUB_HOST");
        String envPubsubPort = System.getenv("PUBSUB_PORT");
        String envTopic = System.getenv("TOPIC");
        
        // Check if required environment variables are present
        if (envLoginUrl == null || envPubsubHost == null || envPubsubPort == null || envTopic == null) {
            return false;
        }
        
        // Load required parameters
        this.loginUrl = envLoginUrl;
        this.pubsubHost = envPubsubHost;
        this.pubsubPort = Integer.parseInt(envPubsubPort);
        this.topic = envTopic;
        
        // Load authentication (either username/password or token/tenant)
        this.username = System.getenv("USERNAME");
        this.password = System.getenv("PASSWORD");
        this.tenantId = System.getenv("TENANT_ID");
        this.accessToken = System.getenv("ACCESS_TOKEN");
        
        // Validate authentication - must have either username/password or tenant/token
        boolean hasUserPass = (username != null && password != null);
        boolean hasTenantToken = (tenantId != null && accessToken != null);
        
        if (!hasUserPass && !hasTenantToken) {
            return false;
        }
        
        // Load optional parameters
        this.userId = System.getenv("USER_ID");
        this.plaintextChannel = Boolean.parseBoolean(System.getenv("USE_PLAINTEXT_CHANNEL"));
        this.providedLoginUrl = Boolean.parseBoolean(System.getenv("USE_PROVIDED_LOGIN_URL"));
        
        // Load replay configuration
        String replayPresetStr = System.getenv("REPLAY_PRESET");
        if ("EARLIEST".equals(replayPresetStr)) {
            this.replayPreset = ReplayPreset.EARLIEST;
        } else if ("CUSTOM".equals(replayPresetStr)) {
            this.replayPreset = ReplayPreset.CUSTOM;
            String replayIdStr = System.getenv("REPLAY_ID");
            if (replayIdStr != null) {
                this.replayId = ReplayIdParser.getByteStringFromReplayIdString(replayIdStr);
            }
        } else {
            this.replayPreset = ReplayPreset.LATEST;
        }
        
        // Load Kody configuration
        this.kodyHostname = System.getenv("KODY_HOSTNAME");
        this.kodyApiKey = System.getenv("KODY_API_KEY");
        this.kodyStoreId = System.getenv("KODY_STORE_ID");
        
        return true;
    }

    public ApplicationConfig(String username, String password, String loginUrl,
                                 String pubsubHost, int pubsubPort, String topic) {
        this(username, password, loginUrl, null, null, pubsubHost, pubsubPort, topic,
                false, false, ReplayPreset.LATEST, null, null);
    }

    public ApplicationConfig(String username, String password, String loginUrl, String tenantId, String accessToken,
                                 String pubsubHost, Integer pubsubPort, String topic,
                                 Boolean plaintextChannel, Boolean providedLoginUrl,
                                 ReplayPreset replayPreset, ByteString replayId, String userId) {
        this.username = username;
        this.password = password;
        this.loginUrl = loginUrl;
        this.tenantId = tenantId;
        this.accessToken = accessToken;
        this.pubsubHost = pubsubHost;
        this.pubsubPort = pubsubPort;
        this.topic = topic;
        this.plaintextChannel = plaintextChannel;
        this.providedLoginUrl = providedLoginUrl;
        this.replayPreset = replayPreset;
        this.replayId = replayId;
        this.userId = userId;
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

    public String getLoginUrl() {
        return loginUrl;
    }

    public void setLoginUrl(String loginUrl) {
        this.loginUrl = loginUrl;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getPubsubHost() {
        return pubsubHost;
    }

    public void setPubsubHost(String pubsubHost) {
        this.pubsubHost = pubsubHost;
    }

    public int getPubsubPort() {
        return pubsubPort;
    }

    public void setPubsubPort(int pubsubPort) {
        this.pubsubPort = pubsubPort;
    }


    public boolean usePlaintextChannel() {
        return plaintextChannel;
    }

    public void setPlaintextChannel(boolean plaintextChannel) {
        this.plaintextChannel = plaintextChannel;
    }

    public Boolean useProvidedLoginUrl() {
        return providedLoginUrl;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public void setProvidedLoginUrl(Boolean providedLoginUrl) {
        this.providedLoginUrl = providedLoginUrl;
    }

    public ReplayPreset getReplayPreset() {
        return replayPreset;
    }

    public void setReplayPreset(ReplayPreset replayPreset) {
        this.replayPreset = replayPreset;
    }

    public ByteString getReplayId() {
        return replayId;
    }

    public void setReplayId(ByteString replayId) {
        this.replayId = replayId;
    }


    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getKodyHostname() {
        return kodyHostname;
    }

    public void setKodyHostname(String kodyHostname) {
        this.kodyHostname = kodyHostname;
    }

    public String getKodyApiKey() {
        return kodyApiKey;
    }

    public void setKodyApiKey(String kodyApiKey) {
        this.kodyApiKey = kodyApiKey;
    }

    public String getKodyStoreId() {
        return kodyStoreId;
    }

    public void setKodyStoreId(String kodyStoreId) {
        this.kodyStoreId = kodyStoreId;
    }

    /**
     * NOTE: replayIds are meant to be opaque (See docs: https://developer.salesforce.com/docs/platform/pub-sub-api/guide/intro.html)
     * and this is used for example purposes only. A long-lived subscription client will use the stored replay to
     * resubscribe on failure. The stored replay should be in bytes and not in any other form.
     */
    public ByteString getByteStringFromReplayIdInputString(String input) {
        ByteString replayId;
        String[] values = input.substring(1, input.length()-2).split(",");
        byte[] b = new byte[values.length];
        int i=0;
        for (String x : values) {
            if (x.strip().length() != 0) {
                b[i++] = (byte)Integer.parseInt(x.strip());
            }
        }
        replayId = ByteString.copyFrom(b);
        return replayId;
    }

    /**
     * Converts ByteString type replayId back to the original input string format.
     *
     * @param replayId The ByteString type replayId to convert
     * @return The converted string, formatted like "[1, 2, 3]"
     */
    public String getReplayIdStringFromByteString(ByteString replayId) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < replayId.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(replayId.byteAt(i));
        }
        sb.append("]");
        return sb.toString();
    }
}

package ai.teraunit.core.repository;

import ai.teraunit.core.common.ProviderName;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "tera_instances", indexes = {
        @Index(name = "idx_active_heartbeat", columnList = "isActive, lastHeartbeat"),
        @Index(name = "idx_active_expiry", columnList = "isActive, expiresAt")
})
public class InstanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String instanceId; // The Provider's ID (e.g., "12345")

    // Heartbeat identity used by agents to authenticate (does NOT have to equal
    // provider instanceId)
    @Column(columnDefinition = "TEXT")
    private String heartbeatId;

    // SHA-256 hex of the heartbeat token (never store token plaintext)
    @Column(columnDefinition = "TEXT")
    private String heartbeatTokenSha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProviderName provider;

    // PROTOCOL 1: THE VAULT REFERENCE
    // We store the Encrypted Key so the Reaper can kill it later.
    @Column(nullable = false, columnDefinition = "TEXT")
    private String encryptedApiKey;

    @Column(nullable = false)
    private Instant startTime;

    // Optional: hard stop even if heartbeats continue (prevents weekend leaks)
    private Instant expiresAt;

    private Instant lastHeartbeat;

    @Column(nullable = false)
    private boolean isActive = true;

    // --- CONSTRUCTORS & GETTERS ---

    public InstanceEntity() {
    }

    public InstanceEntity(String instanceId, ProviderName provider, String encryptedApiKey) {
        this.instanceId = instanceId;
        this.provider = provider;
        this.encryptedApiKey = encryptedApiKey;
        this.startTime = Instant.now();
        this.lastHeartbeat = Instant.now();

        // Backwards-compatible default: heartbeatId == provider instanceId
        this.heartbeatId = instanceId;
    }

    public InstanceEntity(String instanceId,
            String heartbeatId,
            String heartbeatTokenSha256,
            ProviderName provider,
            String encryptedApiKey) {
        this.instanceId = instanceId;
        this.heartbeatId = heartbeatId;
        this.heartbeatTokenSha256 = heartbeatTokenSha256;
        this.provider = provider;
        this.encryptedApiKey = encryptedApiKey;
        this.startTime = Instant.now();
        this.lastHeartbeat = Instant.now();
    }

    public void heartbeat() {
        this.lastHeartbeat = Instant.now();
    }

    public void kill() {
        this.isActive = false;
    }

    // Getters...
    public String getInstanceId() {
        return instanceId;
    }

    public String getHeartbeatId() {
        return heartbeatId;
    }

    public String getHeartbeatTokenSha256() {
        return heartbeatTokenSha256;
    }

    public ProviderName getProvider() {
        return provider;
    }

    public String getEncryptedApiKey() {
        return encryptedApiKey;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    public boolean isActive() {
        return isActive;
    }
}

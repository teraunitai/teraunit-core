package ai.teraunit.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;

public interface InstanceRepository extends JpaRepository<InstanceEntity, Long> {

    // Find zombies: Active instances that haven't phoned home since [cutoff]
    @Query("SELECT i FROM InstanceEntity i WHERE i.isActive = true AND i.lastHeartbeat < :cutoff")
    List<InstanceEntity> findZombies(Instant cutoff);

    // Find expired leases: Active instances past their max runtime
    @Query("SELECT i FROM InstanceEntity i WHERE i.isActive = true AND i.expiresAt IS NOT NULL AND i.expiresAt < :now")
    List<InstanceEntity> findExpired(Instant now);

    // List active instances for control-plane UI
    @Query("SELECT i FROM InstanceEntity i WHERE i.isActive = true ORDER BY i.startTime DESC")
    List<InstanceEntity> findActive();

    InstanceEntity findByInstanceId(String instanceId);

    InstanceEntity findByHeartbeatId(String heartbeatId);
}

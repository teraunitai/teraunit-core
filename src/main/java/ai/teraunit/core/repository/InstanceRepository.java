package ai.teraunit.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;

public interface InstanceRepository extends JpaRepository<InstanceEntity, Long> {

    // Find zombies: Active instances that haven't phoned home since [cutoff]
    @Query("SELECT i FROM InstanceEntity i WHERE i.isActive = true AND i.lastHeartbeat < :cutoff")
    List<InstanceEntity> findZombies(Instant cutoff);

    InstanceEntity findByInstanceId(String instanceId);

    InstanceEntity findByHeartbeatId(String heartbeatId);
}

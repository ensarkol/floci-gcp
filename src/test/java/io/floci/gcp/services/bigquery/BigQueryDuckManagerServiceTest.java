package io.floci.gcp.services.bigquery;

import io.floci.gcp.config.EmulatorConfig;
import io.floci.gcp.core.common.GcpException;
import io.floci.gcp.core.common.docker.ContainerBuilder;
import io.floci.gcp.core.common.docker.ContainerDetector;
import io.floci.gcp.core.common.docker.ContainerLifecycleManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class BigQueryDuckManagerServiceTest {

    @Test
    void stopManagedContainersPreventsFutureContainerStarts() {
        ContainerBuilder builder = mock(ContainerBuilder.class);
        ContainerLifecycleManager lifecycleManager = mock(ContainerLifecycleManager.class);
        ContainerDetector detector = mock(ContainerDetector.class);
        EmulatorConfig config = mock(EmulatorConfig.class);

        BigQueryDuckManager manager = new BigQueryDuckManager(builder, lifecycleManager, detector, config);

        manager.stopManagedContainers();

        assertThrows(GcpException.class, manager::ensureReady);
        verifyNoInteractions(lifecycleManager);
    }
}

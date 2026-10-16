package dev.dyclaim;
import dev.dyclaim.manager.ConfigManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigSnapshotTest extends ManagerFixture {
    @Test void publishedConfigViewIsNotMutatedByLaterAdminWrites() throws Exception {
        values.save(dir.resolve("config.yml").toFile());when(plugin.getConfig()).thenReturn(values);
        ConfigManager manager=new ConfigManager(plugin);manager.setClaimPrice(700);
        var previous=manager.values();manager.setClaimPrice(900);
        assertEquals(700,previous.getDouble("economy.claim-price"));
        assertEquals(900,manager.values().getDouble("economy.claim-price"));
    }
}

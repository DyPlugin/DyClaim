package dev.dyclaim;

import dev.dyclaim.manager.UpdateChecker;
import dev.dyclaim.util.UpdateFeed;
import org.bukkit.entity.Player;
import org.bukkit.event.player.*;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.net.URL;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class UpdateCheckerTest extends ManagerFixture {
    MockedStatic<UpdateFeed> feed;
    List<Runnable> async;
    UpdateChecker checker;
    @BeforeEach void updaterSetup() throws Exception {
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getDescription()).thenReturn(new PluginDescriptionFile("DyClaim","1.0.1","dev.dyclaim.DyClaim"));
        when(plugin.getServer().getBukkitVersion()).thenReturn("26.2.build.2633-stable");
        when(plugin.getMessageManager().getMessage(any(org.bukkit.command.CommandSender.class),anyString(),anyMap())).thenAnswer(i->i.getArgument(1));
        async=new ArrayList<>();
        when(scheduler.runTaskAsynchronously(eq(plugin),any(Runnable.class))).thenAnswer(i->{async.add(i.getArgument(1));return mock(BukkitTask.class);});
        when(scheduler.runTask(eq(plugin),any(Runnable.class))).thenAnswer(i->{((Runnable)i.getArgument(1)).run();return mock(BukkitTask.class);});
        feed=mockStatic(UpdateFeed.class,CALLS_REAL_METHODS);
        feed.when(()->UpdateFeed.fetch(any(URL.class),eq("1.0.1"))).thenReturn("[]");
        checker=new UpdateChecker(plugin);
    }
    @AfterEach void updaterTeardown(){feed.close();}
    private void release(String id,String number) throws Exception {
        feed.when(()->UpdateFeed.fetch(any(URL.class),eq("1.0.1"))).thenReturn("[{\"id\":\""+id+"\",\"version_number\":\""+number+"\",\"status\":\"listed\",\"version_type\":\"release\",\"game_versions\":[\"26.2\"],\"loaders\":[\"paper\"]}]");
    }
    @Test void adminWhoJoinedBeforeHttpCompletedIsNotMissedAndIsNotSpammed() throws Exception {
        Player admin=player("Admin",chunk(0,0));when(admin.hasPermission("dyclaim.admin")).thenReturn(true);
        when(plugin.getServer().getOnlinePlayers()).thenAnswer(i->List.of(admin));
        checker.onAdminJoin(new PlayerJoinEvent(admin,""));timers.remove(0).run();verify(admin,never()).sendMessage(anyString());
        release("Public","v1.0.2");checker.check();async.remove(0).run();
        assertTrue(checker.isUpdateAvailable());assertTrue(checker.getDownloadUrl().endsWith("/Public"));
        verify(admin,times(2)).sendMessage(anyString());
        checker.check();async.remove(0).run();verify(admin,times(2)).sendMessage(anyString());
        checker.onQuit(new PlayerQuitEvent(admin,""));checker.onAdminJoin(new PlayerJoinEvent(admin,""));
        timers.get(timers.size()-1).run();verify(admin,times(4)).sendMessage(anyString());
    }
    @Test void failureRetriesSoonerAndDoesNotErasePreviouslyDetectedUpdate() throws Exception {
        release("Public","1.0.2");checker.check();async.remove(0).run();
        feed.when(()->UpdateFeed.fetch(any(URL.class),eq("1.0.1"))).thenThrow(new IOException("HTTP 429"));
        checker.check();async.remove(0).run();assertTrue(checker.isUpdateAvailable());
        verify(scheduler).runTaskLater(eq(plugin),any(Runnable.class),eq(10*1200L));
        verify(scheduler).runTaskLater(eq(plugin),any(Runnable.class),eq(6*60*1200L));
    }
    @Test void disabledOrClosedCheckerMakesNoRequests() {
        values.set("update-checker.enabled",false);checker.check();assertTrue(async.isEmpty());
        values.set("update-checker.enabled",true);checker.close();checker.check();assertTrue(async.isEmpty());
    }
    @Test void overlappingChecksAndReloadDoNotPublishStaleResults() throws Exception {
        release("Public","1.0.2");checker.check();checker.check();assertEquals(1,async.size());
        values.set("update-checker.enabled",false);checker.reload();async.remove(0).run();
        assertFalse(checker.isUpdateAvailable());assertTrue(async.isEmpty());
    }
    @Test void onlyOnlineAdminsReceiveMessagesAndNewerReleaseNotifiesAgain() throws Exception {
        Player admin=player("Admin",chunk(0,0)),visitor=player("Visitor",chunk(0,0));
        when(admin.hasPermission("dyclaim.admin")).thenReturn(true);
        when(plugin.getServer().getOnlinePlayers()).thenAnswer(i->List.of(admin,visitor));
        release("First","1.0.2");checker.check();async.remove(0).run();
        release("Second","1.0.3");checker.check();async.remove(0).run();
        verify(admin,times(4)).sendMessage(anyString());verify(visitor,never()).sendMessage(anyString());
    }
}

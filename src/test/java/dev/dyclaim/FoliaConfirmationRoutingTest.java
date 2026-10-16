package dev.dyclaim;
import dev.dyclaim.platform.PlatformTasks;
import dev.dyclaim.manager.ConfirmationManager;
import io.papermc.paper.threadedregions.scheduler.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FoliaConfirmationRoutingTest extends ManagerFixture {
    org.bukkit.entity.Player recipient;List<Runnable> queued=new ArrayList<>();
    @BeforeEach void routing(){
        recipient=player("Recipient",chunk(0,0));values.set("confirmations.clickable-buttons",false);
        var scheduler=mock(EntityScheduler.class);when(recipient.getScheduler()).thenReturn(scheduler);
        when(scheduler.runDelayed(eq(plugin),any(),any(),anyLong())).thenAnswer(i->{
            Consumer<ScheduledTask> callback=i.getArgument(1);var handle=mock(ScheduledTask.class);
            queued.add(()->callback.accept(handle));return handle;
        });
        when(plugin.getPlatformTasks()).thenReturn(PlatformTasks.folia(plugin));
    }
    @Test void foreignPlayerOfferDoesNotSendOrPublishBeforeOwnerCallback(){
        confirmations.addPending(recipient.getUniqueId(),ConfirmationManager.ActionType.TRANSFER,uuid->{},uuid->{});
        assertFalse(confirmations.hasPending(recipient.getUniqueId()));verify(recipient,never()).sendMessage(anyString());
        queued.remove(0).run();assertTrue(confirmations.hasPending(recipient.getUniqueId()));verify(recipient).sendMessage("confirm-text");
    }
    @Test void reloadInvalidatesAnOfferWaitingForRecipientOwner(){
        confirmations.addPending(recipient.getUniqueId(),ConfirmationManager.ActionType.TRANSFER,uuid->fail("Stale offer accepted"),uuid->{});
        confirmations.clear();queued.remove(0).run();assertFalse(confirmations.hasPending(recipient.getUniqueId()));
        verify(recipient,never()).sendMessage(anyString());
    }
    @Test void claimInvalidationAlsoDiscardsDeferredOffers(){
        confirmations.addPending(recipient.getUniqueId(),ConfirmationManager.ActionType.TRANSFER,"world:0:0",uuid->fail("Cancelled claim offer accepted"),uuid->{});
        confirmations.cancelClaim("world:0:0");queued.remove(0).run();assertFalse(confirmations.hasPending(recipient.getUniqueId()));
        verify(recipient,never()).sendMessage(anyString());
    }
    @Test void replacementQueuedOfferCannotBeOverwrittenByAnOlderCallback(){
        List<String> result=new ArrayList<>();
        confirmations.addPending(recipient.getUniqueId(),ConfirmationManager.ActionType.TRANSFER,uuid->result.add("old"),uuid->{});
        confirmations.addPending(recipient.getUniqueId(),ConfirmationManager.ActionType.TRANSFER,uuid->result.add("new"),uuid->{});
        queued.get(1).run();queued.get(0).run();assertTrue(confirmations.confirm(recipient.getUniqueId()));assertEquals(List.of("new"),result);
    }
    @Test void olderCallbackRunningFirstCannotConsumeItsReplacement(){
        List<String> result=new ArrayList<>();
        confirmations.addPending(recipient.getUniqueId(),ConfirmationManager.ActionType.TRANSFER,uuid->result.add("old"),uuid->{});
        confirmations.addPending(recipient.getUniqueId(),ConfirmationManager.ActionType.TRANSFER,uuid->result.add("new"),uuid->{});
        queued.get(0).run();queued.get(1).run();assertTrue(confirmations.confirm(recipient.getUniqueId()));assertEquals(List.of("new"),result);
    }
}

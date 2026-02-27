package dev.dyclaim.manager;

import dev.dyclaim.DyClaim;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ConfirmationManager {

    private final DyClaim plugin;
    private final Map<UUID, PendingAction> pendingActions = new ConcurrentHashMap<>();

    public ConfirmationManager(DyClaim plugin) {
        this.plugin = plugin;
    }

    public void addPending(UUID playerUUID, ActionType type, Consumer<UUID> onConfirm, Consumer<UUID> onDeny) {
        cancelPending(playerUUID);

        PendingAction action = new PendingAction(type, onConfirm, onDeny);
        pendingActions.put(playerUUID, action);

        BukkitTask timeoutTask = new BukkitRunnable() {
            @Override
            public void run() {
                PendingAction current = pendingActions.get(playerUUID);
                if (current != null && current == action) {
                    pendingActions.remove(playerUUID);
                    var player = plugin.getServer().getPlayer(playerUUID);
                    if (player != null && player.isOnline()) {
                        player.sendMessage(plugin.getMessageManager().getPrefixed(player, "confirm-expired"));
                    }
                }
            }
        }.runTaskLater(plugin, plugin.getConfigManager().getConfirmationTimeout() * 20L);

        action.setTimeoutTask(timeoutTask);
    }

    public boolean confirm(UUID playerUUID) {
        PendingAction action = pendingActions.remove(playerUUID);
        if (action == null) {
            return false;
        }
        action.cancelTimeout();
        action.onConfirm().accept(playerUUID);
        return true;
    }

    public boolean deny(UUID playerUUID) {
        PendingAction action = pendingActions.remove(playerUUID);
        if (action == null) {
            return false;
        }
        action.cancelTimeout();
        action.onDeny().accept(playerUUID);
        return true;
    }

    public boolean hasPending(UUID playerUUID) {
        return pendingActions.containsKey(playerUUID);
    }

    public void cancelPending(UUID playerUUID) {
        PendingAction action = pendingActions.remove(playerUUID);
        if (action != null) {
            action.cancelTimeout();
        }
    }

    public enum ActionType {
        CLAIM,
        SELL
    }

    public static class PendingAction {
        private final ActionType type;
        private final Consumer<UUID> onConfirm;
        private final Consumer<UUID> onDeny;
        private BukkitTask timeoutTask;

        public PendingAction(ActionType type, Consumer<UUID> onConfirm, Consumer<UUID> onDeny) {
            this.type = type;
            this.onConfirm = onConfirm;
            this.onDeny = onDeny;
        }

        public ActionType type() {
            return type;
        }

        public Consumer<UUID> onConfirm() {
            return onConfirm;
        }

        public Consumer<UUID> onDeny() {
            return onDeny;
        }

        public void setTimeoutTask(BukkitTask task) {
            this.timeoutTask = task;
        }

        public void cancelTimeout() {
            if (timeoutTask != null && !timeoutTask.isCancelled()) {
                timeoutTask.cancel();
            }
        }
    }
}

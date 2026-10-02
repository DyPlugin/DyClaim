package dev.dyclaim.command;
import dev.dyclaim.DyClaim;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
public class UnclaimCommand implements CommandExecutor {
    private final DyClaim plugin;
    public UnclaimCommand(DyClaim plugin){this.plugin=plugin;}
    public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(sender instanceof Player player)SellHelper.request(plugin,player,true);
        else sender.sendMessage(plugin.getMessageManager().getPrefixed(sender,"player-only"));
        return true;
    }
}

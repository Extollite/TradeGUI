package pl.extollite.tradegui;

import cn.nukkit.Player;
import cn.nukkit.block.BlockID;
import cn.nukkit.block.BlockWool;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.event.player.PlayerTeleportEvent;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.level.Sound;
import cn.nukkit.utils.ClientChainData;
import cn.nukkit.utils.TextFormat;
import com.nukkitx.fakeinventories.inventory.ChestFakeInventory;
import com.nukkitx.fakeinventories.inventory.FakeSlotChangeEvent;

import java.util.HashMap;
import java.util.Map;

public class TradeGUICommand extends CommandManager {

    private TradeGUI plugin;

    public TradeGUICommand(TradeGUI plugin) {
        super(plugin, "tradegui", "trade command", "/tradegui <player_name> <name>");
        this.plugin = plugin;
        Map<String, CommandParameter[]> parameters = new HashMap<>();
        parameters.put("set", new CommandParameter[]{
                new CommandParameter("Player Name", CommandParamType.TARGET, false),
                new CommandParameter("Trader Name", false, plugin.getTraders().keySet().toArray(new String[0])),
        });
        this.setCommandParameters(parameters);
        this.setPermission("tradegui.command");
    }

    public boolean execute(CommandSender sender, String label, String[] args) {
        if (sender instanceof Player) {
            if (!sender.isOp() && !sender.hasPermission("tradegui.command")) {
                return true;
            }
        }
        if (args.length >= 2) {
            Trader trader = plugin.getTraders().get(args[1]);
            if (trader != null) {
                Player p = this.plugin.getServer().getPlayerExact(args[0]);
                if (p != null) {
                    plugin.openTrade(p, trader);
                } else {
                    sender.sendMessage("Player not found!");
                }
            }
            return true;
        }
        sender.sendMessage(plugin.getPrefix() + TextFormat.GREEN + "Usage: ");
        sender.sendMessage(TextFormat.GREEN + "/tradegui <player_name> <name>");
        return true;
    }

}


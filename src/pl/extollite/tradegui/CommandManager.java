package pl.extollite.tradegui;

import cn.nukkit.command.Command;
import cn.nukkit.command.PluginIdentifiableCommand;

public abstract class CommandManager extends Command implements PluginIdentifiableCommand {
    private TradeGUI plugin;

    public CommandManager(TradeGUI plugin, String name, String desc, String usage) {
        super(name, desc, usage);

        this.plugin = plugin;
    }

    public TradeGUI getPlugin() {
        return plugin;
    }
}

package pl.extollite.tradegui;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.passive.EntityVillager;
import cn.nukkit.entity.passive.EntityVillagerV1;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDeathEvent;
import cn.nukkit.event.entity.EntitySpawnEvent;
import cn.nukkit.event.inventory.InventoryCloseEvent;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Sound;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.plugin.service.RegisteredServiceProvider;
import cn.nukkit.utils.*;
import com.nukkitx.fakeinventories.inventory.ChestFakeInventory;
import com.nukkitx.fakeinventories.inventory.FakeInventories;
import com.nukkitx.fakeinventories.inventory.FakeSlotChangeEvent;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

public class TradeGUI extends PluginBase implements Listener {
    private static final String format = "yyyy-MM-dd HH:mm:ss Z";

    private static TradeGUI instance;

    static TradeGUI getInstance() {
        return instance;
    }

    private String prefix;
    private String fromTitle;
    private String toTitle;
    private String noItem;
    private String accept;
    private String decline;
    private String success;
    private int dieAfter;
    private Map<String, Trader> traders = new HashMap<>();
    private Map<Entity, Trader> villagers = new HashMap<>();
    private FakeInventories fakeInventories;

    @Override
    public void onEnable() {
        RegisteredServiceProvider<FakeInventories> provider = getServer().getServiceManager().getProvider(FakeInventories.class);

        if (provider == null || provider.getProvider() == null) {
            this.getLogger().error(TextFormat.RED + "FakeInventories not provided! Turning off!");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }
        fakeInventories = provider.getProvider();
        this.saveDefaultConfig();
        instance = this;
        List<String> authors = this.getDescription().getAuthors();
        this.getLogger().info(TextFormat.DARK_GREEN + "Plugin by " + authors.get(0));
        parseConfig();
        this.getServer().getPluginManager().registerEvents(this, this);
        this.getServer().getCommandMap().register("tradegui", new TradeGUICommand(this));
    }

    private void parseConfig() {
        Config configFile = getConfig();
        prefix = configFile.getString("prefix");
        fromTitle = configFile.getString("from-title");
        toTitle = configFile.getString("to-title");
        noItem = configFile.getString("noItem");
        accept = configFile.getString("accept");
        decline = configFile.getString("decline");
        success = configFile.getString("success");
        dieAfter = configFile.getInt("dieAfter");
        Config cfg = new Config(getDataFolder() + "/traders.yml", Config.YAML);
        Set<String> keys = cfg.getKeys(false);
        if (keys.isEmpty()) {
            cfg.load(getResource("traders.yml"));
            cfg.save();
            keys = cfg.getKeys(false);
        }
        for (String key : keys) {
            ConfigSection section = cfg.getSection(key);
            String name = section.getString("name");
            Item[] items = new Item[2];
            loadItems(items, section.getSection("items"));
            Trader trader = new Trader(name, items);
            this.traders.put(key, trader);
        }
    }

    public String getPrefix() {
        return prefix;
    }

    void loadItems(Item[] items, ConfigSection rewards) {
        Map<String, Object> reward = rewards.getAllMap();
        for (Map.Entry<String, Object> serialize : reward.entrySet()) {
            if (serialize.getValue() instanceof Map) {
                Map<String, Object> toSerialize = ((Map) serialize.getValue());
                Item rewardItem = new Item((int) toSerialize.get("id"), (int) toSerialize.get("meta"), (int) toSerialize.get("count"));
                if (!toSerialize.get("customName").equals("Default")) {
                    rewardItem.setCustomName(toSerialize.get("customName").toString());
                }
                if (toSerialize.containsKey("enchantments")) {
                    List<String> enchants = (List<String>) toSerialize.get("enchantments");
                    for (String enchant : enchants) {
                        String[] sep = enchant.split(":");
                        rewardItem.addEnchantment(Enchantment.getEnchantment(Integer.parseInt(sep[0])).setLevel(Integer.parseInt(sep[1])));
                    }
                }
                if (toSerialize.containsKey("lore")) {
                    List<String> lore = (List<String>) toSerialize.get("lore");
                    rewardItem.setLore(lore.toArray(new String[0]));
                }
                if (serialize.getKey().equals("from")) {
                    items[0] = rewardItem;
                } else {
                    items[1] = rewardItem;
                }
            }
        }
    }

    @EventHandler
    public void onSpawn(EntitySpawnEvent event){
        if(event.getEntity() != null && (event.getEntity().getNetworkId() == EntityVillager.NETWORK_ID || event.getEntity().getNetworkId() == EntityVillagerV1.NETWORK_ID)){
            List<Trader> tradersList = Arrays.asList(traders.values().toArray(new Trader[0]));
            Collections.shuffle(tradersList);
            villagers.put(event.getEntity(), tradersList.get(0));
            this.getServer().getScheduler().scheduleDelayedTask(this, () -> {
                if(event.getEntity().isAlive()){
                    event.getEntity().kill();
                }
                villagers.remove(event.getEntity());
            }, dieAfter*20);
        }
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event){
        if(event.isCancelled())
            return;
        if(event.getEntity() != null && (event.getEntity().getNetworkId() == EntityVillager.NETWORK_ID || event.getEntity().getNetworkId() == EntityVillagerV1.NETWORK_ID)){
            if(event.getDamager() instanceof Player && !((Player)event.getDamager()).getInventory().getItemInHand().isSword() ){
                if(currentTrader.containsKey((Player)event.getDamager())){
                    event.setCancelled();
                }
                else if(villagers.containsKey(event.getEntity())) {
                    event.setCancelled();
                    openTrade(((Player) event.getDamager()), villagers.get(event.getEntity()));
                }
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event){
        if(event.getEntity() != null && (event.getEntity().getNetworkId() == EntityVillager.NETWORK_ID || event.getEntity().getNetworkId() == EntityVillagerV1.NETWORK_ID)){
            if(villagers.containsKey(event.getEntity())) {
                villagers.remove(event.getEntity());
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event){
        Trader trader = currentTrader.getOrDefault(event.getPlayer(), null);
        if(trader != null && event.getInventory() instanceof ChestFakeInventory && event.getInventory().getTitle().equals(trader.getName())){
            currentTrader.remove(event.getPlayer());
        }
    }

    public FakeInventories getFakeInventories() {
        return fakeInventories;
    }

    public Map<String, Trader> getTraders() {
        return traders;
    }

    public String getFromTitle() {
        return fromTitle;
    }

    public String getToTitle() {
        return toTitle;
    }

    public String getNoItem() {
        return noItem;
    }

    public String getAccept() {
        return accept;
    }

    public String getDecline() {
        return decline;
    }

    public String getSuccess() {
        return success;
    }

    private Map<Player, Trader> currentTrader = new HashMap<>();

    public void openTrade(Player p, Trader trader){
        if(p != null) {
            ChestFakeInventory fakeInventory = getFakeInventories().createChestInventory();
            if(p.getLoginChainData().getUIProfile() == ClientChainData.UI_PROFILE_CLASSIC){
                fakeInventory.setItem(2, new Item(ItemID.SIGN, 0, 1).setCustomName(this.getFromTitle()));
                fakeInventory.setItem(4, new Item(BlockID.END_ROD));
                fakeInventory.setItem(6, new Item(ItemID.SIGN, 0, 1).setCustomName(this.getToTitle()));
                fakeInventory.setItem(11, trader.getFrom());
                fakeInventory.setItem(13, new Item(BlockID.END_ROD));
                fakeInventory.setItem(15, trader.getTo());
                fakeInventory.setItem(20, new Item(BlockID.WOOL, 14, 1).setCustomName(this.getDecline()));
                fakeInventory.setItem(22, new Item(BlockID.END_ROD));
                fakeInventory.setItem(24, new Item(BlockID.WOOL, 5, 1).setCustomName(this.getAccept()));
                fakeInventory.addListener(this::onSlotChangePC);
            } else{
                fakeInventory.setItem(1, new Item(ItemID.SIGN, 0, 1).setCustomName(this.getFromTitle()));
                //fakeInventory.setItem(4, new Item(BlockID.END_ROD));
                fakeInventory.setItem(4, new Item(ItemID.SIGN, 0, 1).setCustomName(this.getToTitle()));
                fakeInventory.setItem(7, trader.getFrom());
                //fakeInventory.setItem(13, new Item(BlockID.END_ROD));
                fakeInventory.setItem(10, trader.getTo());
                fakeInventory.setItem(20, new Item(BlockID.WOOL, 14, 1).setCustomName(this.getDecline()));
                //fakeInventory.setItem(22, new Item(BlockID.END_ROD));
                fakeInventory.setItem(21, new Item(BlockID.WOOL, 5, 1).setCustomName(this.getAccept()));
                fakeInventory.addListener(this::onSlotChangeMobile);
            }
            fakeInventory.setTitle(trader.getName());
            fakeInventory.setName(trader.getName());
            p.addWindow(fakeInventory);
            currentTrader.put(p, trader);
        }
    }

    public void onSlotChangePC(FakeSlotChangeEvent event) {
        if (event.getInventory() instanceof ChestFakeInventory) {
            Player player = event.getPlayer();
            Trader trader = currentTrader.remove(player);
            if (trader != null) {
                SlotChangeAction action = event.getAction();
                if (action.getSlot() == 20) {
                    this.getServer().getScheduler().scheduleDelayedTask(this, () ->
                            player.removeWindow(event.getInventory()), 1);
                    event.setCancelled();
                } else if (action.getSlot() == 24) {
                    if (!player.getInventory().contains(trader.getFrom())) {
                        this.getServer().getScheduler().scheduleDelayedTask(this, () ->
                                player.removeWindow(event.getInventory()), 1);
                        player.getLevel().addSound(player, Sound.MOB_VILLAGER_NO);
                        int count = 0;
                        for(Item item : player.getInventory().all(trader.getFrom()).values()){
                            count += item.getCount();
                        }
                        try {
                            Object obj = Item.list[trader.getFrom().getId()].getConstructor().newInstance();
                            if(obj instanceof Item){
                                player.sendMessage(getNoItem().replace("%count%", String.valueOf(trader.getFrom().getCount() - count)).replace("%name%", ((Item)obj).getName()));
                            } else if(obj != null){
                                player.sendMessage(getNoItem().replace("%count%", String.valueOf(trader.getFrom().getCount() - count)).replace("%name%", ((Block)obj).getName()));
                            }
                        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException | InstantiationException e) {
                            e.printStackTrace();
                        }
                        event.setCancelled();
                        return;
                    }
                    int count = trader.getFrom().getCount();
                    for (Map.Entry<Integer, Item> entry : player.getInventory().all(trader.getFrom()).entrySet()) {
                        if (count == 0)
                            break;
                        int itemCount = entry.getValue().getCount();
                        if (itemCount > count) {
                            entry.getValue().setCount(itemCount - count);
                            player.getInventory().setItem(entry.getKey(), entry.getValue());
                        } else {
                            count -= itemCount;
                            player.getInventory().setItem(entry.getKey(), new Item(BlockID.AIR));
                        }
                    }
                    if (player.getInventory().canAddItem(trader.getTo())) {
                        player.getInventory().addItem(trader.getTo().clone());
                    } else {
                        player.getLevel().dropItem(player, trader.getTo().clone());
                    }
                    player.sendAllInventories();
                    this.getServer().getScheduler().scheduleDelayedTask(this, () ->
                            player.removeWindow(event.getInventory()), 1);
                    player.sendMessage(getSuccess());
                    player.getLevel().addSound(player, Sound.MOB_VILLAGER_YES);
                    player.getLevel().addSound(player, Sound.RANDOM_LEVELUP);
                    event.setCancelled();
                } else {
                    event.setCancelled(true);
                }
            }
        }
    }

    public void onSlotChangeMobile(FakeSlotChangeEvent event){
        if(event.getInventory() instanceof ChestFakeInventory){
            Player player = event.getPlayer();
            Trader trader = currentTrader.remove(player);
            if(trader != null){
                SlotChangeAction action = event.getAction();
                if(action.getSlot() == 20){
                    this.getServer().getScheduler().scheduleDelayedTask(this, ()->
                            player.removeWindow(event.getInventory()), 1);
                    event.setCancelled();
                } else if(action.getSlot() == 21){
                    if (!player.getInventory().contains(trader.getFrom())) {
                        this.getServer().getScheduler().scheduleDelayedTask(this, () ->
                                player.removeWindow(event.getInventory()), 1);
                        player.getLevel().addSound(player, Sound.MOB_VILLAGER_NO);
                        int count = 0;
                        for(Item item : player.getInventory().all(trader.getFrom()).values()){
                            count += item.getCount();
                        }
                        try {
                            Object obj = Item.list[trader.getFrom().getId()].getConstructor().newInstance();
                            if(obj instanceof Item){
                                player.sendMessage(getNoItem().replace("%count%", String.valueOf(trader.getFrom().getCount() - count)).replace("%name%", ((Item)obj).getName()));
                            } else if(obj != null){
                                player.sendMessage(getNoItem().replace("%count%", String.valueOf(trader.getFrom().getCount() - count)).replace("%name%", ((Block)obj).getName()));
                            }
                        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException | InstantiationException e) {
                            e.printStackTrace();
                        }
                        event.setCancelled();
                        return;
                    }
                    int count = trader.getFrom().getCount();
                    for(Map.Entry<Integer, Item> entry : player.getInventory().all(trader.getFrom()).entrySet()){
                        if(count == 0)
                            break;
                        int itemCount = entry.getValue().getCount();
                        if(itemCount > count){
                            entry.getValue().setCount(itemCount-count);
                            player.getInventory().setItem(entry.getKey(), entry.getValue());
                        } else{
                            count -= itemCount;
                            player.getInventory().setItem(entry.getKey(), new Item(BlockID.AIR));
                        }
                    }
                    if(player.getInventory().canAddItem(trader.getTo())){
                        player.getInventory().addItem(trader.getTo().clone());
                    }
                    else{
                        player.getLevel().dropItem(player, trader.getTo().clone());
                    }
                    player.sendAllInventories();
                    this.getServer().getScheduler().scheduleDelayedTask(this, ()->
                            player.removeWindow(event.getInventory()), 1);
                    player.sendMessage(getSuccess());
                    player.getLevel().addSound(player, Sound.MOB_VILLAGER_YES);
                    player.getLevel().addSound(player, Sound.RANDOM_LEVELUP);
                    event.setCancelled();
                } else {
                    event.setCancelled(true);
                }
            }
        }
    }
}

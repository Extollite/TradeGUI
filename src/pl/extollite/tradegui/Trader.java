package pl.extollite.tradegui;

import cn.nukkit.item.Item;

public class Trader {
    private Item from;
    private Item to;
    private String name;

    public Trader(String name, Item[] items) {
        this.from = items[0];
        this.to = items[1];
        this.name = name;
    }

    public Item getFrom() {
        return from;
    }

    public Item getTo() {
        return to;
    }

    public String getName() {
        return name;
    }
}

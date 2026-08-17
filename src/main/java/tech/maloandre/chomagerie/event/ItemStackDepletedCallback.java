package tech.maloandre.chomagerie.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ItemStackDepletedCallback {

    public static final Event<ItemStackDepletedCallback.Depleted> EVENT = EventFactory.createArrayBacked(
            ItemStackDepletedCallback.Depleted.class,
            (listeners) -> (player, slot, item, previousStack) -> {
                for (Depleted listener : listeners) {
                    listener.onItemStackDepleted(player, slot, item, previousStack);
                }
            }
    );

    @FunctionalInterface
    public interface Depleted {
        /**
         * Appelé quand un stack d'items se vide dans l'inventaire du joueur
         *
         * @param player        Le joueur dont l'inventaire a changé
         * @param slot          Le slot qui s'est vidé
         * @param item          L'item qui était dans le slot
         * @param previousStack Le stack précédent (avant qu'il se vide) - contient les données complètes de l'item
         */
        void onItemStackDepleted(Player player, int slot, Item item, ItemStack previousStack);
    }
}


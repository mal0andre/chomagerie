package tech.maloandre.chomagerie.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class ShulkerRefillHandler {

	/**
	 * Checks if an item is a shulker box
	 */
	public static boolean isShulkerBox(Item item) {
		return item instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock;
	}

	/**
	 * Compares two ItemStacks to check if they are the same type with the same data components
	 * This is important to differentiate items with different enchantment levels, firework levels, etc.
	 */
	private static boolean isSameItemType(ItemStack stack1, ItemStack stack2) {
		// Check for null stacks first
		if (stack1 == null || stack2 == null) {
			return false;
		}

		if (stack1.isEmpty() || stack2.isEmpty()) {
			return false;
		}

		// First check: same item type
		if (stack1.getItem() != stack2.getItem()) {
			return false;
		}

		// Compare all data components to ensure we're matching exactly the same item
		// This is crucial for items like fireworks with different flight duration levels
		return stack1.getComponentsPatch().equals(stack2.getComponentsPatch());
	}

	/**
	 * Checks if the player already has the item in their main inventory (excluding shulker boxes)
	 *
	 * @param inventory   The player's inventory
	 * @param itemToCheck The item to search for
	 * @return true if the item is present in the main inventory
	 */
	private static boolean hasItemInMainInventory(Container inventory, ItemStack itemToCheck) {
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);

			// Ignore shulker boxes
			if (isShulkerBox(stack.getItem())) {
				continue;
			}

			// Check if we found the same type of item with the same components
			if (isSameItemType(stack, itemToCheck)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Searches for and extracts an item from a shulker box in a given inventory
	 *
	 * @param player          The player (needed to drop items if inventory is full)
	 * @param sourceInventory The inventory to search (can be the ender chest)
	 * @param targetInventory The inventory where to put the extracted item (player inventory)
	 * @param targetSlot      The slot where to put the extracted item
	 * @param itemToRefill    The item to search for (ItemStack with all data components)
	 * @param filterByName    If true, filter by shulker box name
	 * @param nameFilters     The names to search for (ignored if filterByName is false)
	 * @return true if an item was found and extracted
	 */
	private static boolean tryRefillFromInventoryShulkers(Player player, Container sourceInventory, Container targetInventory,
														  int targetSlot, ItemStack itemToRefill,
														  boolean filterByName, List<String> nameFilters) {
		// Search the inventory for shulker boxes
		for (int i = 0; i < sourceInventory.getContainerSize(); i++) {
			ItemStack stack = sourceInventory.getItem(i);

			if (isShulkerBox(stack.getItem())) {
				// If name filtering is enabled, check the name of the shulker
				if (filterByName) {
					// Get the name of the shulker box
					String shulkerName = stack.getHoverName().getString();

					// If the shulker has no name or if the name doesn't match, ignore it
					if (shulkerName == null || nameFilters == null || !nameFilters.contains(shulkerName)) {
						continue; // Name doesn't match
					}
				}

				// Check the contents of the shulker box
				ItemContainerContents container = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);

				// Convert the stream to a list
				List<ItemStack> contents = new ArrayList<>();
				container.allItemsCopyStream().forEach(contents::add);

				if (contents.isEmpty()) {
					continue;
				}

				// Search for the item in the shulker box
				for (int j = 0; j < contents.size(); j++) {
					ItemStack shulkerItem = contents.get(j);

					if (isSameItemType(shulkerItem, itemToRefill)) {
						// Found the item! Transfer it to the empty slot
						int amountToTake = Math.min(shulkerItem.getCount(), itemToRefill.getMaxStackSize());

						ItemStack refillStack = shulkerItem.copy();
						refillStack.setCount(amountToTake);

						// IMPORTANT: Save the current item in the slot (e.g., empty bucket) before replacing it
						ItemStack oldStack = targetInventory.getItem(targetSlot).copy();

						// First, put the new item in the target slot
						targetInventory.setItem(targetSlot, refillStack);

						// Now try to add the old item (empty bucket) back to the player's inventory
						if (!oldStack.isEmpty()) {


							// Try to insert the item in the inventory
							// Since targetSlot is now occupied by the new item, insertStack won't use it
							boolean fullyInserted = player.getInventory().add(oldStack);


							// If insertStack returned false OR if there are still items left, drop them
							if (!fullyInserted || !oldStack.isEmpty()) {

								// Drop the remaining items in the world
								if (!player.level().isClientSide() && player.level() instanceof ServerLevel serverWorld) {
									// Get player's eye position
									double eyeHeight = player.getEyeHeight();
									double x = player.getX();
									double y = player.getY() + eyeHeight;
									double z = player.getZ();

									// Get player's look direction
									Vec3 lookVec = player.getViewVector(1.0F).multiply(0.3, 0.3, 0.3);

									// Create the item entity
									ItemEntity itemEntity = new ItemEntity(
											serverWorld,
											x,
											y,
											z,
											oldStack.copy()
									);

									// Set velocity to throw the item forward
									itemEntity.setDeltaMovement(lookVec.x, 0.2, lookVec.z);

									// Set pickup delay so the item can't be immediately picked up
									itemEntity.setPickUpDelay(40);

									// Set the thrower to prevent immediate pickup
									itemEntity.setThrower(player);
								}
							}
						}

						// Remove the item from the shulker box
						shulkerItem.shrink(amountToTake);

						// Create a new container with the modified contents
						List<ItemStack> newContents = new ArrayList<>();
						for (int k = 0; k < contents.size(); k++) {
							ItemStack itemInSlot = contents.get(k);
							if (k == j) {
								if (!shulkerItem.isEmpty()) {
									newContents.add(shulkerItem);
								}
							} else {
								newContents.add(itemInSlot);
							}
						}

						// Update the shulker box with the new contents
						stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(newContents));
						sourceInventory.setChanged();
						targetInventory.setChanged();

						return true; // Refill completed
					}
				}
			}
		}
		return false; // No item found
	}

	/**
	 * Attempts to refill an empty slot from shulker boxes in inventory and ender chest
	 *
	 * @param player       The player
	 * @param emptySlot    The slot that just became empty
	 * @param itemToRefill The item to refill (ItemStack with all data components)
	 * @param filterByName If true, filter by shulker box name
	 * @param nameFilters  The names to search for (ignored if filterByName is false)
	 * @return RefillResult containing success status and item name
	 */
	public static RefillResult tryRefillFromShulker(Player player, int emptySlot, ItemStack itemToRefill,
													boolean filterByName, List<String> nameFilters) {
		// Check if itemToRefill is null
		if (itemToRefill == null) {
			return new RefillResult(false, ""); // Nothing to refill
		}

		if (player.level().isClientSide()) {
			return new RefillResult(false, ""); // Only works server-side
		}

		Container inventory = player.getInventory();

		// Check if the player already has the item in their main inventory
		if (hasItemInMainInventory(inventory, itemToRefill)) {
			return new RefillResult(false, ""); // Don't refill if the item is already present elsewhere in inventory
		}

		String itemName = itemToRefill.getHoverName().getString();

		// 1. Try first in the main inventory
		if (tryRefillFromInventoryShulkers(player, inventory, inventory, emptySlot, itemToRefill, filterByName, nameFilters)) {
			return new RefillResult(true, itemName); // Refill completed from inventory
		}

		// 2. If nothing found, search in the ender chest
		Container enderChest = player.getEnderChestInventory();
		boolean success = tryRefillFromInventoryShulkers(player, enderChest, inventory, emptySlot, itemToRefill, filterByName, nameFilters);
		return new RefillResult(success, success ? itemName : "");
	}

	public record RefillResult(boolean success, String itemName) {
	}
}


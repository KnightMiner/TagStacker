package knightminer.tagstacker;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ItemStackedOnOtherEvent;
import org.slf4j.Logger;

@Mod(TagStacker.MOD_ID)
public class TagStacker {
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "tag_stacker";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    public TagStacker(IEventBus modEventBus, ModContainer modContainer) {
        Matcher.init(modEventBus, modContainer);

        NeoForge.EVENT_BUS.addListener(TagStacker::onItemStacked);
    }

    /** Called when an item is stacked on another in the inventory to merge them into a single stack */
    private static void onItemStacked(ItemStackedOnOtherEvent event) {
        // seems mayPickup is used as a permission check more than checking if you can remove the item specifically, must be able to pickup to change the stack
        Slot slot = event.getSlot();
        Player player = event.getPlayer();
        if (slot.mayPickup(player)) {
            // event has backwards parameters.
            ItemStack inSlot = event.getStackedOnItem();
            ItemStack held = event.getCarriedItem();
            // check if these two items can stack. Only true if they are not the same item already
            if (Matcher.canTagStack(inSlot, held)) {
                ClickAction action = event.getClickAction();
                SlotAccess heldAccess = event.getCarriedSlotAccess();

                // if we can modify the slot, place in the slot
                if (slot.mayPlace(inSlot)) {
                    int count = action == ClickAction.PRIMARY ? held.getCount() : 1;
                    // vanilla does the following, but that does some redundant checks so we simplify
                    // heldAccess.set(slot.safeInsert(held, count));
                    // simplified logic below
                    int change = Math.min(count, slot.getMaxStackSize(inSlot) - inSlot.getCount());
                    if (change > 0) {
                        held.shrink(change);
                        inSlot.grow(change);
                        slot.setByPlayer(inSlot);
                        heldAccess.set(held);
                        event.setCanceled(true);
                    }
                } else {
                    // cannot place in the slot, so try grabbing from the slot
                    slot.tryRemove(inSlot.getCount(), held.getMaxStackSize() - held.getCount(), player).ifPresent(stack -> {
                        held.grow(stack.getCount());
                        slot.onTake(player, stack);
                        event.setCanceled(true);
                    });
                }
            }
        }
    }
}

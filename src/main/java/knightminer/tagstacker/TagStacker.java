package knightminer.tagstacker;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ItemStackedOnOtherEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(TagStacker.MOD_ID)
public class TagStacker {
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "tag_stacker";

    /** Makes an identifier under our mod ID */
    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    public TagStacker() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        Matcher.init(modEventBus);

        MinecraftForge.EVENT_BUS.addListener(TagStacker::onItemStacked);
    }

    /** Called when an item is stacked on another in the inventory to merge them into a single stack */
    private static void onItemStacked(ItemStackedOnOtherEvent event) {
        // seems mayPickup is used as a permission check more than checking if you can remove the item specifically, must be able to pickup to change the stack
        Slot slot = event.getSlot();
        Player player = event.getPlayer();
        if (slot.mayPickup(player)) {
            // event has backwards parameters. TODO: swap them back for 1.21.1
            ItemStack held = event.getStackedOnItem();
            ItemStack inSlot = event.getCarriedItem();
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
                    boolean convertRemainder = Matcher.CONVERT_REMAINDER.get();
                    if (convertRemainder || change > 0) {
                        held.shrink(change);
                        inSlot.grow(change);
                        slot.setByPlayer(inSlot);
                        // swap item type on the remainder if enabled
                        ItemStack remainder = held;
                        if (convertRemainder && !remainder.isEmpty()) {
                            remainder = inSlot.copyWithCount(remainder.getCount());
                        }
                        heldAccess.set(remainder);
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

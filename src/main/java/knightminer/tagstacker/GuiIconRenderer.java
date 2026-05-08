package knightminer.tagstacker;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.IItemDecorator;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import static knightminer.tagstacker.Matcher.NO_STACKING;

/** Renders icons on items in the GUI when they support stacking */
@EventBusSubscriber(value = Dist.CLIENT)
public class GuiIconRenderer {
  private static final int COLOR = 0xFF55ff55;
  private static boolean isOpen = false;
  private static ItemStack lastStack = ItemStack.EMPTY;
  private static boolean wasEmpty = false;
  private static TagKey<Item> lastTag = NO_STACKING;

  /** Item decorator instance */
  private static final IItemDecorator DECORATOR = (graphics, font, stack, xOffset, yOffset) -> {
    if (isOpen) {
      Item item = stack.getItem();
      if (lastTag != NO_STACKING && !lastStack.is(item) && Matcher.getStackingTag(item) == lastTag && stack.getCount() < stack.getMaxStackSize() && Matcher.sameComponentPatch(stack, lastStack)) {
        graphics.text(font, "↓", xOffset + 5, yOffset, COLOR);
      }
    }
    return false;
  };

  @SubscribeEvent
  static void onOpen(ScreenEvent.Opening event) {
    if (Matcher.SHOW_ICON.getAsBoolean() && event.getScreen() instanceof AbstractContainerScreen) {
      isOpen = true;
    }
  }

  /** Handles setting the current held item when it changes */
  @SubscribeEvent
  static void onRender(ScreenEvent.Render.Pre event) {
    if (isOpen && event.getScreen() instanceof AbstractContainerScreen<?> container) {
      ItemStack held = container.getMenu().getCarried();
      if (held != lastStack || held.isEmpty() != wasEmpty) {
        lastStack = held;
        wasEmpty = held.isEmpty();
        if (wasEmpty) {
          lastTag = NO_STACKING;
        } else {
          lastTag = Matcher.getStackingTag(held.getItem());
        }
      }
    }
  }

  /** Clears the renderer state on screen close */
  @SubscribeEvent
  static void onClose(ScreenEvent.Closing event) {
    lastStack = ItemStack.EMPTY;
    wasEmpty = true;
    lastTag = NO_STACKING;
    if (Matcher.SHOW_ICON.getAsBoolean() && event.getScreen() instanceof AbstractContainerScreen) {
      isOpen = false;
    }
  }

  @SubscribeEvent
  static void registerRenderer(RegisterItemDecorationsEvent event) {
    for (Item item : BuiltInRegistries.ITEM) {
      event.register(item, DECORATOR);
    }
  }
}

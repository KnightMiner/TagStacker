package knightminer.tagstacker.jei;

import knightminer.tagstacker.TagStacker;
import knightminer.tagstacker.jei.TagStackingCategory.ItemTag;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawablesView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.placement.VerticalAlignment;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.gui.widgets.IScrollGridWidget;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayList;
import java.util.List;

/** Renders stacks that can be merged, based off JEI's TagInfoRecipeCategory */
public class TagStackingCategory extends AbstractRecipeCategory<ItemTag> {
  public static final RecipeType<ItemTag> RECIPE_TYPE = RecipeType.create(TagStacker.MOD_ID, "tag_stacking", ItemTag.class);
  private static final Component TITLE = Component.translatable("tag_stacker.jei.title");
  private static final Component TOOLTIP = Component.translatable("tag_stacker.jei.tooltip");
  private static final int WIDTH = 142;
  private static final int HEIGHT = 56;

  public TagStackingCategory(IGuiHelper guiHelper) {
    super(RECIPE_TYPE, TITLE, guiHelper.createDrawableItemLike(Items.COPPER_INGOT), WIDTH, HEIGHT);
  }

  @Override
  public ResourceLocation getRegistryName(ItemTag recipe) {
    return recipe.tag.location();
  }

  @Override
  public void setRecipe(IRecipeLayoutBuilder builder, ItemTag tag, IFocusGroup focuses) {
    builder.addInputSlot()
      .addItemStacks(tag.values)
      .setStandardSlotBackground();

    for (ItemStack stack : tag.values) {
      builder.addOutputSlot().addItemStack(stack);
    }
  }

  private static Component getName(TagKey<Item> tag) {
    String tagTranslationKey = Tags.getTagTranslationKey(tag);
    return Component.translatableWithFallback(tagTranslationKey, "#" + tag.location());
  }

  @Override
  public void createRecipeExtras(IRecipeExtrasBuilder builder, ItemTag recipe, IFocusGroup focuses) {
    builder.addText(getName(recipe.tag), getWidth() - 22, 20)
      .setPosition(22, 0)
      .setColor(0xFF505050)
      .setLineSpacing(0)
      .setTextAlignment(VerticalAlignment.CENTER)
      .setTextAlignment(HorizontalAlignment.CENTER);

    IRecipeSlotDrawablesView recipeSlots = builder.getRecipeSlots();
    List<IRecipeSlotDrawable> outputSlots = recipeSlots.getSlots(RecipeIngredientRole.OUTPUT);

    IScrollGridWidget scrollGridWidget = builder.addScrollGridWidget(outputSlots, 7, 2);
    scrollGridWidget.setPosition(0, 0, getWidth(), getHeight(), HorizontalAlignment.CENTER, VerticalAlignment.BOTTOM);

    IRecipeSlotDrawable inputSlot = recipeSlots.getSlots(RecipeIngredientRole.INPUT).getFirst();
    inputSlot.setPosition(scrollGridWidget.getScreenRectangle().position().x() + 1, 1);
  }

  @Override
  public void getTooltip(ITooltipBuilder tooltip, ItemTag recipe, IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY) {
    if (mouseX > 18 && mouseY < 20) {
      tooltip.add(TOOLTIP);
    }
  }

  /** Represents a tag in JEI */
  public record ItemTag(TagKey<Item> tag, List<ItemStack> values) {
    /** Creates a new instance from the given tag key */
    public static ItemTag create(TagKey<Item> tag) {
      List<ItemStack> values = new ArrayList<>();
      for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
        values.add(new ItemStack(holder));
      }
      return new ItemTag(tag, List.copyOf(values));
    }
  }
}

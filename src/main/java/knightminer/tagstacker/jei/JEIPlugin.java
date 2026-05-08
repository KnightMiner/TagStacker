package knightminer.tagstacker.jei;

import knightminer.tagstacker.Matcher;
import knightminer.tagstacker.TagStacker;
import knightminer.tagstacker.jei.TagStackingCategory.ItemTag;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.Identifier;

/** Plugin to show tag unification in JEI */
@JeiPlugin
public class JEIPlugin implements IModPlugin {
  private static final Identifier ID = TagStacker.id("tag_stacking");

  @Override
  public Identifier getPluginUid() {
    return ID;
  }

  @Override
  public void registerCategories(IRecipeCategoryRegistration registration) {
    if (Matcher.ENABLE_JEI.getAsBoolean()) {
      registration.addRecipeCategories(new TagStackingCategory(registration.getJeiHelpers().getGuiHelper()));
    }
  }

  @Override
  public void registerRecipes(IRecipeRegistration registration) {
    if (Matcher.ENABLE_JEI.getAsBoolean()) {
      registration.addRecipes(TagStackingCategory.RECIPE_TYPE, Matcher.getAllTags().stream().map(ItemTag::create).toList());
    }
  }
}

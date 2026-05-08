package knightminer.tagstacker;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Handles checking if two items match */
public class Matcher {
    private static final String COMMON = "c:";
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();

    /** Function to validate a string is a resource location */
    private static final Predicate<Object> VALID_RESOURCE_LOCATION = s -> ResourceLocation.tryParse(s.toString()) != null;
    /** Supplier for new elements, as Neo needs it for some reason... */
    private static final Supplier<String> NEW_ELEMENT_SUPPLIER = () -> "";

    private static final ModConfigSpec.ConfigValue<List<? extends String>> TAGS = BUILDER
      .comment("List of tags to stack. Any tag in this list will stack with other entries in a tag. Note each item may only belong to 1 tag, may be unexpected results if it belongs to multiple.",
        "Format is 'domain:name', for example 'c:ingots/'")
      .defineListAllowEmpty("items.tags", List.of(
        COMMON + "bottles/splash",
        COMMON + "bottles/lingering"
      ), NEW_ELEMENT_SUPPLIER, VALID_RESOURCE_LOCATION);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> PREFIXES = BUILDER
      .comment("List of tag prefixes to stack. Any tag starting with the prefix will stack with other entries in the tag.",
        "Format is 'domain:name', for example 'c:ingots/'")
      .defineListAllowEmpty("items.prefixes", List.of(
        COMMON + "ingots/",
        COMMON + "nuggets/",
        COMMON + "storage_blocks/",
        COMMON + "raw_materials/",
        COMMON + "dusts/",
        COMMON + "gems/"
      ), NEW_ELEMENT_SUPPLIER, VALID_RESOURCE_LOCATION);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLIST = BUILDER
      .comment("List of tags to ignore despite them matching one of the prefixes above.",
        "Format is 'domain:name', for example 'c:ingots/special' if `c:ingots/` is a prefix")
      .defineListAllowEmpty("items.prefix_blacklist", List.of(
        COMMON + "nuggets/brass_like" // from FTB materials
      ), NEW_ELEMENT_SUPPLIER, VALID_RESOURCE_LOCATION);

    public static final ModConfigSpec.BooleanValue CONVERT_REMAINDER = BUILDER
      .comment("If true, when there is a remainder after transferring the stack, that remainder is converted to the target item.")
      .define("items.convert_remainder", true);

    public static final ModConfigSpec.BooleanValue SHOW_ICON = CLIENT_BUILDER
      .comment("If true, shows an icon on items which support tag stacking")
      .define("show_icon", true);
    public static final ModConfigSpec.BooleanValue ENABLE_JEI = CLIENT_BUILDER
      .comment("If true, add a JEI category to show match equivelencies")
      .worldRestart()
      .define("enable_jei", true);

    private static final ModConfigSpec SPEC = BUILDER.build();
    private static final ModConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();


    /* Reloading */

    /** Set of tags selected by prefix */
    private static Set<TagKey<Item>> ALL_TAGS = Set.of();

    /** Gets all active tags to show in JEI */
    public static Set<TagKey<Item>> getAllTags() {
        return ALL_TAGS;
    }

    /** Parses a list of strings into a set of item tags */
    private static Set<TagKey<Item>> parseTags(List<? extends String> list) {
        return list.stream().map(ResourceLocation::tryParse).filter(Objects::nonNull)
          .map(ItemTags::create)
          .collect(Collectors.toSet());
    }

    /** Called on config reload or tag reload to clear cache and update tag sets */
    private static void updateTags(Registry<Item> registry) {
        Set<TagKey<Item>> finalTags = new HashSet<>();
        Set<TagKey<Item>> setTags = parseTags(TAGS.get());
        Set<TagKey<Item>> blacklist = parseTags(BLACKLIST.get());

        // store into map of namespace -> list[path] for efficient lookup
        Multimap<String, String> prefixes = PREFIXES.get().stream()
          .map(ResourceLocation::tryParse).filter(Objects::nonNull)
          .collect(Multimaps.toMultimap(ResourceLocation::getNamespace, ResourceLocation::getPath, MultimapBuilder.treeKeys().arrayListValues()::build));

        // iterate all tag keys
        registry.getTags().forEach(tag -> {
            // don't care if the tag has only 1 element
            if (tag.getSecond().size() > 1) {
                TagKey<Item> key = tag.getFirst();
                // only add tags that actually exist into the final set, save some effort
                if (setTags.contains(key)) {
                    finalTags.add(key);
                } else if (!blacklist.contains(key)) {
                    ResourceLocation name = key.location();
                    String path = name.getPath();
                    for (String prefix : prefixes.get(name.getNamespace())) {
                        if (path.startsWith(prefix)) {
                            finalTags.add(key);
                            break;
                        }
                    }
                }
            }
        });
        // convert to immutable set
        ALL_TAGS = Set.copyOf(finalTags);
        STACKING_TAG.clear();
    }

    private static void onConfigLoad(final ModConfigEvent event) {
        // if the config changes, only need to update assuming tags are loaded
        if (event.getConfig().getSpec() == SPEC && SPEC.isLoaded() && BuiltInRegistries.ITEM.getTagNames().findAny().isPresent()) {
            updateTags(BuiltInRegistries.ITEM);
        }
    }

    private static void onTagsUpdated(TagsUpdatedEvent event) {
        if (SPEC.isLoaded()) {
            updateTags(event.getRegistryAccess().registryOrThrow(Registries.ITEM));
        }
    }

    /** Called by {@link TagStacker} to register event listeners */
    static void init(IEventBus modBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, Matcher.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, Matcher.CLIENT_SPEC);

        modBus.addListener(Matcher::onConfigLoad);
        NeoForge.EVENT_BUS.addListener(Matcher::onTagsUpdated);
    }


    /* Getters */

    /** Cache of the tag for each item, for quick comparisons */
    private static final Map<Item, TagKey<Item>> STACKING_TAG = new HashMap<>();

    /** Some tag that should never stack used as the default for the cache. I can't imagine anyone wanting to merge all trimmable armor. */
    public static final TagKey<Item> NO_STACKING = ItemTags.TRIMMABLE_ARMOR;

    /** Function to get the stacking tag for a given item */
    private static final Function<Item,TagKey<Item>> GET_STACKING_TAG = item -> {
        return item.builtInRegistryHolder().tags().filter(ALL_TAGS::contains).findAny().orElse(NO_STACKING);
    };

    /** Gets the stacking tag for an item from the cache, or computes it if absent */
    public static TagKey<Item> getStackingTag(Item item) {
        return STACKING_TAG.computeIfAbsent(item, GET_STACKING_TAG);
    }

    /** Checks if the non-prototype components on two stacks match */
    public static boolean sameComponentPatch(ItemStack first, ItemStack second) {
        return first.components.patch.equals(second.components.patch);
    }

    /** Checks if the items can stack when they would not normally */
    public static boolean canTagStack(ItemStack first, ItemStack second) {
        if (!first.isEmpty() && !second.isEmpty()) {
            Item firstItem = first.getItem();
            Item secondItem = second.getItem();
            if (firstItem != secondItem) {
                TagKey<Item> firstTag = getStackingTag(firstItem);
                TagKey<Item> secondTag = getStackingTag(secondItem);
                // NO_STACKING is a special value that indicates it has no stacking tag
                // skipping the components getter as we want to compare patches, field gives us direct access with a few ATs
                return firstTag == secondTag && firstTag != NO_STACKING && sameComponentPatch(first, second);
            }
        }
        return false;
    }
}

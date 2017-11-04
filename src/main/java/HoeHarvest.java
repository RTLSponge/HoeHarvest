import com.flowpowered.math.vector.Vector3d;
import org.slf4j.Logger;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.manipulator.immutable.block.ImmutableGrowthData;
import org.spongepowered.api.data.manipulator.mutable.RepresentedItemData;
import org.spongepowered.api.data.value.mutable.MutableBoundedValue;
import org.spongepowered.api.data.value.mutable.Value;
import org.spongepowered.api.entity.EntityTypes;
import org.spongepowered.api.entity.Item;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.NamedCause;
import org.spongepowered.api.event.filter.IsCancelled;
import org.spongepowered.api.event.filter.cause.Named;
import org.spongepowered.api.event.item.inventory.DropItemEvent;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.util.Tristate;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;

@SuppressWarnings({"WeakerAccess", "ClassWithoutConstructor", "PublicConstructor"})
@Plugin(id= HoeHarvest.HOE_HARVEST, name = HoeHarvest.HOE_HARVEST, version = "0.1.0")
public class HoeHarvest {
    static final String HOE_HARVEST = "HoeHarvest";

    static final Set<ItemType> HOE_SET = new HashSet<>(5);
    static {
        HOE_SET.add(ItemTypes.DIAMOND_HOE);
        HOE_SET.add(ItemTypes.GOLDEN_HOE);
        HOE_SET.add(ItemTypes.IRON_HOE);
        HOE_SET.add(ItemTypes.STONE_HOE);
        HOE_SET.add(ItemTypes.WOODEN_HOE);
    }

    @Inject
    private Logger logger;

    private static boolean isHoe(final ItemStack itemStack){
        final ItemType item = itemStack.getItem();
        return HOE_SET.contains(item);
    }

    static final Set<BlockState> GROWN_CROP_STATES = new HashSet<>(3);
    static {
        addMaxGrowth(BlockTypes.CARROTS,  GROWN_CROP_STATES);
        addMaxGrowth(BlockTypes.WHEAT,    GROWN_CROP_STATES);
        addMaxGrowth(BlockTypes.POTATOES, GROWN_CROP_STATES);
    }

    private static void addMaxGrowth(final BlockType type, final Collection<BlockState> out){
        final BlockState defaultState = type.getDefaultState();
        final Optional<MutableBoundedValue<Integer>> optValue = defaultState.getValue(Keys.GROWTH_STAGE);
        final MutableBoundedValue<Integer> value = optValue.get(); //Programmer error to call addMaxGrowth on non grow crops.
        final Integer maxValue = value.getMaxValue();
        final Optional<BlockState> optState = defaultState.with(Keys.GROWTH_STAGE, maxValue);
        final BlockState state = optState.get(); //Programmer error to call addMaxGrowth on non grow crops.
        out.add(state);
    }

    private static BlockSnapshot transformToYoungGrowth(final BlockSnapshot original) {
        final Optional<ImmutableGrowthData> optGrowthData = original.getOrCreate(ImmutableGrowthData.class);
        final ImmutableGrowthData growthData = optGrowthData.get();
        final Integer min = growthData.growthStage().getMinValue();
        final Optional<BlockSnapshot> optYoungSnapshot = original.with(Keys.GROWTH_STAGE, min);
        return optYoungSnapshot.get(); //Programmer error to call transformToYoungGrowth on non crop.
    }

    private static boolean isFullyGrownCrop(final Transaction<BlockSnapshot> t){
        final BlockSnapshot original = t.getOriginal();
        return isFullyGrownCrop(original);
    }
    private static boolean isFullyGrownCrop(BlockSnapshot t){
        final BlockState state = t.getState();
        return GROWN_CROP_STATES.contains(state);
    }

    @Listener
    public final void onItemDrop(final DropItemEvent.Destruct drops){
        logger.warn(drops.getCause().toString());
    }

    @SuppressWarnings("MethodMayBeStatic")
    @Listener(order = Order.LAST)
    @IsCancelled(Tristate.UNDEFINED)
    public final void onBreakCrops(final ChangeBlockEvent.Break event, @Named(NamedCause.SOURCE) final Player player){
        player.getItemInHand().ifPresent(
            item->{
                if(isHoe(item)){
                    boolean modified = false;
                    for(final Transaction<BlockSnapshot> t:event.getTransactions()){
                        if(isFullyGrownCrop(t)){
                            t.setCustom(transformToYoungGrowth(t.getOriginal()));
                            modified = true;
                        } else {
                            if(event.isCancelled()){
                                t.setValid(false);
                            }
                        }
                    }
                    if(modified) {
                        event.setCancelled(false);
                    }
                }
            }
        );
    }

    static Map<BlockType,ItemType> itemFromCrop = new HashMap<>(5);static{
        itemFromCrop.put(BlockTypes.WHEAT, ItemTypes.WHEAT);
        itemFromCrop.put(BlockTypes.CARROTS, ItemTypes.CARROT);
        itemFromCrop.put(BlockTypes.POTATOES, ItemTypes.POTATO);
    }
    private static ItemType itemFromCrop(BlockType t){
        return itemFromCrop.get(t);
    }

    @Listener(order=Order.LATE) @IsCancelled(Tristate.UNDEFINED)
    public final void onRightClickCrops(final InteractBlockEvent.Secondary event, @Named(NamedCause.SOURCE) final Player player){
        player.getItemInHand().ifPresent(
                interactItem->{
                    if(isHoe(interactItem)){
                        final ItemStack hoe = interactItem;
                        final BlockSnapshot targetBlock = event.getTargetBlock();
                        final Location<World> blockLocation = targetBlock.getLocation().get();
                        final Vector3d centerPos = blockLocation.getPosition().add(.5,.5,.5);
                        final World world = blockLocation.getExtent();

                        final Optional<MutableBoundedValue<Integer>> optDurability = hoe.getValue(Keys.ITEM_DURABILITY);
                        if(!optDurability.isPresent()) return;

                        final MutableBoundedValue<Integer> durability = optDurability.get();
                        final Integer minValue = durability.getMinValue();
                        if(durability.get().equals(minValue)) return;

                        if(isFullyGrownCrop(targetBlock)){
                            transformToYoungGrowth(targetBlock).restore(true, false);
                            final Item itemEnt = (Item) world.createEntity(EntityTypes.ITEM, centerPos).get();
                            final ItemType foodType = itemFromCrop(targetBlock.getState().getType());
                            final ItemStack food = ItemStack.builder().itemType(foodType).build();
                            RepresentedItemData representedItemData = itemEnt.getItemData();
                            final Value<ItemStackSnapshot> valueFood = representedItemData.item().set(food.createSnapshot());
                            representedItemData = representedItemData.set(valueFood);
                            itemEnt.offer(representedItemData);
                            final boolean spawned = world.spawnEntity(itemEnt, Cause.of(NamedCause.simulated(player)));
                            if(spawned){
                                final Value<Integer> newDurability = durability.set(Math.max(minValue, durability.get() - 13));
                                hoe.offer(newDurability);
                                player.setItemInHand(hoe);
                            }
                        }
                    }
                }
        );
    }
}

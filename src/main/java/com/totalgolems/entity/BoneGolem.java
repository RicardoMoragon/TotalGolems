package com.totalgolems.entity;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The rancher. While it wears an azure bluet or a dead bush it looks after the animals in the fenced pen it stands
 * in: it feeds them from its bone chest so they breed, thins the herd when it grows past twenty,
 * and puts whatever is dropped on the ground into the chest.
 * <p>
 * It only feeds an animal that has a partner to breed with: another of its kind already in love, or
 * one it has the food to feed as well. Food is never wasted on an animal left without a mate.
 * <p>
 * The thinning takes a sword from its chest, which wears with every animal it kills: the one that
 * has the longest to wait before it can breed again. Without a sword it goes on feeding and tidying
 * up, and the herd just keeps growing.
 */
public class BoneGolem extends PenGolem {
	public static final int MAX_ANIMALS = 20;
	private static final int FOOD_SLOTS = 5;
	private static final int DROP_SLOTS = 9;
	/** After a trip to the chest that achieved nothing (it is full), how long before trying again. */
	private static final int RETRY_TICKS = 200;
	/** Ten seconds between one cull and the next, to give the herd time to breed. */
	private static final int CULL_INTERVAL = 200;

	private final SimpleContainer food = new SimpleContainer(FOOD_SLOTS);
	private final SimpleContainer drops = new SimpleContainer(DROP_SLOTS);
	private @Nullable Errand errand;
	private @Nullable Entity target;
	private int storeRetryTick;
	private int nextCullTick;

	private enum Errand {
		STORE,
		PICK_UP,
		FEED,
		FETCH_FOOD,
		FETCH_SWORD,
		CULL
	}

	public BoneGolem(final EntityType<? extends AbstractGolem> type, final Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createWorkerAttributes(1.0, 40.0);
	}

	@Override
	public String getTextureId() {
		return "bone";
	}

	@Override
	protected boolean isActivatingFlower(final ItemStack stack) {
		return stack.is(Items.AZURE_BLUET) || stack.is(Items.DEAD_BUSH);
	}

	@Override
	protected TagKey<Item> toolTag() {
		return ItemTags.SWORDS;
	}

	@Override
	protected boolean isIdleWithoutTool() {
		return false;
	}

	@Override
	protected void clearErrand() {
		this.errand = null;
		this.target = null;
	}

	/** Whether any animal in its pen eats {@code stack}, which makes it feed and not something to cart away. */
	public boolean feedsOn(final ServerLevel level, final ItemStack stack) {
		Enclosure pen = this.penOrSurroundings(level);
		return pen != null
			&& !level.getEntitiesOfClass(Animal.class, pen.bounds(), animal -> animal.isAlive() && pen.holds(animal.position()) && animal.isFood(stack)).isEmpty();
	}

	/** The animals alive in its pen and not escaped from it. */
	private List<Animal> animals(final ServerLevel level) {
		return level.getEntitiesOfClass(Animal.class, this.pen.bounds(), animal -> animal.isAlive() && this.pen.holds(animal.position()));
	}

	private <T extends Entity> @Nullable T nearest(final List<T> entities, final Predicate<T> wanted) {
		return entities.stream().filter(entity -> !this.isUnreachable(entity) && wanted.test(entity)).min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
	}

	/** Ready to breed: grown, not already in love and not resting from the last time. */
	private static boolean canBeFed(final Animal animal) {
		return !animal.isBaby() && animal.getAge() == 0 && animal.canFallInLove();
	}

	/** Pets and named animals are somebody's, and babies are left to grow up. */
	private static boolean canBeCulled(final Animal animal) {
		return !animal.isBaby() && !animal.hasCustomName() && !(animal instanceof OwnableEntity pet && pet.getOwnerReference() != null);
	}

	private boolean hasFoodFor(final Animal animal) {
		return this.food.hasAnyMatching(animal::isFood);
	}

	/** How much of what {@code animal} eats it carries. */
	private int foodFor(final Animal animal) {
		return this.food.getItems().stream().filter(animal::isFood).mapToInt(ItemStack::getCount).sum();
	}

	private static boolean isMateFor(final Animal animal, final Animal other) {
		return other != animal && other.getType() == animal.getType() && !other.isBaby();
	}

	/**
	 * Whether feeding {@code animal} gets a calf: another of its kind in the pen is in love already, or could
	 * be, with {@code food} enough for the two of them.
	 */
	private static boolean hasPartner(final List<Animal> animals, final Animal animal, final int food) {
		return animals.stream().anyMatch(other -> isMateFor(animal, other) && (other.isInLove() || food >= 2 && canBeFed(other)));
	}

	/** Whether {@code animal} is ready to breed, and it carries the food for it and, if need be, for its partner. */
	private boolean isWorthFeeding(final List<Animal> animals, final Animal animal) {
		return canBeFed(animal) && this.hasFoodFor(animal) && hasPartner(animals, animal, this.foodFor(animal));
	}

	@Override
	protected boolean plan(final ServerLevel level) {
		boolean chestUsable = this.canReach(this.chestPos) && this.findChest(level) != null;
		boolean mayStore = chestUsable && this.tickCount >= this.storeRetryTick;
		if (mayStore && isFull(this.drops)) {
			return this.start(Errand.STORE, null);
		}

		Container chest = chestUsable ? this.findChest(level) : null;
		if (!this.hasTool() && chest != null && chest.hasAnyMatching(stack -> stack.is(this.toolTag()))) {
			return this.start(Errand.FETCH_SWORD, null);
		}

		List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, this.pen.bounds(), item -> item.isAlive() && this.pen.holds(item.position()));
		ItemEntity item = this.nearest(items, candidate -> !candidate.hasPickUpDelay() && this.drops.canAddItem(candidate.getItem()));
		if (item != null) {
			return this.start(Errand.PICK_UP, item);
		}

		List<Animal> animals = this.animals(level);
		Animal hungry = this.nearest(animals, animal -> this.isWorthFeeding(animals, animal));
		if (hungry != null) {
			return this.start(Errand.FEED, hungry);
		}

		// Food for a pair, that is: an animal on its own is not worth the trip.
		if (chest != null && chest.hasAnyMatching(stack -> this.food.canAddItem(stack)
			&& animals.stream().anyMatch(animal -> canBeFed(animal) && animal.isFood(stack) && hasPartner(animals, animal, Integer.MAX_VALUE)))) {
			return this.start(Errand.FETCH_FOOD, null);
		}

		// Twenty or fewer and nothing dies. Babies count towards the total but are never the ones to go.
		// And not one after another: the herd gets time to breed between culls. The one to go is the one
		// with the longest wait before it can breed again, and never one in love if another will do.
		if (this.hasTool() && animals.size() > MAX_ANIMALS && this.tickCount >= this.nextCullTick) {
			Animal surplus = animals.stream()
				.filter(animal -> canBeCulled(animal) && !this.isUnreachable(animal))
				.min(Comparator.comparingInt((Animal animal) -> -animal.getAge())
					.thenComparing(Animal::isInLove)
					.thenComparingDouble(this::distanceToSqr))
				.orElse(null);
			if (surplus != null) {
				return this.start(Errand.CULL, surplus);
			}
		}

		return mayStore && !this.drops.isEmpty() && this.start(Errand.STORE, null);
	}

	@Override
	protected void onGaveUp() {
		if (this.target != null) {
			this.markUnreachable(this.target);
		}
	}

	private boolean start(final Errand errand, final @Nullable Entity target) {
		this.errand = errand;
		this.target = target;
		return true;
	}

	@Override
	protected boolean work(final ServerLevel level) {
		if (this.errand == Errand.STORE || this.errand == Errand.FETCH_FOOD || this.errand == Errand.FETCH_SWORD) {
			if (!this.canReach(this.chestPos)) {
				return true;
			}

			Container chest = this.reachContainer(level, this.chestPos);
			if (chest == null) {
				return false;
			}

			if (this.errand == Errand.FETCH_SWORD) {
				this.takeTool(chest);
			} else if (this.errand == Errand.FETCH_FOOD) {
				this.takeFood(level, chest);
			} else if (!unload(this.drops, chest)) {
				this.storeRetryTick = this.tickCount + RETRY_TICKS;
			}

			this.playSound(SoundEvents.ITEM_PICKUP, 0.3F, 0.8F);
			return true;
		}

		Entity entity = this.target;
		if (entity == null || !entity.isAlive() || !this.pen.holds(entity.position())) {
			return true;
		}

		if (!this.walkTo(level, entity.blockPosition())) {
			return false;
		}

		this.getLookControl().setLookAt(entity);
		this.setSwingingArm(this.errand == Errand.CULL);
		if (!this.isActTick()) {
			return false;
		}

		if (entity instanceof ItemEntity item) {
			ItemStack stack = item.getItem();
			int before = stack.getCount();
			ItemStack left = this.drops.addItem(stack);
			this.take(item, before - left.getCount());
			if (left.isEmpty()) {
				item.discard();
			} else {
				item.setItem(left);
			}
		} else if (entity instanceof Animal animal && this.errand == Errand.FEED) {
			this.feed(level, animal);
		} else if (entity instanceof Animal animal && this.hasTool() && this.animals(level).size() > MAX_ANIMALS && canBeCulled(animal)) {
			animal.hurtServer(level, this.cullingBlow(level), Float.MAX_VALUE);
			Weapon weapon = this.getTool().get(DataComponents.WEAPON);
			this.wearTool(weapon != null ? weapon.itemDamagePerAttack() : 1);
			this.nextCullTick = this.tickCount + CULL_INTERVAL;
		}

		return true;
	}

	/**
	 * The golem is behind the blow but does not deliver it in person. That keeps Looting, which goes by
	 * who is to blame, and leaves out Fire Aspect, which goes by whose hand it was: the meat comes out
	 * raw, and cooking it stays the coal golem's job.
	 */
	private DamageSource cullingBlow(final ServerLevel level) {
		return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.MOB_ATTACK), null, this);
	}

	private void feed(final ServerLevel level, final Animal animal) {
		// Its partner may have wandered off, or been fed by somebody else, on the way over.
		if (!this.isWorthFeeding(this.animals(level), animal)) {
			return;
		}

		for (int slot = 0; slot < this.food.getContainerSize(); slot++) {
			ItemStack stack = this.food.getItem(slot);
			if (animal.isFood(stack)) {
				stack.shrink(1);
				animal.setInLove(null);
				return;
			}
		}
	}

	/** Takes only what some animal in the pen eats that has another of its kind to breed with. */
	private void takeFood(final ServerLevel level, final Container chest) {
		List<Animal> animals = this.animals(level);
		for (int slot = 0; slot < chest.getContainerSize(); slot++) {
			ItemStack stack = chest.getItem(slot);
			if (!stack.isEmpty() && animals.stream().anyMatch(animal -> animal.isFood(stack) && animals.stream().anyMatch(other -> isMateFor(animal, other)))) {
				chest.setItem(slot, this.food.addItem(stack));
			}
		}

		chest.setChanged();
	}

	@Override
	public void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		this.food.storeAsItemList(output.list("food", ItemStack.CODEC));
		this.drops.storeAsItemList(output.list("drops", ItemStack.CODEC));
	}

	@Override
	public void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		this.food.fromItemList(input.listOrEmpty("food", ItemStack.CODEC));
		this.drops.fromItemList(input.listOrEmpty("drops", ItemStack.CODEC));
	}

	@Override
	protected void dropEquipment(final ServerLevel level) {
		super.dropEquipment(level);
		for (SimpleContainer container : List.of(this.food, this.drops)) {
			container.removeAllItems().forEach(stack -> this.spawnAtLocation(level, stack));
		}
	}
}

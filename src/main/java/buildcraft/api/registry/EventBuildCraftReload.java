package buildcraft.api.registry;

import java.util.Set;

import javax.annotation.Nullable;

import com.google.gson.GsonBuilder;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import buildcraft.api.events.BCEvent;
import buildcraft.api.events.BCEventListeners;
import buildcraft.api.fluids.BCFluidStack;

/** Posted by the reloadable-registry manager around a reload; each stage posts to its own {@link BCEventListeners}
 * {@code LISTENERS} (and, on NeoForge, then on {@code NeoForge.EVENT_BUS}, see {@link BCEvent}), in the order the stages
 * are documented below. */
public abstract class EventBuildCraftReload extends BCEvent {

    /** The manager that is being reloaded. */
    public final IReloadableRegistryManager manager;

    /** A set of all {@link IReloadableRegistry}'s that are being reloaded. */
    public final Set<IReloadableRegistry<?>> reloadingRegistries;

    public EventBuildCraftReload(IReloadableRegistryManager manager, Set<IReloadableRegistry<?>> reloadingRegistries) {
        this.manager = manager;
        this.reloadingRegistries = reloadingRegistries;
    }

    /** Fired before all script based entries are registered, but before the previous recipes have been cleared. I'm not
     * sure how useful this actually is. */
    public static class BeforeClear extends EventBuildCraftReload {
        public static final BCEventListeners<BeforeClear> LISTENERS = new BCEventListeners<>();

        public BeforeClear(IReloadableRegistryManager manager,
            @Nullable Set<IReloadableRegistry<?>> reloadingRegistries) {
            super(manager, reloadingRegistries);
        }
    }

    /** Fired before all script based buildcraft reloadable entries will be registered. Use this to add your own
     * entries. */
    public static class PreLoad extends EventBuildCraftReload {
        public static final BCEventListeners<PreLoad> LISTENERS = new BCEventListeners<>();

        public PreLoad(IReloadableRegistryManager manager, @Nullable Set<IReloadableRegistry<?>> reloadingRegistries) {
            super(manager, reloadingRegistries);
        }
    }

    /** Fired after {@link PreLoad}, but before scripts are loaded in order to register custom type adaptors for various
     * classes. BuildCraft itself adds adaptors for {@link ItemStack}, {@link Ingredient}, and {@link BCFluidStack} before
     * this event is fired. */
    public static class PopulateGson extends EventBuildCraftReload {
        public static final BCEventListeners<PopulateGson> LISTENERS = new BCEventListeners<>();

        public final GsonBuilder gsonBuilder;

        public PopulateGson(IReloadableRegistryManager manager,
            @Nullable Set<IReloadableRegistry<?>> reloadingRegistries, GsonBuilder gsonBuilder) {
            super(manager, reloadingRegistries);
            this.gsonBuilder = gsonBuilder;
        }
    }

    /** Fired when all script based buildcraft reloadable entries have been registered. Use this to modify existing
     * entries. */
    public static class PostLoad extends EventBuildCraftReload {
        public static final BCEventListeners<PostLoad> LISTENERS = new BCEventListeners<>();

        public PostLoad(IReloadableRegistryManager manager, @Nullable Set<IReloadableRegistry<?>> reloadingRegistries) {
            super(manager, reloadingRegistries);
        }
    }

    /** Fired after {@link PostLoad}, when the {@link IReloadableRegistry#getReloadableEntryMap()} has been frozen until
     * the next reload. */
    public static class FinishLoad extends EventBuildCraftReload {
        public static final BCEventListeners<FinishLoad> LISTENERS = new BCEventListeners<>();

        public FinishLoad(IReloadableRegistryManager manager,
            @Nullable Set<IReloadableRegistry<?>> reloadingRegistries) {
            super(manager, reloadingRegistries);
        }
    }
}


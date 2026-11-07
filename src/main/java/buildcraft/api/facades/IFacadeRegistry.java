package buildcraft.api.facades;

import java.util.Collection;

import javax.annotation.Nullable;

import net.minecraft.world.item.DyeColor;

public interface IFacadeRegistry {

    /** Every block state that can be a facade, including look-alikes a client hides from its own lists. */
    Collection<? extends IFacadeState> getValidFacades();

    IFacadePhasedState createPhasedState(IFacadeState state, @Nullable DyeColor activeColor);

    IFacade createPhasedFacade(IFacadePhasedState[] states, boolean isHollow);

    default IFacade createBasicFacade(IFacadeState state, boolean isHollow) {
        return createPhasedFacade(new IFacadePhasedState[] { createPhasedState(state, null) }, isHollow);
    }
}


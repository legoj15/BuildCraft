package buildcraft.lib.misc;

import javax.annotation.Nonnull;

/** Capability reference stubs. In 1.12 these were Forge Capability tokens;
 *  in NeoForge 1.21 the capability system is different. These exist so that
 *  statement classes can compile — actual capability lookups will be
 *  modernised when the transport/gate system is ported. */
public class CapUtil {
    @Nonnull
    public static final Object CAP_ITEMS = new Object();

    @Nonnull
    public static final Object CAP_FLUIDS = new Object();
}

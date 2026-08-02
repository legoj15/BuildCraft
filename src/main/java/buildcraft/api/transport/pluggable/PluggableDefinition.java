package buildcraft.api.transport.pluggable;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.Direction;


import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.transport.pipe.IPipeHolder;

public final class PluggableDefinition {
    public final Object identifier;

    public final IPluggableNetLoader loader;
    public final IPluggableNbtReader reader;

    @Nullable
    public final IPluggableCreator creator;

    /**
     * A pluggable that can only be rebuilt from saved/synced data, never conjured from nothing — so it has
     * no {@link #creator} and therefore <b>cannot be placed by {@code ItemPluggableSimple}</b>
     * ({@code onPlace} returns null when {@code creator} is null, and {@code BlockPipeHolder.useItemOn}
     * then falls through silently). Correct for pluggables whose item builds them itself (facades, gates,
     * lenses, pulsars); if the pluggable IS meant to be placed from a plain item, use
     * {@link #PluggableDefinition(Object, IPluggableNbtReader, IPluggableNetLoader, IPluggableCreator)}.
     */
    public PluggableDefinition(Object identifier, IPluggableNbtReader reader, IPluggableNetLoader loader) {
        this(identifier, reader, loader, null);
    }

    /**
     * The all-in-one case: one creator serves as reader, loader and creator. Note that
     * {@link IPluggableCreator}'s default {@code loadFromBuffer} <b>ignores the buffer</b>, so this is only
     * correct for pluggables that sync no state of their own; anything that writes a creation payload must
     * use the four-argument constructor instead or its synced state is dropped (and the buffer left unread).
     */
    public PluggableDefinition(Object identifier, @Nullable IPluggableCreator creator) {
        this.identifier = identifier;
        this.reader = creator;
        this.loader = creator;
        this.creator = creator;
    }

    /**
     * Explicit reader and loader — for a pluggable with real synced/persisted state — <i>plus</i> a creator,
     * so a plain {@code ItemPluggableSimple} can still place it. Splitting these three apart is the only way
     * to have both: the two-argument constructor's shared creator silently drops the creation payload, while
     * the three-argument one leaves the pluggable unplaceable.
     */
    public PluggableDefinition(Object identifier, IPluggableNbtReader reader, IPluggableNetLoader loader,
        @Nullable IPluggableCreator creator) {
        this.identifier = identifier;
        this.reader = reader;
        this.loader = loader;
        this.creator = creator;
    }

    public PipePluggable readFromNbt(IPipeHolder holder, Direction side, CompoundTag nbt) {
        return reader.readFromNbt(this, holder, side, nbt);
    }

    public PipePluggable loadFromBuffer(IPipeHolder holder, Direction side, FriendlyByteBuf buffer)
        throws InvalidInputDataException {
        return loader.loadFromBuffer(this, holder, side, buffer);
    }

    @FunctionalInterface
    public interface IPluggableNbtReader {
        /** Reads the pipe pluggable from NBT. Unlike {@link IPluggableNetLoader} (which is allowed to fail and throw an
         * exception if the wrong data is given) this should make a best effort to read the pluggable from nbt, or fall
         * back to sensible defaults. */
        PipePluggable readFromNbt(PluggableDefinition definition, IPipeHolder holder, Direction side,
            CompoundTag nbt);
    }

    @FunctionalInterface
    public interface IPluggableNetLoader {
        PipePluggable loadFromBuffer(PluggableDefinition definition, IPipeHolder holder, Direction side,
            FriendlyByteBuf buffer) throws InvalidInputDataException;
    }

    @FunctionalInterface
    public interface IPluggableCreator extends IPluggableNbtReader, IPluggableNetLoader {
        @Override
        default PipePluggable loadFromBuffer(PluggableDefinition definition, IPipeHolder holder, Direction side,
            FriendlyByteBuf buffer) {
            return createSimplePluggable(definition, holder, side);
        }

        @Override
        default PipePluggable readFromNbt(PluggableDefinition definition, IPipeHolder holder, Direction side,
            CompoundTag nbt) {
            return createSimplePluggable(definition, holder, side);
        }

        PipePluggable createSimplePluggable(PluggableDefinition definition, IPipeHolder holder, Direction side);
    }
}


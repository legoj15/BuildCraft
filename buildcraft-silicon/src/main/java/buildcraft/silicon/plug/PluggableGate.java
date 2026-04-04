package buildcraft.silicon.plug;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import buildcraft.silicon.item.ItemGateCopier;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.network.chat.Component;

import buildcraft.api.transport.IWireEmitter;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pipe.IPipeHolder.PipeMessageReceiver;
import buildcraft.api.transport.pluggable.PipePluggable;
import buildcraft.api.transport.pluggable.PluggableDefinition;
import buildcraft.api.transport.pluggable.PluggableModelKey;
import buildcraft.silicon.client.model.key.KeyPlugGate;

import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.data.ModelVariableData;
import buildcraft.lib.net.IPayloadWriter;
import buildcraft.lib.net.PacketBufferBC;

// import buildcraft.silicon.BCSiliconGuis; // TODO: port GUI
import buildcraft.silicon.BCSiliconItems;
import buildcraft.silicon.client.model.key.KeyPlugGate;
import buildcraft.silicon.gate.EnumGateLogic;
import buildcraft.silicon.gate.EnumGateMaterial;
import buildcraft.silicon.gate.EnumGateModifier;
import buildcraft.silicon.gate.GateLogic;
import buildcraft.silicon.gate.GateVariant;

public class PluggableGate extends PipePluggable implements IWireEmitter {
    // PluggableHolder.ID_UPDATE_PLUG (was 1 locally in BuildCraft)
    private static final byte ID_UPDATE_PLUG = 1;

    // public static final FunctionContext MODEL_FUNC_CTX_STATIC, MODEL_FUNC_CTX_DYNAMIC;
    // private static final NodeVariableObject<String> MODEL_MATERIAL;
    // private static final NodeVariableObject<String> MODEL_MODIFIER;
    // private static final NodeVariableObject<String> MODEL_LOGIC;
    // private static final NodeVariableObject<Direction> MODEL_SIDE;
    // private static final NodeVariableBoolean MODEL_IS_ON;
    // public static final ContextInfo MODEL_VAR_INFO;

    private static final AABB[] BOXES = new AABB[6];

    private static final Identifier ADVANCEMENT_PLACE_GATE
        = Identifier.parse("buildcraftunofficial:pipe_logic");

    private static final Identifier ADVANCEMENT_PLACE_ADV_GATE
        = Identifier.parse("buildcraftunofficial:extended_logic");

    public final GateLogic logic;

    public final ModelVariableData clientModelData = new ModelVariableData();

    static {
        double ll = 2 / 16.0;
        double lu = 4 / 16.0;
        double ul = 12 / 16.0;
        double uu = 14 / 16.0;

        double min = 5 / 16.0;
        double max = 11 / 16.0;

        BOXES[Direction.DOWN.get3DDataValue()] = new AABB(min, ll, min, max, lu, max);
        BOXES[Direction.UP.get3DDataValue()] = new AABB(min, ul, min, max, uu, max);
        BOXES[Direction.NORTH.get3DDataValue()] = new AABB(min, min, ll, max, max, lu);
        BOXES[Direction.SOUTH.get3DDataValue()] = new AABB(min, min, ul, max, max, uu);
        BOXES[Direction.WEST.get3DDataValue()] = new AABB(ll, min, min, lu, max, max);
        BOXES[Direction.EAST.get3DDataValue()] = new AABB(ul, min, min, uu, max, max);

        /* TODO: Port expression subsystem
        MODEL_FUNC_CTX_STATIC = DefaultContexts.createWithAll();
        MODEL_MATERIAL = MODEL_FUNC_CTX_STATIC.putVariableString("material");
        MODEL_MODIFIER = MODEL_FUNC_CTX_STATIC.putVariableString("modifier");
        MODEL_LOGIC = MODEL_FUNC_CTX_STATIC.putVariableString("logic");
        MODEL_SIDE = MODEL_FUNC_CTX_STATIC.putVariableObject("side", Direction.class);

        MODEL_FUNC_CTX_DYNAMIC = new FunctionContext(MODEL_FUNC_CTX_STATIC);
        MODEL_IS_ON = MODEL_FUNC_CTX_DYNAMIC.putVariableBoolean("on");

        MODEL_VAR_INFO = new ContextInfo(MODEL_FUNC_CTX_DYNAMIC);
        VariableInfoObject<String> infoMaterial = MODEL_VAR_INFO.createInfoObject(MODEL_MATERIAL);
        infoMaterial.cacheType = CacheType.ALWAYS;
        infoMaterial.setIsComplete = true;
        infoMaterial.possibleValues
            .addAll(Arrays.stream(EnumGateMaterial.VALUES).map(m -> m.tag).collect(Collectors.toList()));

        VariableInfoObject<String> infoModifier = MODEL_VAR_INFO.createInfoObject(MODEL_MODIFIER);
        infoModifier.cacheType = CacheType.ALWAYS;
        infoModifier.setIsComplete = true;
        infoModifier.possibleValues
            .addAll(Arrays.stream(EnumGateModifier.VALUES).map(m -> m.tag).collect(Collectors.toList()));

        VariableInfoObject<String> infoLogic = MODEL_VAR_INFO.createInfoObject(MODEL_LOGIC);
        infoLogic.cacheType = CacheType.ALWAYS;
        infoLogic.setIsComplete = true;
        infoLogic.possibleValues
            .addAll(Arrays.stream(EnumGateLogic.VALUES).map(m -> m.tag).collect(Collectors.toList()));

        VariableInfoObject<Direction> infoSide = MODEL_VAR_INFO.createInfoObject(MODEL_SIDE);
        infoSide.cacheType = CacheType.ALWAYS;
        infoSide.setIsComplete = true;
        Collections.addAll(infoSide.possibleValues, Direction.values());

        VariableInfoBoolean infoIsOn = MODEL_VAR_INFO.createInfoBoolean(MODEL_IS_ON);
        infoIsOn.cacheType = CacheType.ALWAYS;
        infoIsOn.setIsComplete = true;
        infoIsOn.possibleValues = BooleanPossibilities.FALSE_TRUE;
        */
    }

    // Manual constructor (called by the specific item pluggable gate code)

    public PluggableGate(PluggableDefinition def, IPipeHolder holder, Direction side, GateVariant variant) {
        super(def, holder, side);
        logic = new GateLogic(this, variant);
    }

    // Saving + Loading

    public PluggableGate(PluggableDefinition def, IPipeHolder holder, Direction side, CompoundTag nbt) {
        super(def, holder, side);
        logic = new GateLogic(this, nbt.getCompound("data").orElse(new CompoundTag()));
    }

    @Override
    public CompoundTag writeToNbt() {
        CompoundTag nbt = super.writeToNbt();
        nbt.put("data", logic.writeToNbt());
        return nbt;
    }

    // Networking

    public PluggableGate(PluggableDefinition def, IPipeHolder holder, Direction side, net.minecraft.network.FriendlyByteBuf buffer) {
        super(def, holder, side);
        PacketBufferBC packetBuffer = new PacketBufferBC(buffer);
        logic = new GateLogic(this, packetBuffer);
    }

    // @Override
    // public void writeCreationPayload(net.minecraft.network.RegistryFriendlyByteBuf buffer) {
    //     super.writeCreationPayload(buffer);
    //     PacketBufferBC packetBuffer = new PacketBufferBC(buffer);
    //     logic.writeCreationToBuf(packetBuffer);
    // }

    public void sendMessage(IPayloadWriter writer) {
        PipeMessageReceiver to = PipeMessageReceiver.PLUGGABLES[side.ordinal()];
        holder.sendMessage(to, (buffer) -> {
            /* The pluggable holder receives this message and requires the ID '1' (UPDATE) to forward the message onto
             * ourselves */
            buffer.writeByte(ID_UPDATE_PLUG);
            writer.write(buffer);
        });
    }

    public void sendGuiMessage(IPayloadWriter writer) {
        PipeMessageReceiver to = PipeMessageReceiver.PLUGGABLES[side.ordinal()];
        holder.sendGuiMessage(to, (buffer) -> {
            /* The pluggable holder receives this message and requires the ID '1' (UPDATE) to forward the message onto
             * ourselves */
            buffer.writeByte(ID_UPDATE_PLUG);
            writer.write(buffer);
        });
    }

    // @Override
    // public void writePayload(FriendlyByteBuf buffer, Object side) {
    //     throw new Error("All messages must have an ID, and we can't just write a payload directly!");
    // }

    // @Override
    // public void readPayload(FriendlyByteBuf b, Object side, Object ctx) throws IOException {
    //     PacketBufferBC packetBuffer = new PacketBufferBC(b);
    //     logic.readPayload(packetBuffer, ((Boolean) ctx).booleanValue());
    // }

    // PipePluggable

    @Override
    public AABB getBoundingBox() {
        return BOXES[side.get3DDataValue()];
    }

    @Override
    public boolean isBlocking() {
        return true;
    }

    @Override
    public ItemStack getPickStack() {
        // return BCSiliconItems.gate.getStack(logic.variant);
        return ItemStack.EMPTY; // TODO: ItemPluggableGate
    }

    @Override
    public PluggableModelKey getModelRenderKey(Object layer) {
        if ("cutout".equals(layer)) {
            return new KeyPlugGate(side, logic.variant, logic.isOn);
        }
        return null;
    }

    @Override
    public void onPlacedBy(Player player) {
        super.onPlacedBy(player);
        if (!holder.getPipeWorld().isClientSide()) {
            AdvancementUtil.unlockAdvancement(player, ADVANCEMENT_PLACE_GATE);
            if (logic.variant.numActionArgs >= 1) {
                AdvancementUtil.unlockAdvancement(player, ADVANCEMENT_PLACE_ADV_GATE);
            }
        }
    }

    @Override
    public boolean onPluggableActivate(Player player, HitResult trace, float hitX, float hitY, float hitZ) {
        if (!player.level().isClientSide()) {
            if (interactWithCopier(player, player.getMainHandItem())) {
                return true;
            }
            if (interactWithCopier(player, player.getOffhandItem())) {
                return true;
            }

            BlockPos pos = holder.getPipePos();
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                serverPlayer.openMenu(new net.minecraft.world.MenuProvider() {
                    @Override
                    public Component getDisplayName() {
                        return logic.variant.getLocalizedName();
                    }

                    @Override
                    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inv, Player p) {
                        return new buildcraft.silicon.container.ContainerGate(id, inv, PluggableGate.this);
                    }
                }, buf -> {
                    buf.writeBlockPos(pos);
                    buf.writeEnum(side);
                });
            }
        }
        return true;
    }

    private boolean interactWithCopier(Player player, ItemStack stack) {
        if (!(stack.getItem() instanceof ItemGateCopier)) {
            return false;
        }

        // TODO: ItemGateCopier interactions
        /*
        CompoundTag stored = ItemGateCopier.getCopiedGateData(stack);

        if (stored != null) {

            logic.readConfigData(stored);

            player.displayClientMessage(Component.translatable("chat.gateCopier.gatePasted"), true);

        } else {
            stored = logic.writeToNbt();
            stored.remove("wireBroadcasts");

            if (stored.size() == 1) {
                player.displayClientMessage(Component.translatable("chat.gateCopier.noInformation"), true);
                return false;
            }

            ItemGateCopier.setCopiedGateData(stack, stored);
            player.displayClientMessage(Component.translatable("chat.gateCopier.gateCopied"), true);
        }
        */

        return true;
    }

    @Override
    public boolean isEmitting(DyeColor colour) {
        return logic.isEmitting(colour);
    }

    @Override
    public void emitWire(DyeColor colour) {
        logic.emitWire(colour);
    }

    // Gate methods

    @Override
    public void onTick() {
        logic.onTick();
        if (holder.getPipeWorld().isClientSide()) {
            clientModelData.tick();
        }
    }

    @Override
    public boolean canConnectToRedstone(@Nullable Direction to) {
        return true;
    }

    // Model

    public static void setClientModelVariables(Direction side, GateVariant variant) {
        /*
        MODEL_SIDE.value = side;
        MODEL_MATERIAL.value = variant.material.tag;
        MODEL_MODIFIER.value = variant.modifier.tag;
        MODEL_LOGIC.value = variant.logic.tag;
        MODEL_IS_ON.value = false;// Used by the item
        */
    }

    public void setClientModelVariables() {
        // setClientModelVariables(side, logic.variant);
        // MODEL_IS_ON.value = logic.isOn;
    }
}

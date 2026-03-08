package buildcraft.builders.container;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

import buildcraft.api.filler.IFillerPattern;

import buildcraft.lib.gui.ContainerBC_Neptune;
import buildcraft.lib.statement.FullStatement;

import buildcraft.builders.addon.AddonFillerPlanner;
import buildcraft.builders.filler.FillerType;

public class ContainerFillerPlanner extends ContainerBC_Neptune implements IContainerFilling {
    // TODO: The addon lookup requires WorldSavedDataVolumeBoxes + EnumAddonSlot
    // which are not yet ported as source. For now we allow null addon during
    // container construction; the container will only be fully functional once
    // the volume box system is ported.
    public AddonFillerPlanner addon;
    private final FullStatement<IFillerPattern> patternStatementClient = new FullStatement<>(
        FillerType.INSTANCE,
        4,
        (statement, paramIndex) -> onStatementChange()
    );

    public ContainerFillerPlanner(MenuType<?> menuType, int containerId, Player player) {
        super(menuType, containerId, player);
        // TODO: look up addon from WorldSavedDataVolumeBoxes when that class is ported
        addon = null;
        init();
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public FullStatement<IFillerPattern> getPatternStatementClient() {
        return patternStatementClient;
    }

    @Override
    public FullStatement<IFillerPattern> getPatternStatement() {
        return addon != null ? addon.patternStatement : patternStatementClient;
    }

    @Override
    public boolean isInverted() {
        return addon != null && addon.inverted;
    }

    @Override
    public void setInverted(boolean value) {
        if (addon != null) {
            addon.inverted = value;
        }
    }

    @Override
    public void valuesChanged() {
        if (addon != null) {
            addon.updateBuildingInfo();
        }
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}

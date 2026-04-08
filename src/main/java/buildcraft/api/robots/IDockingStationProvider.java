package buildcraft.api.robots;

/** By default, this can be either an IPipePluggable or a BlockEntity. */
public interface IDockingStationProvider {
    DockingStation getStation();
}


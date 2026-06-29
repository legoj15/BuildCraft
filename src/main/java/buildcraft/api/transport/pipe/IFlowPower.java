package buildcraft.api.transport.pipe;

public interface IFlowPower extends IFlowPowerLike {
    /** Makes this pipe reconfigure itself, possibly due to the addition of new modules. */
    @Override
    void reconfigure();
}


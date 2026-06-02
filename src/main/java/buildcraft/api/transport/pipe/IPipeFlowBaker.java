package buildcraft.api.transport.pipe;

import java.util.List;

//? if >=26.1 {
import net.minecraft.client.resources.model.geometry.BakedQuad;
//?} else {
/*import net.minecraft.client.renderer.block.model.BakedQuad;*/
//?}

public interface IPipeFlowBaker<F extends PipeFlow> {
    List<BakedQuad> bake(F flow);
}


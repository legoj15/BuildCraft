package buildcraft.lib.misc.collect;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.lib.misc.collect.TypedMap;
import buildcraft.lib.misc.collect.TypedMapDirect;
import buildcraft.lib.misc.collect.TypedMapHierarchy;

public class TypedMapTester {

    interface IRandomInterface {
        int getRandom();
    }

    interface INamedInterface {
        String getName();
    }

    class Independant {}

    enum RandomE implements IRandomInterface {
        A,
        B;

        @Override
        public int getRandom() {
            return ordinal();
        }
    }

    enum Both implements IRandomInterface, INamedInterface {
        A,
        B;

        @Override
        public String getName() {
            return name();
        }

        @Override
        public int getRandom() {
            return 15 + ordinal();
        }
    }

    @Test
    public void testDirect() {
        TypedMap<Object> map = new TypedMapDirect<>();
        map.put(RandomE.A);
        map.put(Both.A);
        Independant i = new Independant();
        map.put(i);

        Assertions.assertNull(map.get(IRandomInterface.class));
        Assertions.assertNull(map.get(INamedInterface.class));
        Assertions.assertEquals(i, map.get(Independant.class));
        Assertions.assertEquals(RandomE.A, map.get(RandomE.class));
        Assertions.assertEquals(Both.A, map.get(Both.class));

        map.put(RandomE.B);

        Assertions.assertEquals(RandomE.B, map.get(RandomE.class));

        map.remove(RandomE.B);

        Assertions.assertNull(map.get(RandomE.class));
    }

    @Test
    public void testMult() {
        TypedMap<Object> map = new TypedMapHierarchy<>();
        map.put(RandomE.A);
        map.put(Both.A);
        Independant i = new Independant();
        map.put(i);

        Assertions.assertNotNull(map.get(IRandomInterface.class));
        Assertions.assertEquals(Both.A, map.get(INamedInterface.class));
        Assertions.assertEquals(i, map.get(Independant.class));
        Assertions.assertEquals(RandomE.A, map.get(RandomE.class));
        Assertions.assertEquals(Both.A, map.get(Both.class));
    }
}

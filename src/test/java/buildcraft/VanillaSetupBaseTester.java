package buildcraft;

import java.io.InputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.BeforeAll;

import net.minecraft.server.Bootstrap;

public class VanillaSetupBaseTester {
    @BeforeAll
    public static void init() {
        System.out.println("INIT");
        PrintStream sysOut = System.out;
        InputStream sysIn = System.in;

        //? if >=26.1 {
        Bootstrap.bootStrap();
        //? }

        System.setIn(sysIn);
        System.setOut(sysOut);
    }
}

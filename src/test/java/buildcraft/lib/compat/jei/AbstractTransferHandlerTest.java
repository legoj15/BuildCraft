/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.compat.jei;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
//? if >=1.21.10 {
import mezz.jei.api.recipe.types.IRecipeType;
//?} else {
/*import mezz.jei.api.recipe.RecipeType;*/
//?}
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Player;

import buildcraft.lib.gui.ContainerBC_Neptune;

/**
 * Pins the order of the two halves every BuildCraft JEI "+" handler shares ({@link AbstractTransferHandler}): JEI
 * calls it with {@code doTransfer = false} on every hover to decide whether the button is enabled, so that call must
 * never send anything to the server; a real press sends exactly once, and only when the check said OK; and each
 * {@link TransferResult} comes back as its own JEI answer. The machine-specific halves are stubbed — they are
 * covered by the table and bucket transfer tests — so this stays JEI-API-only and headless.
 */
public class AbstractTransferHandlerTest {

    /** Counts what the handler asked JEI for; the answers themselves are inert stand-ins. */
    private static final class Answers {
        final List<String> userErrorKeys = new ArrayList<>();
        int internalErrors;

        IRecipeTransferHandlerHelper helper() {
            return (IRecipeTransferHandlerHelper) Proxy.newProxyInstance(
                AbstractTransferHandlerTest.class.getClassLoader(),
                new Class<?>[] {IRecipeTransferHandlerHelper.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "createUserErrorWithTooltip" -> {
                            Component tooltip = (Component) args[0];
                            userErrorKeys.add(((TranslatableContents) tooltip.getContents()).getKey());
                        }
                        case "createInternalError" -> internalErrors++;
                        default -> throw new AssertionError("unexpected JEI helper call: " + method);
                    }
                    return Proxy.newProxyInstance(AbstractTransferHandlerTest.class.getClassLoader(),
                        new Class<?>[] {IRecipeTransferError.class}, (p, m, a) -> null);
                });
        }
    }

    /** A handler whose check answers {@link #result} and which records what the skeleton did with it. */
    private static final class Probe extends AbstractTransferHandler<ContainerBC_Neptune, String> {
        TransferResult result = TransferResult.OK;
        int checks;
        final List<String> sent = new ArrayList<>();
        final List<Boolean> checkedMax = new ArrayList<>();
        final List<Boolean> sentMax = new ArrayList<>();

        Probe(Answers answers) {
            super(answers.helper(), ContainerBC_Neptune.class, () -> null);
        }

        @Override
        //? if >=1.21.10 {
        public IRecipeType<String> getRecipeType() {
        //?} else {
        /*public RecipeType<String> getRecipeType() {*/
        //?}
            return null;
        }

        @Override
        protected TransferResult check(ContainerBC_Neptune container, String recipe, Player player,
                boolean maxTransfer) {
            checks++;
            checkedMax.add(maxTransfer);
            return result;
        }

        @Override
        protected void send(ContainerBC_Neptune container, String recipe, Player player, boolean maxTransfer) {
            sent.add(recipe);
            sentMax.add(maxTransfer);
        }

        @SuppressWarnings("removal")
        IRecipeTransferError press(String recipe, boolean maxTransfer, boolean doTransfer) {
            return transferRecipe(null, recipe, null, null, maxTransfer, doTransfer);
        }
    }

    @Test
    public void theAdvisoryCallNeverSendsWhateverTheCheckSays() {
        for (TransferResult result : TransferResult.values()) {
            Probe probe = new Probe(new Answers());
            probe.result = result;

            probe.press("recipe", false, false);

            Assertions.assertEquals(1, probe.checks, result + ": the advisory call runs the check");
            Assertions.assertTrue(probe.sent.isEmpty(),
                result + ": hovering the button must not message the server");
        }
    }

    @Test
    public void aPressSendsOnceAndOnlyWhenTheCheckSaidOk() {
        for (TransferResult result : TransferResult.values()) {
            Probe probe = new Probe(new Answers());
            probe.result = result;

            probe.press("recipe", true, true);

            Assertions.assertEquals(result == TransferResult.OK ? 1 : 0, probe.sent.size(),
                result + ": a real press sends exactly when the check passed");
        }
    }

    @Test
    public void theSendGetsTheSameRecipeAndQuantityTheCheckSaw() {
        Probe probe = new Probe(new Answers());

        probe.press("the-recipe", true, true);

        Assertions.assertEquals(List.of("the-recipe"), probe.sent);
        Assertions.assertEquals(List.of(true), probe.checkedMax, "shift-click reaches the check");
        Assertions.assertEquals(List.of(true), probe.sentMax, "and the send");
    }

    @Test
    public void eachResultIsItsOwnJeiAnswer() {
        Answers ok = new Answers();
        Probe okProbe = new Probe(ok);
        Assertions.assertNull(okProbe.press("recipe", false, false), "OK enables the button: no JEI error");
        Assertions.assertTrue(ok.userErrorKeys.isEmpty() && ok.internalErrors == 0);

        Answers missing = new Answers();
        Probe missingProbe = new Probe(missing);
        missingProbe.result = TransferResult.MISSING;
        Assertions.assertNotNull(missingProbe.press("recipe", false, false));
        Assertions.assertEquals(1, missing.userErrorKeys.size(), "MISSING is a tooltip on the greyed button");

        Answers busy = new Answers();
        Probe busyProbe = new Probe(busy);
        busyProbe.result = TransferResult.BUSY;
        Assertions.assertNotNull(busyProbe.press("recipe", false, false));
        Assertions.assertEquals(1, busy.userErrorKeys.size(), "BUSY is a tooltip on the greyed button");
        Assertions.assertNotEquals(missing.userErrorKeys.get(0), busy.userErrorKeys.get(0),
            "but it says something different from MISSING: the player has to act differently");

        Answers invalid = new Answers();
        Probe invalidProbe = new Probe(invalid);
        invalidProbe.result = TransferResult.INVALID;
        Assertions.assertNotNull(invalidProbe.press("recipe", false, false));
        Assertions.assertEquals(1, invalid.internalErrors, "INVALID is JEI's internal error, not a player tooltip");
        Assertions.assertTrue(invalid.userErrorKeys.isEmpty());
    }
}

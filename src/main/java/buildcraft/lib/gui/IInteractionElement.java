package buildcraft.lib.gui;

public interface IInteractionElement extends IGuiElement {

    /** This is called EVEN IF the mouse is not inside your width and height! */
    default void onMouseClicked(int button) {}

    /** This is called EVEN IF the mouse is not inside your width and height! */
    default void onMouseDragged(int button, long ticksSinceClick) {}

    /** This is called EVEN IF the mouse is not inside your width and height! */
    default void onMouseReleased(int button) {}

    default boolean onKeyPress(char typedChar, int keyCode) {
        return false;
    }

    /** Offered the mouse wheel before the screen's own handling; unlike the clicks above, an element is expected to
     *  check for itself that the mouse is over it.
     *
     *  @param amountY the wheel delta, positive for wheel up
     *  @return true if the event was used, which stops it reaching anything else */
    default boolean onMouseScrolled(double amountY) {
        return false;
    }
}

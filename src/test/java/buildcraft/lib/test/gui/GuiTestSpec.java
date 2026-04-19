package buildcraft.lib.test.gui;

import java.util.List;

public class GuiTestSpec {
    public String test_name;
    public String block;

    public List<GuiAction> actions;
    public List<GuiAssert> asserts;

    public static class GuiAction {
        public String type;
        public String item;
        public Integer count;
        public Integer slot;
        public String action;
        public Integer button_id;  
        
        // Custom Payload Fields
        public Integer message_id;
        public String string_payload;
        public String pattern_tag;
        public String param_slot_0;
    }

    public static class GuiAssert {
        public String type;
        public Integer slot;
        public String item;
        public Integer count;
        public String property;
        public String value;
        
        // Custom Asserts
        public Boolean boolean_value;
        public String pattern_tag;
        public String param_slot_0;
    }
}

/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import buildcraft.lib.client.sprite.SpriteHolderRegistry;
import buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder;

/** Sprite holder definitions for the robotics gate statements (Ph6). Each holder resolves
 *  {@code buildcraftunofficial:triggers/<icon>} — the 1.12.2-era trigger textures moved from
 *  {@code misc/unused_textures/triggers/}. Mirrors {@code BCCoreSprites}. */
public class BCRoboticsSprites {
    public static final SpriteHolder ACTION_ROBOT_FILTER = h("triggers/action_robot_filter");
    public static final SpriteHolder ACTION_ROBOT_FILTER_TOOL = h("triggers/action_robot_filter_tool");
    public static final SpriteHolder ACTION_ROBOT_GOTO_STATION = h("triggers/action_robot_goto_station");
    public static final SpriteHolder ACTION_ROBOT_WAKE_UP = h("triggers/action_robot_wakeup");
    public static final SpriteHolder ACTION_ROBOT_WORK_IN_AREA = h("triggers/action_robot_work_in_area");
    public static final SpriteHolder ACTION_ROBOT_LOAD_UNLOAD_AREA = h("triggers/action_robot_load_unload_area");

    public static final SpriteHolder ACTION_STATION_ACCEPT_FLUIDS = h("triggers/action_station_accept_fluids");
    public static final SpriteHolder ACTION_STATION_ACCEPT_ITEMS = h("triggers/action_station_drop_in_pipe");
    public static final SpriteHolder ACTION_STATION_FORBID_ROBOT = h("triggers/action_station_robot_forbidden");
    public static final SpriteHolder ACTION_STATION_FORCE_ROBOT = h("triggers/action_station_robot_mandatory");
    public static final SpriteHolder ACTION_STATION_PROVIDE_FLUIDS = h("triggers/action_station_provide_fluids");
    public static final SpriteHolder ACTION_STATION_PROVIDE_ITEMS = h("triggers/action_station_provide_items");
    public static final SpriteHolder ACTION_STATION_REQUEST_ITEMS = h("triggers/action_station_request_items");
    public static final SpriteHolder ACTION_STATION_MACHINE_REQUEST = h("triggers/action_station_machine_request");

    public static final SpriteHolder TRIGGER_ROBOT_SLEEP = h("triggers/trigger_robot_sleep");
    public static final SpriteHolder TRIGGER_ROBOT_IN_STATION = h("triggers/trigger_robot_in_station");
    public static final SpriteHolder TRIGGER_ROBOT_LINKED = h("triggers/trigger_robot_linked");
    public static final SpriteHolder TRIGGER_ROBOT_RESERVED = h("triggers/trigger_robot_reserved");

    private static SpriteHolder h(String path) {
        return SpriteHolderRegistry.getHolder("buildcraftunofficial:" + path);
    }
}

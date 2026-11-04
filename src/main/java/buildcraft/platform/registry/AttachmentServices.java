/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.registry;

/** The loader half of {@link BCAttachmentRegistrar#create}. */
public interface AttachmentServices {
    /** A new, empty attachment registrar naming its attachments in {@code namespace}. */
    BCAttachmentRegistrar create(String namespace);
}

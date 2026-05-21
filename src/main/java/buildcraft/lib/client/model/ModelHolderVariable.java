/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.model;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.gson.JsonParseException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;

import buildcraft.api.core.BCLog;

import buildcraft.lib.client.model.ModelUtil.TexturedFace;
import buildcraft.lib.client.model.json.JsonTexture;
import buildcraft.lib.client.model.json.JsonVariableModel;
import buildcraft.lib.expression.FunctionContext;
import buildcraft.lib.expression.node.value.ITickableNode;

/** Holds a model that can be changed by variables. Models are defined in this way by firstly creating a
 * {@link FunctionContext}, and then defining all of the variables with FunctionContext.getOrAddX(). It is recommended
 * that you define all models inside of static initializer block. <br>
 * The json model definition of a variable model matches the vanilla format, except that any of the static numbers may
 * be replaced with an expression, that may use any of the variables you have defined. */
public class ModelHolderVariable extends ModelHolder {
    public final Map<String, TextureAtlasSprite> customSprites = new HashMap<>();
    private final FunctionContext context;
    private JsonVariableModel rawModel;
    private boolean unseen = true;
    private boolean loadAttempted = false;
    /** Bumped every time {@link #onTextureStitchPre} reloads {@link #rawModel}. Callers that cache baked quads keyed
     * off this model store the generation they baked at and treat a mismatch as a forced re-bake, because a resource
     * reload re-creates the sprites the cached quads' UVs were bound to. */
    private int reloadGeneration = 0;

    public ModelHolderVariable(String modelLocation, FunctionContext context) {
        super(modelLocation);
        this.context = context;
    }

    @Override
    public boolean hasBakedQuads() {
        return rawModel != null;
    }

    @Override
    protected void onTextureStitchPre(Set<Identifier> toRegisterSprites) {
        rawModel = null;
        failReason = null;
        loadAttempted = false;
        reloadGeneration++;

        loadModelFromDisk();
        if (rawModel != null) {
            rawModel.onTextureStitchPre(modelLocation, toRegisterSprites);
        }
    }

    /** @return A counter incremented on every resource reload (atlas restitch). Used to invalidate quad caches that
     *          hold sprites/UVs bound to a now-discarded atlas. */
    public int getReloadGeneration() {
        return reloadGeneration;
    }

    /** Lazily loads the model from disk if it hasn't been loaded yet. */
    private void ensureLoaded() {
        if (rawModel == null && !loadAttempted) {
            loadModelFromDisk();
        }
    }

    private void loadModelFromDisk() {
        loadAttempted = true;
        try {
            rawModel = JsonVariableModel.deserialize(modelLocation, context);
            BCLog.logger.info("[lib.model.holder] Successfully loaded variable model " + modelLocation);
        } catch (JsonParseException jse) {
            rawModel = null;
            failReason = "The model had errors: " + jse.getMessage();
            BCLog.logger.warn("[lib.model.holder] Failed to load the model " + modelLocation + " because ", jse);
        } catch (IOException io) {
            rawModel = null;
            failReason = "The model did not exist in any resource pack: " + io.getMessage();
            BCLog.logger.warn("[lib.model.holder] Failed to load the model " + modelLocation + " because ", io);
        }
    }

    @Override
    protected void onModelBake() {
        // NO-OP: we bake every time get{Cutout/Translucent}Quads is called as this is a variable model
    }

    private TexturedFace lookupTexture(String lookup) {
        int attempts = 0;
        JsonTexture texture = new JsonTexture(lookup);
        TextureAtlasSprite sprite;
        while (texture.location.startsWith("#") && attempts < 10) {
            JsonTexture tex = rawModel.textures.get(texture.location);
            if (tex == null) break;
            else texture = texture.inParent(tex);
            attempts++;
        }
        lookup = texture.location;
        TextureAtlas atlas = (TextureAtlas) Minecraft.getInstance()
                .getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        if (lookup.startsWith("~")) {
            sprite = customSprites.get(lookup.substring(1));
            if (sprite == null) {
                sprite = atlas.getSprite(MissingTextureAtlasSprite.getLocation());
            }
        } else {
            sprite = atlas.getSprite(Identifier.parse(lookup));
        }
        TexturedFace face = new TexturedFace();
        face.sprite = sprite;
        face.faceData = texture.faceData;
        return face;
    }

    private void printNoModelWarning() {
        if (unseen) {
            unseen = false;
            String warnText = "[lib.model.holder] Tried to use the model " + modelLocation + " but it failed to load!";
            if (failReason != null) {
                warnText += " Reason: " + failReason;
            }
            if (ModelHolderRegistry.DEBUG) {
                BCLog.logger.warn(warnText, new Throwable());
            } else {
                BCLog.logger.warn(warnText);
            }
        }
    }

    @Nullable
    public JsonVariableModel getModel() {
        ensureLoaded();
        if (rawModel == null) {
            printNoModelWarning();
        }
        return rawModel;
    }

    public ITickableNode[] createTickableNodes() {
        ensureLoaded();
        if (rawModel == null) {
            printNoModelWarning();
            return new ITickableNode[0];
        }
        return rawModel.createTickableNodes();
    }

    public MutableQuad[] getCutoutQuads() {
        ensureLoaded();
        if (rawModel == null) {
            printNoModelWarning();
            return MutableQuad.EMPTY_ARRAY;
        }
        return rawModel.bakePart(rawModel.cutoutElements, this::lookupTexture);
    }

    /** Bakes a name-selected subset of the cutout elements. Used to split a variable model into independently-cached
     * groups (e.g. animated vs static engine elements) so that an unchanged group can skip its bake.
     *
     * @param names The element {@code name}s to include (or exclude — see {@code complement}).
     * @param complement {@code false} bakes only elements whose name is in {@code names}; {@code true} bakes only
     *            elements whose name is <em>not</em> in {@code names}. The two together over the same name set partition
     *            the cutout elements with no overlap and no omission.
     * @see #getCutoutQuads() */
    public MutableQuad[] getCutoutQuads(Set<String> names, boolean complement) {
        ensureLoaded();
        if (rawModel == null) {
            printNoModelWarning();
            return MutableQuad.EMPTY_ARRAY;
        }
        return rawModel.bakePart(rawModel.cutoutElements, this::lookupTexture, names, complement);
    }

    public MutableQuad[] getTranslucentQuads() {
        ensureLoaded();
        if (rawModel == null) {
            printNoModelWarning();
            return MutableQuad.EMPTY_ARRAY;
        }
        return rawModel.bakePart(rawModel.translucentElements, this::lookupTexture);
    }
}


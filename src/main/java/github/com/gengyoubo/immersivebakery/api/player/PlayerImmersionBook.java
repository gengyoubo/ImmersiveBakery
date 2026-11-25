package github.com.gengyoubo.immersivebakery.api.player;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.JsonOps;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.scene.NodeDefinition;
import com.pockyl.diegesis.api.scene.Transform;
import github.com.gengyoubo.immersivebakery.client.recipebook.RecipeBook;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The stations' recipe book in a scene before the player. The book knows stations and their nodes: here it gets a station that is
 * this scene and a node that is its place. The screen passes its input on.
 */
public final class PlayerImmersionBook {
    private static final String NODE = "book_open";
    private static final float LONGEST_FRAME = 0.1F;
    /** Of the open book, in its own pixels. */
    private static final float HALF_WIDE = 5.3F;
    private static final float HALF_DEEP = 3.9F;

    private final PlayerImmersionScreen screen;
    private final RecipeBook book;
    private final BlockPos pos;
    private final NodeDefinition definition;
    private final StationView station = proxy(StationView.class, this::station);
    private final NodeView node = proxy(NodeView.class, this::node);
    /** From the scene, in pixels with its front away from the player's back as stations have it, to the book. */
    private final Matrix4f place = new Matrix4f();
    private final Vector3f moved = new Vector3f();
    private final Vector3f turned = new Vector3f();
    private float scale = 1;
    private boolean visible;
    private Component tooltip = Component.empty();
    private double mouseX;
    private double mouseY;
    private long lastFrame = Util.getMillis();

    /** {@code at} in the scene; {@code turn} is how far the book is turned about x to face the player, degrees. */
    public PlayerImmersionBook(PlayerImmersionScreen screen, RecipeBook.Source source, Vector3f at, float turn, float size) {
        this.screen = screen;
        book = new RecipeBook(source);
        pos = Minecraft.getInstance().player == null ? BlockPos.ZERO : Minecraft.getInstance().player.blockPosition();
        JsonObject json = new JsonObject();
        json.addProperty("id", NODE);
        json.add("pos", array(-16 * at.x, 16 * at.y, -16 * at.z));
        json.add("rotation", array(turn, 0, 0));
        json.addProperty("scale", size);
        definition = NodeDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(false, message -> { });
        book.opened(station);
    }

    private static JsonArray array(float... values) {
        JsonArray array = new JsonArray();
        for (float value : values) {
            array.add(value);
        }
        return array;
    }

    // ---- What the screen passes on

    public void render(PlayerImmersionScreen.Scene scene) {
        long now = Util.getMillis();
        float seconds = Math.min(LONGEST_FRAME, (now - lastFrame) / 1000.0F);
        lastFrame = now;
        Minecraft minecraft = Minecraft.getInstance();
        mouseX = minecraft.mouseHandler.xpos() * screen.width / Math.max(1, minecraft.getWindow().getScreenWidth());
        mouseY = minecraft.mouseHandler.ypos() * screen.height / Math.max(1, minecraft.getWindow().getScreenHeight());
        // Where it is from the last frame on.
        place();
        book.frame(station, seconds);
        place();
        if (!visible) {
            return;
        }
        PoseStack pose = scene.pose();
        pose.pushPose();
        pose.scale(1 / 16.0F, 1 / 16.0F, 1 / 16.0F);
        pose.mulPose(new Quaternionf().rotationY((float) Math.PI));
        pose.mulPoseMatrix(place);
        // Scaled evenly, the normals are as they were turned.
        pose.last().normal().mul(place.getNormalizedRotation(new Quaternionf()).get(new Matrix3f()));
        book.paint(pose, scene.buffers(), scene.light());
        pose.popPose();
    }

    public void renderTooltip(GuiGraphics graphics, int x, int y) {
        if (isHovered() && !tooltip.getString().isEmpty()) {
            graphics.renderTooltip(Minecraft.getInstance().font, tooltip, x, y);
        }
    }

    public boolean mouseClicked(int button) {
        if (!isHovered()) {
            return false;
        }
        book.click(station, button);
        return true;
    }

    public boolean mouseScrolled(double amount) {
        return isHovered() && book.scroll(amount);
    }

    public boolean keyPressed(int keyCode, int modifiers) {
        return book.key(station, keyCode, modifiers);
    }

    public boolean charTyped(char character) {
        return book.typed(station, character);
    }

    public boolean isHovered() {
        return visible && pointer(0).filter(at -> Math.abs(at.x) < HALF_WIDE && Math.abs(at.z) < HALF_DEEP).isPresent();
    }

    // ---- What the book asks for

    private void place() {
        Transform own = definition.transform();
        place.translation(new Vector3f(own.pos()).add(moved)).rotate(own.quaternion()).rotate(Transform.toQuaternion(turned, new Quaternionf()))
                .scale(own.scale().x() * scale);
    }

    /** Where the mouse points on a level of the book, in its own pixels. */
    private Optional<Vector3f> pointer(float height) {
        return screen.ray(mouseX, mouseY).map(ray -> ray.in(toPixels())).map(ray -> ray.in(new Matrix4f(place).invert())).flatMap(ray -> ray.onLevel(height));
    }

    private static Matrix4f toPixels() {
        return new Matrix4f().rotationY((float) Math.PI).scale(16);
    }

    private Object station(Method method, Object[] arguments) {
        return switch (method.getName()) {
            case "node" -> NODE.equals(arguments[0]) ? Optional.of(node) : Optional.empty();
            case "pos" -> pos;
            case "level" -> Minecraft.getInstance().level;
            case "isFocused", "isInUse" -> Minecraft.getInstance().screen == screen;
            case "pointerOn" -> pointer((Float) arguments[1]);
            case "screenOn" -> screen.ray((Float) arguments[0] * screen.width, (Float) arguments[1] * screen.height)
                    .map(ray -> ray.in(toPixels())).flatMap(ray -> ray.onLevel((Float) arguments[2]));
            default -> null;
        };
    }

    private Object node(Method method, Object[] arguments) {
        switch (method.getName()) {
            case "setVisible" -> visible = (Boolean) arguments[0];
            case "moveTo" -> moved.set((Float) arguments[0], (Float) arguments[1], (Float) arguments[2]);
            case "rotateTo" -> turned.set((Float) arguments[0], (Float) arguments[1], (Float) arguments[2]);
            case "scaleTo" -> scale = (Float) arguments[0];
            case "setTooltip" -> tooltip = (Component) arguments[0];
            default -> { }
        }
        return switch (method.getName()) {
            case "id" -> NODE;
            case "definition" -> definition;
            case "isVisible" -> visible;
            case "isHovered" -> isHovered();
            case "sceneMatrix" -> new Matrix4f(place);
            default -> null;
        };
    }

    private interface Answers {
        Object answer(Method method, Object[] arguments);
    }

    /** What is not asked for by the book answers as a station that knows nothing of it. */
    private static <T> T proxy(Class<T> type, Answers answers) {
        InvocationHandler handler = (proxy, method, arguments) -> {
            Object answer = answers.answer(method, arguments);
            if (answer != null) {
                return answer;
            }
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, arguments);
            }
            Class<?> wanted = method.getReturnType();
            return wanted == boolean.class ? (Object) false : wanted == float.class ? (Object) 0.0F : wanted == int.class ? (Object) 0
                    : wanted == Optional.class ? Optional.empty() : null;
        };
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}

package dev.overflight.forge17;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import dev.overflight.core.config.OverflightConfig;

/** Settings screen behind the Config button in the mod list: a preset button, yes/no buttons and number fields. */
public class ConfigScreen extends GuiScreen {

    private static final String[] PRESETS = {"realistic", "fancy", "chemtrail", "custom"};
    private static final int ID_DONE = 100;
    private static final int ID_CANCEL = 101;
    private static final int ID_PRESET = 102;
    private static final int ROW = 24;

    private final GuiScreen parent;
    private final List<String> keys = new ArrayList<String>();
    private final List<GuiTextField> fields = new ArrayList<GuiTextField>();
    private final List<String> original = new ArrayList<String>();
    private final List<Boolean> flags = new ArrayList<Boolean>();
    private String preset;
    private String originalPreset;
    private String message = "";

    public ConfigScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        fields.clear();
        keys.clear();
        original.clear();
        flags.clear();
        OverflightConfig c = SkyRenderer.get().config();
        if (preset == null) {
            preset = c.preset;
            originalPreset = c.preset;
        }
        int left = width / 2 - 155;
        int top = 40;
        buttonList.add(new GuiButton(ID_PRESET, left, top - 4, 150, 20, "Look preset: " + preset));
        int i = 0;
        for (Map.Entry<String, Class<?>> e : ConfigOptions.TYPES.entrySet()) {
            String key = e.getKey();
            Object value = ConfigOptions.read(c, key);
            int col = i % 2;
            int row = i / 2 + 1;
            int x = left + col * 160;
            int y = top + row * ROW;
            keys.add(key);
            original.add(String.valueOf(value));
            if (e.getValue() == Boolean.class) {
                flags.add((Boolean) value);
                fields.add(null);
                buttonList.add(new GuiButton(i, x, y - 4, 150, 20, label(key, (Boolean) value)));
            } else {
                flags.add(null);
                GuiTextField field = new GuiTextField(fontRendererObj, x + 100, y - 4, 50, 20);
                field.setMaxStringLength(8);
                field.setText(String.valueOf(value));
                fields.add(field);
            }
            i++;
        }
        int bottom = top + ((i + 1) / 2 + 2) * ROW;
        buttonList.add(new GuiButton(ID_DONE, width / 2 - 155, bottom, 150, 20, "Done"));
        buttonList.add(new GuiButton(ID_CANCEL, width / 2 + 5, bottom, 150, 20, "Cancel"));
    }

    /** For the CI run: types a value into the field of one option, presses Done, reports the stored value. */
    String selfTest(String key, String text) {
        int at = keys.indexOf(key);
        fields.get(at).setText(text);
        actionPerformed(new GuiButton(ID_DONE, 0, 0, 1, 1, "Done"));
        return key + "=" + ConfigOptions.read(SkyRenderer.get().config(), key) + " message=" + message;
    }

    private static String label(String key, boolean on) {
        return key + ": " + (on ? "yes" : "no");
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == ID_CANCEL) {
            mc.displayGuiScreen(parent);
        } else if (button.id == ID_DONE) {
            if (apply()) {
                mc.displayGuiScreen(parent);
            }
        } else if (button.id == ID_PRESET) {
            int at = java.util.Arrays.asList(PRESETS).indexOf(preset);
            preset = PRESETS[(at + 1) % PRESETS.length];
            button.displayString = "Look preset: " + preset;
        } else if (button.id >= 0 && button.id < keys.size() && flags.get(button.id) != null) {
            boolean now = !flags.get(button.id);
            flags.set(button.id, now);
            button.displayString = label(keys.get(button.id), now);
        }
    }

    /** Applies the preset first, then every option the player changed, so a preset never wipes a hand-set value. */
    private boolean apply() {
        OverflightConfig c = SkyRenderer.get().config();
        List<Object> values = new ArrayList<Object>();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            Class<?> type = ConfigOptions.TYPES.get(key);
            Object value;
            if (type == Boolean.class) {
                value = flags.get(i);
            } else {
                String text = fields.get(i).getText().trim().replace(',', '.');
                try {
                    value = type == Integer.class ? (Object) Integer.valueOf(text) : (Object) Double.valueOf(text);
                } catch (NumberFormatException ex) {
                    message = key + ": \"" + text + "\" is not a number";
                    return false;
                }
                if (value instanceof Double && (((Double) value).isNaN() || ((Double) value).isInfinite())) {
                    message = key + ": not a finite number";
                    return false;
                }
            }
            values.add(value);
        }
        if (!preset.equals(originalPreset)) {
            c.preset = preset;
            c.applyPreset();
        }
        for (int i = 0; i < keys.size(); i++) {
            if (!String.valueOf(values.get(i)).equals(original.get(i))) {
                ConfigOptions.write(c, keys.get(i), values.get(i));
            }
        }
        c.sanitise();
        SkyRenderer.get().applyConfig(c);
        ConfigIo.save(c);
        return true;
    }

    @Override
    protected void keyTyped(char typed, int code) {
        for (GuiTextField field : fields) {
            if (field != null && field.isFocused() && field.textboxKeyTyped(typed, code)) {
                return;
            }
        }
        if (code == 1) {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        for (GuiTextField field : fields) {
            if (field != null) {
                field.mouseClicked(x, y, button);
            }
        }
    }

    @Override
    public void updateScreen() {
        for (GuiTextField field : fields) {
            if (field != null) {
                field.updateCursorCounter();
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partial) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Overflight settings", width / 2, 15, 0xFFFFFF);
        int left = width / 2 - 155;
        for (int i = 0; i < keys.size(); i++) {
            GuiTextField field = fields.get(i);
            if (field != null) {
                int x = left + (i % 2) * 160;
                int y = 40 + (i / 2 + 1) * ROW;
                fontRendererObj.drawString(keys.get(i), x, y + 2, 0xE0E0E0);
                field.drawTextBox();
            }
        }
        if (!message.isEmpty()) {
            drawCenteredString(fontRendererObj, message, width / 2, height - 20, 0xFF5555);
        }
        super.drawScreen(mouseX, mouseY, partial);
    }
}

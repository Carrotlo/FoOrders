package me.foesio.core.dialog;

public class DialogButton {
    public static final String SAVE_ICON = "floppy_disk";
    public static final String CONFIRM_ICON_COLOR = "§a";

    DialogButton() {}

    public static DialogButton search(String label, String tooltip, int width) {
        return new DialogButton();
    }

    public static DialogButton cancel(String label, String tooltip, int width) {
        return new DialogButton();
    }

    public static DialogButton icon(String iconName, String label, String tooltip, int width) {
        return new DialogButton();
    }

    public String label() {
        return "";
    }

    public String labelWithIcon() {
        return "";
    }

    public String tooltip() {
        return "";
    }

    public int width() {
        return 0;
    }

    public String icon() {
        return "";
    }
}

package me.foesio.core.dialog;

import java.util.List;

public class TextDialogRequest {
    private final String title;
    private final List<String> body;
    private final String fieldLabel;
    private final String initialValue;
    private final String placeholder;
    private final DialogButton submitButton;
    private final DialogButton cancelButton;
    private final int bodyWidth;
    private final int inputWidth;
    private final int maxLength;
    private final boolean labelVisible;
    private final boolean canCloseWithEscape;
    private final boolean pause;

    public TextDialogRequest(
        String title,
        List<String> body,
        String fieldLabel,
        String initialValue,
        String placeholder,
        DialogButton submitButton,
        DialogButton cancelButton,
        int bodyWidth,
        int inputWidth,
        int maxLength,
        boolean labelVisible,
        boolean canCloseWithEscape,
        boolean pause
    ) {
        this.title = title;
        this.body = body;
        this.fieldLabel = fieldLabel;
        this.initialValue = initialValue;
        this.placeholder = placeholder;
        this.submitButton = submitButton;
        this.cancelButton = cancelButton;
        this.bodyWidth = bodyWidth;
        this.inputWidth = inputWidth;
        this.maxLength = maxLength;
        this.labelVisible = labelVisible;
        this.canCloseWithEscape = canCloseWithEscape;
        this.pause = pause;
    }

    public static TextDialogRequest number(List<String> body, String initialValue, String placeholder) {
        return new TextDialogRequest("", body, "", initialValue, placeholder, null, null, 200, 200, 100, false, true, false);
    }

    public String title() { return title; }
    public List<String> body() { return body; }
    public String fieldLabel() { return fieldLabel; }
    public String initialValue() { return initialValue; }
    public String placeholder() { return placeholder; }
    public DialogButton submitButton() { return submitButton; }
    public DialogButton cancelButton() { return cancelButton; }
    public int bodyWidth() { return bodyWidth; }
    public int inputWidth() { return inputWidth; }
    public int maxLength() { return maxLength; }
    public boolean labelVisible() { return labelVisible; }
    public boolean canCloseWithEscape() { return canCloseWithEscape; }
    public boolean pause() { return pause; }
}

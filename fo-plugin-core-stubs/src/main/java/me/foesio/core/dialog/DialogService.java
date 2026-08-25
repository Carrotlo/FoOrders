package me.foesio.core.dialog;

public class DialogService {
    public NativeDialogSupport support() {
        return NativeDialogSupport.detect(null, null);
    }
}

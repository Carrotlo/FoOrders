package me.foesio.core.reload;

public class FoReloadResult {
    public boolean successful() {
        return true;
    }

    public String failedStep() {
        return null;
    }

    public String errorMessage() {
        return null;
    }

    public Throwable error() {
        return null;
    }
}

package me.foesio.core.gui;

public class EntryBrowserClick {
    public Action action() {
        return Action.NONE;
    }

    public String entryId() {
        return null;
    }

    public enum Action {
        ENTRY,
        ADD,
        SEARCH,
        CLEAR_SEARCH,
        PREVIOUS_PAGE,
        NEXT_PAGE,
        BACK,
        NONE
    }
}

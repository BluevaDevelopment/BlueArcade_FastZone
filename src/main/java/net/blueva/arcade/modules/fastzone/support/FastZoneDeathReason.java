package net.blueva.arcade.modules.fastzone.support;

public enum FastZoneDeathReason {
    DEATH_BLOCK("messages.deaths.death_block"),
    VOID("messages.deaths.void"),
    WALL("messages.deaths.wall");

    private final String messagePath;

    FastZoneDeathReason(String messagePath) {
        this.messagePath = messagePath;
    }

    public String getMessagePath() {
        return messagePath;
    }
}

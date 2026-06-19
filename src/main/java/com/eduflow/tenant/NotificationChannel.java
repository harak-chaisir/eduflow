package com.eduflow.tenant;

/**
 * Channels a tenant can enable for outbound notifications. Stored on
 * {@link TenantSettings#getDefaultNotificationChannels()} as a comma-separated list of
 * {@link #name()} values (e.g. {@code "EMAIL,SMS"}).
 */
public enum NotificationChannel {

    EMAIL("Email"),
    SMS("SMS"),
    WHATSAPP("WhatsApp");

    private final String label;

    NotificationChannel(String label) {
        this.label = label;
    }

    /** Human-readable label for UI display. */
    public String label() {
        return label;
    }

    /** Returns {@code true} if {@code value} matches a known channel (case-insensitive). */
    public static boolean isValid(String value) {
        if (value == null) {
            return false;
        }
        for (NotificationChannel c : values()) {
            if (c.name().equalsIgnoreCase(value.trim())) {
                return true;
            }
        }
        return false;
    }
}

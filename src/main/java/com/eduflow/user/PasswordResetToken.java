package com.eduflow.user;

import com.eduflow.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A single-use, time-bound token that lets a user set or reset their password
 * (used for the tenant-admin invite / activation flow).
 */
@Entity
@Table(name = "password_reset_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken extends BaseEntity {

    /** The user this token belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Opaque, URL-safe token value. */
    @Column(name = "token", nullable = false, unique = true, length = 255)
    private String token;

    /** When the token stops being valid. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** When the token was consumed; {@code null} while still valid. */
    @Column(name = "used_at")
    private Instant usedAt;

    /** True if the token is unused and not expired. */
    public boolean isValid() {
        return usedAt == null && Instant.now().isBefore(expiresAt);
    }
}

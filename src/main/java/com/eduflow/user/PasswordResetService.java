package com.eduflow.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Issues and consumes single-use password-set/reset tokens.
 *
 * <p>Used by the tenant-admin invite flow: a freshly invited admin is created in
 * {@code PENDING_VERIFICATION}; consuming their token sets the chosen password and
 * activates the account so they can sign in.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    /** How long an issued token stays valid. */
    private static final Duration TOKEN_TTL = Duration.ofDays(7);

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    /** Creates and persists a new token for the user, returning its opaque value. */
    @Transactional
    public String createToken(User user) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .token(value)
                .expiresAt(Instant.now().plus(TOKEN_TTL))
                .build();
        tokenRepository.save(token);
        return value;
    }

    /** Returns the email associated with a valid token, or empty if invalid/expired/used. */
    @Transactional(readOnly = true)
    public Optional<String> emailForValidToken(String token) {
        return tokenRepository.findByToken(token)
                .filter(PasswordResetToken::isValid)
                .map(t -> t.getUser().getEmail());
    }

    /**
     * Consumes a token: sets the user's password, activates the account, and marks
     * the token used. Returns {@code false} if the token is missing/expired/used.
     */
    @Transactional
    public boolean resetPassword(String token, String rawPassword) {
        Optional<PasswordResetToken> found = tokenRepository.findByToken(token)
                .filter(PasswordResetToken::isValid);
        if (found.isEmpty()) {
            return false;
        }

        PasswordResetToken prt = found.get();
        User user = prt.getUser();
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        userRepository.save(user);

        prt.setUsedAt(Instant.now());
        tokenRepository.save(prt);

        log.info("Password set for user {} (tenant {}) via reset token", user.getId(),
                user.getTenant() != null ? user.getTenant().getId() : null);
        return true;
    }
}

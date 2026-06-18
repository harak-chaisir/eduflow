package com.eduflow.security;

import com.eduflow.user.User;
import com.eduflow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Spring Security {@link UserDetailsService} implementation that loads users from
 * the {@code users} table.
 *
 * <p><b>Multi-tenant note:</b> the same email address may exist in more than one
 * tenant. At login, we search all tenants and prefer the first {@code ACTIVE} account.
 * Future improvements may add an explicit tenant-selection step to the login form.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EduFlowUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Loads the user by email address (case-insensitive). The roles and tenant are
     * eagerly fetched in a single query to avoid lazy-loading issues after the
     * Hibernate session closes.
     *
     * @param email the value submitted in the login form's {@code email} field
     * @return a fully populated {@link EduFlowUserDetails}
     * @throws UsernameNotFoundException if no active account exists for this email
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(@NonNull String email) throws UsernameNotFoundException {
        List<User> users = userRepository.findAllByEmailWithRoles(email);

        if (users.isEmpty()) {
            log.debug("Login attempt for unknown email: {}", email);
            throw new UsernameNotFoundException("No account found for email: " + email);
        }

        // Prefer the first ACTIVE user; fall back to the first record found
        User user = users.stream()
                .filter(u -> com.eduflow.user.UserStatus.ACTIVE == u.getStatus())
                .findFirst()
                .orElse(users.getFirst());

        log.debug("Loaded user {} (tenant={}, status={})", user.getEmail(),
                user.getTenant().getId(), user.getStatus());

        return new EduFlowUserDetails(user);
    }
}




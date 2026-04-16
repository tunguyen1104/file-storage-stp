package com.filestorage.adapter.in.rest.security;

import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security principal that carries tenant-scoped identity.
 * <p>
 * Two construction paths:
 * <ul>
 *   <li><b>Claims-based</b> (4-arg): populated by {@code JwtAuthenticationFilter}
 *       after decoding JWT claims — no password, always enabled.</li>
 *   <li><b>Entity-based</b> (6-arg): populated by {@code CustomUserDetailsService}
 *       for {@code DaoAuthenticationProvider} during login — carries password hash.</li>
 * </ul>
 */
@Getter
public class SecurityUser implements UserDetails {

    private final String userId;
    private final String tenantId;
    private final String role;
    private final String email;
    private final String passwordHash;
    private final boolean isActive;

    /**
     * Claims-based constructor — used by JwtAuthenticationFilter.
     * No password needed (JWT is already verified).
     */
    public SecurityUser(String userId, String tenantId, String role, String email) {
        this(userId, tenantId, role, email, null, true);
    }

    /**
     * Entity-based constructor — used by CustomUserDetailsService.
     * Carries password hash for DaoAuthenticationProvider verification.
     */
    public SecurityUser(String userId, String tenantId, String role, String email,
                        String passwordHash, boolean isActive) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.role = role;
        this.email = email;
        this.passwordHash = passwordHash;
        this.isActive = isActive;
    }

    @Override
    public @NonNull Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public @NonNull String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return UserDetails.super.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return UserDetails.super.isAccountNonLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return UserDetails.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return isActive;
    }
}

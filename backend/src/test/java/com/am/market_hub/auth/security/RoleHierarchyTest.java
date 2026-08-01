package com.am.market_hub.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class RoleHierarchyTest {

    private final RoleHierarchy roleHierarchy = new SecurityConfig().roleHierarchy();

    @Test
    void adminReachesModeratorAndTraderAuthorities() {
        Collection<? extends GrantedAuthority> authorities =
                roleHierarchy.getReachableGrantedAuthorities(
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_ADMIN", "ROLE_MODERATOR", "ROLE_TRADER");
    }

    @Test
    void traderDoesNotReachAdminOrModeratorAuthorities() {
        Collection<? extends GrantedAuthority> authorities =
                roleHierarchy.getReachableGrantedAuthorities(
                        List.of(new SimpleGrantedAuthority("ROLE_TRADER")));

        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_TRADER");
    }
}

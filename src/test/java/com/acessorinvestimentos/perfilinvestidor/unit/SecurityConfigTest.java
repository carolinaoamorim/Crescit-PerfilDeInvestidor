package com.acessorinvestimentos.perfilinvestidor.unit;

import com.acessorinvestimentos.perfilinvestidor.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    @Test
    void jwtAuthenticationConverter_readsAuth0PermissionsAndScopes() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("auth0|123")
                .claim("scope", "openid read:profile")
                .claim("permissions", List.of("read:profile", "write:profile"))
                .build();

        var authentication = new SecurityConfig().jwtAuthenticationConverter().convert(jwt);

        assertThat(authentication.getName()).isEqualTo("auth0|123");
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("SCOPE_openid", SecurityConfig.READ_PROFILE, SecurityConfig.WRITE_PROFILE);
    }
}

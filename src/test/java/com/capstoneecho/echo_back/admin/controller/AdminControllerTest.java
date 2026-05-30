package com.capstoneecho.echo_back.admin.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstoneecho.echo_back.global.jwt.JwtProvider;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import com.capstoneecho.echo_back.support.AbstractControllerIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminControllerTest extends AbstractControllerIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("GET /api/admin/me 토큰 없음 → 401 UNAUTHORIZED")
    void noTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/admin/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/admin/me USER 권한 토큰 → 403 FORBIDDEN")
    void userRoleReturns403() throws Exception {
        User user = userRepository.save(User.signup(
                "normal", "normal@test.com", passwordEncoder.encode("Password1!"), "Normal"));
        String token = jwtProvider.issue(user.getId(),
                Map.of("username", "normal", "email", "normal@test.com", "role", "USER"));

        mockMvc.perform(get("/api/admin/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/admin/me ADMIN 권한 토큰 → 200 + role ADMIN")
    void adminRoleReturnsProfile() throws Exception {
        User admin = User.signup(
                "boss", "boss@test.com", passwordEncoder.encode("Password1!"), "Boss");
        admin.promoteToAdmin();
        admin = userRepository.save(admin);
        String token = jwtProvider.issue(admin.getId(),
                Map.of("username", "boss", "email", "boss@test.com", "role", "ADMIN"));

        mockMvc.perform(get("/api/admin/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("boss"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }
}

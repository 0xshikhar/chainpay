package com.chainpay.core.security.api;

import com.chainpay.core.security.api.dto.AuthResponse;
import com.chainpay.core.security.api.dto.LoginRequest;
import com.chainpay.core.security.api.dto.RegisterRequest;
import com.chainpay.core.security.jwt.JwtTokenProvider;
import com.chainpay.core.security.user.User;
import com.chainpay.core.security.user.UserPrincipal;
import com.chainpay.core.security.user.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );

        String accessToken = tokenProvider.generateAccessToken(authentication);
        String refreshToken = tokenProvider.generateRefreshToken(authentication);
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        String role = principal.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("VIEWER");

        return ResponseEntity.ok(AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .username(principal.getUsername())
                .role(role)
                .build());
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest registerRequest) {
        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            return ResponseEntity.badRequest().build();
        }

        User user = User.builder()
                .username(registerRequest.getUsername())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .email(registerRequest.getEmail())
                .role(registerRequest.getRole())
                .enabled(true)
                .build();

        userRepository.save(user);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(registerRequest.getUsername());
        loginRequest.setPassword(registerRequest.getPassword());

        return login(loginRequest);
    }
}

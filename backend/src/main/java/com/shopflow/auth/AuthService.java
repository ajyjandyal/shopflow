package com.shopflow.auth;

import com.shopflow.auth.dto.AccountType;
import com.shopflow.auth.dto.AuthResponse;
import com.shopflow.auth.dto.LoginRequest;
import com.shopflow.auth.dto.RegisterRequest;
import com.shopflow.common.exception.ConflictException;
import com.shopflow.common.exception.UnauthorizedException;
import com.shopflow.security.JwtService;
import com.shopflow.user.Role;
import com.shopflow.user.User;
import com.shopflow.user.UserRepository;
import com.shopflow.user.dto.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * Hash compared against when the email does not exist, so a login for an unknown
     * email takes about as long as one for a known email. Without this, response time
     * reveals which emails are registered (a timing side channel).
     */
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyPasswordHash = passwordEncoder.encode("timing-attack-mitigation-placeholder");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = User.normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        Role role = request.accountType() == AccountType.SELLER ? Role.SELLER : Role.USER;
        User user = new User(email, passwordEncoder.encode(request.password()), request.fullName().trim(), role);
        User saved = userRepository.save(user);
        log.info("Registered user id={} role={}", saved.getId(), saved.getRole());
        return toAuthResponse(saved);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Optional<User> user = userRepository.findByEmail(User.normalizeEmail(request.email()));
        String hashToCheck = user.map(User::getPasswordHash).orElse(dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCheck);

        if (user.isEmpty() || !passwordMatches) {
            // Same message for "no such user" and "wrong password": don't reveal which emails exist.
            log.info("Failed login attempt");
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        log.info("User id={} logged in", user.get().getId());
        return toAuthResponse(user.get());
    }

    private AuthResponse toAuthResponse(User user) {
        return new AuthResponse(jwtService.generateToken(user), "Bearer",
                jwtService.getExpiration().toSeconds(), UserResponse.from(user));
    }
}

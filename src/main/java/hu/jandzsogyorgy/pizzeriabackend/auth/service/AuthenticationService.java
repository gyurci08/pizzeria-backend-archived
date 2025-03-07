package hu.jandzsogyorgy.pizzeriabackend.auth.service;

import hu.jandzsogyorgy.pizzeriabackend.auth.dto.*;
import hu.jandzsogyorgy.pizzeriabackend.auth.exception.AuthenticationException;
import hu.jandzsogyorgy.pizzeriabackend.auth.util.JwtUtil;
import hu.jandzsogyorgy.pizzeriabackend.auth.util.TokenType;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public LoginResponseDto login(LoginRequestDto dto) {
        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(dto.username());
        } catch (UsernameNotFoundException e) {
            throw new AuthenticationException("Incorrect username/email or password");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(userDetails.getUsername(), dto.password())
            );
        } catch (BadCredentialsException e) {
            throw new AuthenticationException("Incorrect username/email or password");
        }

        final String accessToken = jwtUtil.createAccessToken(userDetails);
        final String refreshToken = jwtUtil.createRefreshToken(userDetails);

        return new LoginResponseDto(accessToken, refreshToken);
    }


    public RegisterResponseDto register(RegisterRequestDto dto) {
        return userDetailsService.registerUser(dto.email(), dto.username(), passwordEncoder.encode(dto.password()), "CUSTOMER");
    }


    public LoginResponseDto refreshToken(UserDetails userDetails, RefreshTokenRequestDto dto) {
        try {
            if (jwtUtil.validateToken(userDetails, TokenType.REFRESH, dto.refreshToken())) {
                String newAccessToken = jwtUtil.createAccessToken(userDetails);
                return new LoginResponseDto(newAccessToken, dto.refreshToken());
            } else {
                throw new AuthenticationException("Invalid refresh token");
            }
        } catch (ExpiredJwtException e) {
            throw new AuthenticationException("Refresh token has expired");
        }
    }


    public LogoutResponseDto logout(UserDetails userDetails, LogoutRequestDto dto) {
        if (dto.accessToken() == null || dto.refreshToken() == null || userDetails == null) {
            return new LogoutResponseDto("Authentication and tokens are required");
        }

        try {
            if (jwtUtil.validateToken(userDetails, TokenType.ACCESS, dto.accessToken())) {
                jwtUtil.invalidateToken(dto.accessToken());
                jwtUtil.invalidateToken(dto.refreshToken());
                return new LogoutResponseDto("Logged out successfully");
            } else {
                return new LogoutResponseDto("Invalid or expired token");
            }
        } catch (Exception e) {
            log.error("Error during logout", e);
            return new LogoutResponseDto("Error during logout: " + e.getMessage());
        }
    }


}

package com.w_s_backend.w_s.controllers;

import com.w_s_backend.w_s.DTOs.LoginDTO;
import com.w_s_backend.w_s.DTOs.UserRegistrationDTO;
import com.w_s_backend.w_s.models.User;

import jakarta.servlet.http.HttpServletResponse;

import com.w_s_backend.w_s.Services.JwtService;
import com.w_s_backend.w_s.Services.UserService;
import lombok.AllArgsConstructor;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/auth")
@AllArgsConstructor
public class AuthController {
    
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @PostMapping("/register")
    public ResponseEntity<User> registerUser(@RequestBody UserRegistrationDTO registrationDTO) {
        User createdUser = userService.createUser(registrationDTO);
        return ResponseEntity.ok(createdUser);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginDTO loginDTO, HttpServletResponse response) {
        User user = userService.findByUsername(loginDTO.username);

        if (user == null || !passwordEncoder.matches(loginDTO.password, user.getPassword())) {
            return ResponseEntity.status(401).body(Map.of("message", "Неверный логин или пароль"));
        }

        String jwtToken = jwtService.generateToken(user.getId(), user.getUsername());

        Cookie cookie = new Cookie("jwt", jwtToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);           
        cookie.setPath("/");
        cookie.setMaxAge(7 * 24 * 60 * 60); 
        response.addCookie(cookie);

        return ResponseEntity.ok(Map.of(
            "message", "Вход выполнен",
            "userId", user.getId(),
            "username", user.getUsername()
        ));
    }
    
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("jwt", null);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);          
        cookie.setPath("/");
        cookie.setMaxAge(0);              
        response.addCookie(cookie);

        return ResponseEntity.ok(Map.of("message", "Выход выполнен"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("jwt".equals(cookie.getName())) {
                    String token = cookie.getValue();
                    if (jwtService.isTokenValid(token)) {
                        Long userId = jwtService.extractUserId(token);
                        User user = userService.findById(userId);
                        return ResponseEntity.ok(Map.of(
                            "userId", user.getId(),
                            "username", user.getUsername(),
                            "email", user.getEmail()
                        ));
                    }
                }
            }
        }
        return ResponseEntity.status(401).body(Map.of("message", "Не авторизован"));
    }

}

package com.w_s_backend.w_s.controllers;

import com.w_s_backend.w_s.DTOs.UserRegistrationDTO;
import com.w_s_backend.w_s.models.User;

import org.springframework.ui.Model;

import com.w_s_backend.w_s.Services.UserService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@AllArgsConstructor
public class AuthController {
    
    private final UserService userService;
    
    @PostMapping("/register")
    public ResponseEntity<User> registerUser(@RequestBody UserRegistrationDTO registrationDTO) {
        User createdUser = userService.createUser(registrationDTO);
        return ResponseEntity.ok(createdUser);
    }

}

package com.w_s_backend.w_s.Services;

import javax.management.RuntimeErrorException;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.w_s_backend.w_s.DTOs.UserRegistrationDTO;
import com.w_s_backend.w_s.Repositories.UserRepository;
import com.w_s_backend.w_s.models.User;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public User createUser(UserRegistrationDTO registrationDTO) {
        String encodedPassword = passwordEncoder.encode(registrationDTO.getPassword());
        
        User user = new User();
        user.setUsername(registrationDTO.getUsername());
        user.setEmail(registrationDTO.getEmail());
        user.setPassword(encodedPassword);
        
        return userRepository.save(user);
    }
    
    public User findById(Long userId) {
        if(userId == null) throw new RuntimeErrorException(null);
        
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public User findByUsername(String username){
        return userRepository.findByUsername(username)
            .orElseThrow(() ->  new RuntimeException("User not found"));
    }
}

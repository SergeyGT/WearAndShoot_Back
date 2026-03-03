package com.w_s_backend.w_s.Configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.w_s_backend.w_s.Repositories.UserRepository;
import com.w_s_backend.w_s.models.User;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean 
    public UserDetailsService userDetailsService(UserRepository userRepo){
        return username -> {
            User user = userRepo.findByUsername(username);
            if(user!=null) return user;

            throw new UsernameNotFoundException(username + " not found");
        };
    }

    
    public interface UserDetailsService {
        UserDetails loadUserByUsername(String username) throws 
            UsernameNotFoundException;
        
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authz -> authz
            .requestMatchers("/cloth").hasRole("USER")
            .anyRequest().permitAll()
        )
        .formLogin(form->form
            .loginPage("/login")
            .loginProcessingUrl("/login")          
            .defaultSuccessUrl("/cloth", true)      
            .failureUrl("/login?error=true")        
            .permitAll()
        )
        ;

        return http.build();
    }
}
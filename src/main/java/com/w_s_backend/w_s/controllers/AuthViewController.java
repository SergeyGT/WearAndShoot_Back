package com.w_s_backend.w_s.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.w_s_backend.w_s.DTOs.UserRegistrationDTO;

import lombok.AllArgsConstructor;


@Controller
@AllArgsConstructor
@RequestMapping("/auth")
public class AuthViewController {

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("registrationDTO", new UserRegistrationDTO());
        return "registration";   
    }

}
package com.w_s_backend.w_s.Repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.w_s_backend.w_s.models.User;

public interface UserRepository extends JpaRepository<User, Long>{
    Optional<User> findByUsername(String username);
}

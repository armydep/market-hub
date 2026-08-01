package com.am.market_hub.user.repository;

import java.util.Optional;

import com.am.market_hub.user.domain.Role;
import com.am.market_hub.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByRole(Role role);
}

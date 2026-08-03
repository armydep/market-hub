package com.am.market_hub.user.repository;

import java.util.Optional;

import com.am.market_hub.user.domain.Role;
import com.am.market_hub.user.domain.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Callers always pass an already-lowercased email (see AuthService/AdminSeeder). */
    Optional<User> findByEmail(String email);

    boolean existsByRole(Role role);

    /**
     * Row-locked read, used only by admin block/unblock. Without this, two
     * concurrent requests for the same target could both read the
     * pre-change state, both pass the idempotency check, and both write an
     * audit row for what should be a single transition. The second caller's
     * SELECT blocks until the first transaction commits, then sees the
     * already-changed state and correctly no-ops.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}

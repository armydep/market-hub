package com.am.market_hub.user.repository;

import com.am.market_hub.user.domain.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;

/** {@code UserPreference}'s {@code @Id} is the user id itself, so {@code findById}/{@code save} already scope by owner. */
public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {
}

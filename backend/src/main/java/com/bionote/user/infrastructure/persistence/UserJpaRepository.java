package com.bionote.user.infrastructure.persistence;

import com.bionote.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface UserJpaRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailNormalized(String email);
    boolean existsByEmailNormalized(String email);
}

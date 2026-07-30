package com.bionote.user.infrastructure.persistence;

import com.bionote.user.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserJpaRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailNormalized(String email);

    boolean existsByEmailNormalized(String email);
}

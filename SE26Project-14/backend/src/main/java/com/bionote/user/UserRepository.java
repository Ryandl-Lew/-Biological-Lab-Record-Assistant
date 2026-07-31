package com.bionote.user;

import java.util.Optional;
import java.util.UUID;

/** Application persistence port for user accounts. */
public interface UserRepository {
    Optional<User> findById(UUID id);

    Optional<User> findByEmailNormalized(String email);

    boolean existsByEmailNormalized(String email);

    boolean existsById(UUID id);

    User save(User user);

    User saveAndFlush(User user);

    /** Retained for integration-test fixture cleanup; not used by business services. */
    void deleteAll();
}

package com.bionote.user.infrastructure.persistence;

import com.bionote.user.User;
import com.bionote.user.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaUserRepositoryAdapter implements UserRepository {
    private final UserJpaRepository repository;

    public JpaUserRepositoryAdapter(UserJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<User> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public Optional<User> findByEmailNormalized(String email) {
        return repository.findByEmailNormalized(email);
    }

    @Override
    public boolean existsByEmailNormalized(String email) {
        return repository.existsByEmailNormalized(email);
    }

    @Override
    public boolean existsById(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public User save(User user) {
        return repository.save(user);
    }

    @Override
    public User saveAndFlush(User user) {
        return repository.saveAndFlush(user);
    }

    @Override
    public void deleteAll() {
        repository.deleteAll();
    }
}

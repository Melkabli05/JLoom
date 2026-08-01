package {{package}}.user;

import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
class InMemoryUserRepository implements UserRepository {

    private final Map<UUID, User> byId = new ConcurrentHashMap<>();
    private final Map<String, UUID> emailIndex = new ConcurrentHashMap<>();

    @Override
    public synchronized User save(User user) {
        UUID existingId = emailIndex.get(user.email().toLowerCase());
        if (existingId != null) {
            throw new IllegalArgumentException("email already registered: " + user.email());
        }
        byId.put(user.id(), user);
        emailIndex.put(user.email().toLowerCase(), user.id());
        return user;
    }

    @Override
    public Optional<User> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        if (email == null) return Optional.empty();
        UUID id = emailIndex.get(email.toLowerCase());
        return id == null ? Optional.empty() : findById(id);
    }
}
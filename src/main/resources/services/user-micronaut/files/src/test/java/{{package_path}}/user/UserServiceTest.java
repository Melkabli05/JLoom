package {{package}}.user;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserServiceTest {

    @Test
    void repositoryStoresAndRetrievesByEmail() {
        InMemoryUserRepository repository = new InMemoryUserRepository();
        User u = new User(UUID.randomUUID(), "alice@example.com", "$2hash", Instant.now(), null);

        repository.save(u);
        assertEquals(u, repository.findById(u.id()).orElseThrow());
        assertEquals(u, repository.findByEmail("alice@example.com").orElseThrow());
        assertEquals(u, repository.findByEmail("ALICE@example.com").orElseThrow(),
                "email lookup should be case-insensitive");
    }

    @Test
    void duplicateEmailThrows() {
        InMemoryUserRepository repository = new InMemoryUserRepository();
        UUID id = UUID.randomUUID();
        repository.save(new User(id, "dup@example.com", "$2hash", Instant.now(), null));
        assertThrows(IllegalArgumentException.class, () ->
                repository.save(new User(UUID.randomUUID(), "dup@example.com", "$2hash", Instant.now(), null)));
    }

    @Test
    void recordHasAllExpectedFields() {
        User u = new User(UUID.randomUUID(), "x@y.com", "$2hash", Instant.now(), null);
        assertNotNull(u.id());
        assertEquals("x@y.com", u.email());
    }
}
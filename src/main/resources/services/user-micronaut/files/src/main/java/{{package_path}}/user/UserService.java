package {{package}}.user;

import java.util.UUID;

public interface UserService {

    UUID register(String email, String plainPassword);

    User getById(UUID id);
}
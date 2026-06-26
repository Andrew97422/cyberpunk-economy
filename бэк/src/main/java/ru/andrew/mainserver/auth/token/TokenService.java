package ru.andrew.mainserver.auth.token;

import org.springframework.stereotype.Service;
import ru.andrew.mainserver.account.entity.Account;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class TokenService {

    public String generate(Account account, Long sessionId) {
        String raw = account.getId() + ":" +
                account.getPublicName() + ":" +
                account.getRole().name() + ":" +
                sessionId + ":" +
                Instant.now().getEpochSecond() + ":" +
                UUID.randomUUID();

        return Base64.getUrlEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
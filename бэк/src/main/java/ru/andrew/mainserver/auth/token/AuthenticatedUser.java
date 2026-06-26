package ru.andrew.mainserver.auth.token;

import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;
import java.util.Set;

@Getter
@Builder
public class AuthenticatedUser implements Serializable {
    private Long accountId;
    private String publicName;
    private String role;
    private Long sessionId;
    private Set<String> authorities;
}
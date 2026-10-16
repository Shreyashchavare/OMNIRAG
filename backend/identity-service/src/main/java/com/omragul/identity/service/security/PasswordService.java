package com.omragul.identity.service.security;

public interface PasswordService {

    String hashPassword(String rawPassword);

    boolean matches(
            String rawPassword,
            String encodedPassword
    );
}
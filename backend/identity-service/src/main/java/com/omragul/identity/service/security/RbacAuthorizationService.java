package com.omragul.identity.service.security;

import java.util.Set;
import java.util.UUID;

public interface RbacAuthorizationService {

    Set<String> getUserPermissions(UUID userId);

}
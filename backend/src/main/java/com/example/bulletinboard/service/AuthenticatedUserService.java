package com.example.bulletinboard.service;
import com.example.bulletinboard.security.ActorIds;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bulletinboard.dto.auth.AuthenticatedUserResponse;
import com.example.bulletinboard.repository.ProfileRepository;
import com.example.bulletinboard.repository.UserRepository;

/** SessionのPrincipalから現在のUserを読み直し、公開可能な本人情報へ変換する。 */
@Service
public class AuthenticatedUserService {
    private final UserRepository users;
    private final ProfileRepository profiles;

    public AuthenticatedUserService(UserRepository users, ProfileRepository profiles) {
        this.users = users;
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public AuthenticatedUserResponse current(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof com.example.bulletinboard.security.AccountPrincipal)) {
            return AuthenticatedUserResponse.anonymous();
        }
        return users.findById(ActorIds.parse(authentication.getName()))
                .filter(user -> user.getAccountStatus()==com.example.bulletinboard.model.AccountStatus.ACTIVE)
                .map(user -> new AuthenticatedUserResponse(
                        true,
                        user.getId(),
                        profiles.findByUserId(user.getId()).map(profile -> profile.getId()).orElse(null),
                        user.getUsername(),
                        user.getRole()))
                .orElseGet(AuthenticatedUserResponse::anonymous);
    }
}

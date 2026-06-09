package com.jira.infrastructure.config;

import com.jira.infrastructure.persistence.entity.ProjectMemberEntity;
import com.jira.infrastructure.persistence.repository.ProjectMemberJpaRepository;
import com.jira.infrastructure.persistence.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Loads users from the DB for Spring Security authentication.
 * Also provides a helper to resolve the caller's project role,
 * used by @PreAuthorize expressions on service methods.
 */
@Service
@RequiredArgsConstructor
public class JiraUserDetailsService implements UserDetailsService {

    private final UserJpaRepository       userRepo;
    private final ProjectMemberJpaRepository memberRepo;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var user = userRepo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return User.builder()
                .username(user.getUsername())
                .password(user.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
    }

    public String resolveProjectRole(String username, UUID projectId) {
        var user = userRepo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return memberRepo.findByProjectIdAndUserId(projectId, user.getId())
                .map(ProjectMemberEntity::getRole)
                .map(Enum::name)
                .orElse("NONE");
    }
}

package com.internship.student_exam_api.security;

import com.internship.student_exam_api.entity.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Adapter class wrapping AppUser to implement Spring Security's UserDetails contract.
 * Keeps domain classes decoupled from Spring Security.
 */
public class AppUserDetails implements UserDetails {

    private final AppUser appUser;

    /**
     * Constructs a new AppUserDetails wrapping the given AppUser.
     *
     * @param appUser the database user entity
     */
    public AppUserDetails(AppUser appUser) {
        this.appUser = appUser;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(
            new SimpleGrantedAuthority("ROLE_" + appUser.getRole().name())
        );
    }

    @Override
    public String getPassword() {
        return appUser.getPassword();
    }

    @Override
    public String getUsername() {
        return appUser.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * Retrieves the role name of the user.
     *
     * @return String representation of the Role
     */
    public String getRoleName() {
        return appUser.getRole().name();
    }
}

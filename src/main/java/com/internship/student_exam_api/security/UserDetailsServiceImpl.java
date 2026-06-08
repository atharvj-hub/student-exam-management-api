package com.internship.student_exam_api.security;

import com.internship.student_exam_api.repository.AppUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of UserDetailsService to load user credentials from database.
 */
@Service
@Slf4j
public class UserDetailsServiceImpl implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    /**
     * Constructs the UserDetailsServiceImpl using dependency injection.
     *
     * @param appUserRepository the database repository for app users
     */
    public UserDetailsServiceImpl(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.debug("Loading user details for email: {}", email);
        return appUserRepository.findByEmail(email)
            .map(AppUserDetails::new)
            .orElseThrow(() -> new UsernameNotFoundException(
                "No user found with email: " + email
            ));
    }
}

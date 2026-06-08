package com.internship.student_exam_api.repository;

import com.internship.student_exam_api.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for AppUser operations.
 * Used for fetching authentication data from the database.
 */
@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /**
     * Find a system user by their registered email address.
     *
     * @param email user's email address
     * @return an Optional holding the AppUser if found
     */
    Optional<AppUser> findByEmail(String email);
}

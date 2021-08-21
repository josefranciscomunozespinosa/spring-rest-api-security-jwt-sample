package es.eoi.restapiwithspringsecurityandjwt.repository;

import es.eoi.restapiwithspringsecurityandjwt.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

}

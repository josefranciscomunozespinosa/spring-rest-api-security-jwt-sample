package es.eoi.restapiwithspringsecurityandjwt.repository;

import es.eoi.restapiwithspringsecurityandjwt.domain.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

}

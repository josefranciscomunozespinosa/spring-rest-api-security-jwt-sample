package es.eoi.restapiwithspringsecurityandjwt;

import es.eoi.restapiwithspringsecurityandjwt.domain.Vehicle;
import es.eoi.restapiwithspringsecurityandjwt.repository.VehicleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@Slf4j
public class DataInitializer implements CommandLineRunner {

    @Autowired
    VehicleRepository vehicles;

    @Override
    public void run(String... args) {
        log.debug("initializing vehicles data...");

        Arrays.asList("moto", "car").forEach(v -> this.vehicles.saveAndFlush(Vehicle.builder().name(v).build()));

        log.debug("printing all vehicles...");
        this.vehicles.findAll().forEach(v -> log.debug(" Vehicle :" + v.toString()));
    }
}

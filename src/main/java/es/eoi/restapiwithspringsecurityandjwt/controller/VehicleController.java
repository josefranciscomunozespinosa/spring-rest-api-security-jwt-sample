package es.eoi.restapiwithspringsecurityandjwt.controller;

import es.eoi.restapiwithspringsecurityandjwt.domain.Vehicle;
import es.eoi.restapiwithspringsecurityandjwt.exceptions.VehicleNotFoundException;
import es.eoi.restapiwithspringsecurityandjwt.repository.VehicleRepository;
import es.eoi.restapiwithspringsecurityandjwt.web.VehicleForm;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;

import static org.springframework.http.ResponseEntity.*;

@RestController
@RequestMapping("/v1/vehicles")
public class VehicleController {

    private VehicleRepository vehicles;

    public VehicleController(VehicleRepository vehicles) {
        this.vehicles = vehicles;
    }

    @GetMapping("")
    public ResponseEntity all() {
        return ok(this.vehicles.findAll());
    }

    @PostMapping("")
    public ResponseEntity save(@RequestBody VehicleForm form, HttpServletRequest request) {
        Vehicle saved = this.vehicles.save(Vehicle.builder().name(form.getName()).build());
        return created(
                ServletUriComponentsBuilder
                        .fromContextPath(request)
                        .path("/v1/vehicles/{id}")
                        .buildAndExpand(saved.getId())
                        .toUri())
                .build();
    }

    @GetMapping("/{id}")
    public ResponseEntity get(@PathVariable("id") Long id) {
        return ok(this.vehicles.findById(id).orElseThrow(VehicleNotFoundException::new));
    }

    @PutMapping("/{id}")
    public ResponseEntity update(@PathVariable("id") Long id, @RequestBody VehicleForm form) {
        Vehicle existed = this.vehicles.findById(id).orElseThrow(VehicleNotFoundException::new);
        existed.setName(form.getName());
        this.vehicles.save(existed);
        return noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity delete(@PathVariable("id") Long id) {
        Vehicle existed = this.vehicles.findById(id).orElseThrow(VehicleNotFoundException::new);
        this.vehicles.delete(existed);
        return noContent().build();
    }
}
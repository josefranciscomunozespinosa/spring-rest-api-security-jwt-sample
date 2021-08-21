package es.eoi.restapiwithspringsecurityandjwt;

import es.eoi.restapiwithspringsecurityandjwt.domain.Vehicle;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VehicleTest {

    @Test
    public void testVehicle(){
        Vehicle v = new Vehicle(1L, "test");
        assertEquals("id is 1L", 1L, (long) v.getId());
        assertEquals("name is test", "test", v.getName());

        Vehicle v2 =  Vehicle.builder().id(2L).name("test2").build();
        assertEquals("id is 2L", 2L, (long) v2.getId());
        assertEquals("name is test2", "test2", v2.getName());
    }
}

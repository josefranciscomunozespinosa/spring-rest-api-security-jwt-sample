# Protejer una REST API con Spring Security y JWT 

Cuando se diseña una API REST, se debe tener en cuenta cómo proteger la API REST. En una aplicación basada en Spring, Spring Security es una excelente solución de autenticación y autorización, y proporciona varias opciones para proteger sus API REST.

El enfoque más simple es utilizar HTTP Basic, que se activa de forma predeterminada cuando se inicia una aplicación basada en Spring Boot. Es bueno para el desarrollo y se usa con frecuencia en la fase de desarrollo, pero no se recomienda en un entorno de producción.

Spring Session (con Spring Security) proporciona una estrategia simple para crear y validar el token basado en el encabezado (id de sesión), se puede usar para proteger las API RESTful.

Además de estos, Spring Security OAuth (un subproyecto bajo Spring Security) proporciona una solución completa de autorización OAuth, incluidas las implementaciones de todos los roles definidos en el protocolo OAuth2, como el servidor de autorización, el servidor de recursos, el cliente OAuth2, etc. Spring Cloud agrega inicio de sesión único capacidad para ** OAuth2 Client ** a través de su subproyecto Spring Cloud Security. En la solución basada en Spring Security OAuth, el contenido del token de acceso puede ser un token JWT firmado o un valor opaco, y tenemos que seguir el flujo de autorización estándar de OAuth2 para obtener el token de acceso.

Pero para aquellas aplicaciones que son propiedad del propietario del recurso y no hay un plan para exponer estas API a aplicaciones de terceros, una simple autorización basada en token JWT es más simple y razonable (no necesitamos administrar las credenciales de aplicaciones cliente de terceros). Spring Security en sí no ofrece esa opción; afortunadamente, no es difícil implementarlo entretejiendo nuestro filtro personalizado en la Cadena de filtros de Spring Security. En esta publicación, crearemos una solución de autenticación JWT personalizada.

En esta aplicación de test, el flujo de autenticación basado en token JWT personalizado se puede designar como los siguientes pasos.


1. Obtener el token basado en JWT del punto final de autenticación, por ejemplo, `/auth/signin`.
2. Extraer el token del resultado de la autenticación.
3. Establezca el valor de `Authorization` del encabezado HTTP como `Bearer jwt_token`.
4. Luego enviamos una solicitud para acceder a los recursos protegidos.
5. Si el recurso solicitado está protegido, Spring Security usará nuestro `Filter` personalizado para validar el token JWT, y creará un objeto de` Authentication` y lo configurará en el `SecurityContextHolder` específico de Spring Security para completar el progreso de la autenticación.
6. Si el token JWT es válido, devolverá el recurso solicitado al cliente.

## Paso 1 - REST, configuración BBDD y spring security por defecto

### Genera el esqueleto del proyecto

La forma más rápida de crear un nuevo proyecto Spring Boot es usar [Spring Initializr] (http://start.spring.io) para generar los códigos base.

abre el navegador y ve a http://start.spring.io. En el campo **Dependencias**, seleccione 

 - Web
 - Security
 - JPA
 - Lombok 

Luego haga clic en el botón **Generar** o presione **ALT + ENTRAR** claves para generar el proyecto.

![start](./start.JPG)


### Crea una API REST

Cree una entidad JPA `Vehicle`.

```java
@Entity
@Table(name="vehicles")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Vehicle implements Serializable {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private Long id ;

	@Column
	private String name;
}
```

Crear un repositorio para `Vehicle`.

```java
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    
}
```

Cree un controlador base Spring MVC para exponer las API REST.

```java
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
        return ok(this.vehicles.findById(id).orElseThrow(() -> new VehicleNotFoundException()));
    }


    @PutMapping("/{id}")
    public ResponseEntity update(@PathVariable("id") Long id, @RequestBody VehicleForm form) {
        Vehicle existed = this.vehicles.findById(id).orElseThrow(() -> new VehicleNotFoundException());
        existed.setName(form.getName());

        this.vehicles.save(existed);
        return noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity delete(@PathVariable("id") Long id) {
        Vehicle existed = this.vehicles.findById(id).orElseThrow(() -> new VehicleNotFoundException());
        this.vehicles.delete(existed);
        return noContent().build();
    }
}
```

Es simple y estúpido. Definimos una `VehicleNotFoundException` que se lanzará si el vehículo no es encontrado por id.

Cree un exception handler simple para manejar nuestras excepciones personalizadas.


```java
@RestControllerAdvice
@Slf4j
public class RestExceptionHandler {

    @ExceptionHandler(value = {VehicleNotFoundException.class})
    public ResponseEntity vehicleNotFound(VehicleNotFoundException ex, WebRequest request) {
        log.debug("handling VehicleNotFoundException...");
        return notFound().build();
    }
}	
```

Crea un `CommandLineRunner` bean para inicializar algunos datos de vehículos en la etapa de inicio de la aplicación.

```java

@Component
@Slf4j
public class DataInitializer implements CommandLineRunner {

    @Autowired
    VehicleRepository vehicles;


    @Override
    public void run(String... args) throws Exception {
        log.debug("initializing vehicles data...");
        Arrays.asList("moto", "car").forEach(v -> this.vehicles.saveAndFlush(Vehicle.builder().name(v).build()));

        log.debug("printing all vehicles...");
        this.vehicles.findAll().forEach(v -> log.debug(" Vehicle :" + v.toString()));
    }
}

```

Dependiendo de la base de datos que vayamos a utilizar añadiremos una configuración u otra. En este caso MySQL. Por tanto lo primero añadimos la dependencia al driver

```XML
    <!-- MySql -->
    <dependency>
        <groupId>mysql</groupId>
        <artifactId>mysql-connector-java</artifactId>
        <version>5.1.33</version>
    </dependency>

```

Y seguidamente la configuración para que acceda a nuestra BBDD. En este caso utilizaremos un fichero yml

```yml
server:
  port: 8080

spring:
  datasource:
    driver-class-name: com.mysql.jdbc.Driver
    url: jdbc:mysql://localhost:3306/testdb
    username: root
    password: root

  jpa:
    openInView: false
    show_sql: true
    generate-ddl: true
    hibernate:
      ddl-auto: create-drop

  data:
    jpa:
      repositories.enabled: true
      generate-ddl: true
      show-sql: true

logging:
  level:
    org.springframework.web: INFO
    org.springframework.security: DEBUG
    es.eoi: DEBUG


```

Ahora es el momento de arrancar y una vez lo hagamos tendremos que fijarnos en los logs en el password que nos ha creado spring security por defecto para nuestro usuario user

    Using generated security password: 459da715-9ea1-4447-a0d6-46e0ba979b69


Pero de momento nos será más útil comentar la dependencia de spring security para evitarnos meter el usuario y password continuamente.

Ahora es el momento de probar que funciona. Hay varias formas. Puedes utilizar postman, el propio navegador o otra forma puede ser con curl:

Abra una terminal, use `curl` para probar las API.
```
>curl http://localhost:8080/v1/vehicles
[ {
  "id" : 1,
  "name" : "moto"
}, {
  "id" : 2,
  "name" : "car"
} ]
```

## Paso 2 - Exponer la API directamente desde el repositorio

Spring Data Rest proporciona la capacidad de exponer las API a través de la interfaz del repositorio directamente.

Add a `@RepositoryRestResource` annotation on the existed `VehicleRepository` interface.

```java
@RepositoryRestResource(path = "vehicles", collectionResourceRel = "vehicles", itemResourceRel = "vehicle")
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    
}
```

Tendremos que añadir la dependencia en el pom para poder importar la anotación `@RepositoryRestResource` y para usar HATEOAS

```XML
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-hateoas</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-rest</artifactId>
    </dependency>
```

Compila y reinicia la aplicación e intenta acceder a:

    http://localhost:8080/vehicles

Importante, sin poner `/v1/` que hemos definido para obtener los datos desde el controller.

```
curl -X GET http://localhost:8080/vehicles 
{
  "_embedded" : {
    "vehicles" : [ {
      "name" : "moto",
      "_links" : {
        "self" : {
          "href" : "http://localhost:8080/vehicles/1"
        },
        "vehicle" : {
          "href" : "http://localhost:8080/vehicles/1"
        }
      }
    }, {
      "name" : "car",
      "_links" : {
        "self" : {
          "href" : "http://localhost:8080/vehicles/2"
        },
        "vehicle" : {
          "href" : "http://localhost:8080/vehicles/2"
        }
      }
    } ]
  },
  "_links" : {
    "self" : {
      "href" : "http://localhost:8080/vehicles{?page,size,sort}",
      "templated" : true
    },
    "profile" : {
      "href" : "http://localhost:8080/profile/vehicles"
    }
  },
  "page" : {
    "size" : 20,
    "totalElements" : 2,
    "totalPages" : 1,
    "number" : 0
  }
}
```

Utiliza el proyecto Spring HATEOAS para exponer API REST más elegantes que utilizan [Richardson Mature Model Level 3](https://restfulapi.net/richardson-maturity-model/) (auto documentación).

Y ya por último también podemos añadirle swagger para poder ver nuestros endpoints tanto de Vehicle Entity como de vehicle-controller

Basta con añadir la dependencia de swagger y volver a compilarlo todo de nuevo

```XML
    <!-- Swagger UI - Api Documentation -->
    <dependency>
        <groupId>io.springfox</groupId>
        <artifactId>springfox-boot-starter</artifactId>
        <version>3.0.0</version>
    </dependency>
```


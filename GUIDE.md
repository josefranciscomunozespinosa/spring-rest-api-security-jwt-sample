# Protejer una REST API con Spring Security y JWT 

Cuando se diseña una API REST, se debe tener en cuenta cómo proteger la API REST. En una aplicación basada en Spring, Spring Security es una excelente solución de autenticación y autorización, y proporciona varias opciones para proteger sus API REST.

El enfoque más simple es utilizar HTTP Basic, que se activa de forma predeterminada cuando se inicia una aplicación basada en Spring Boot. Es bueno para el desarrollo y se usa con frecuencia en la fase de desarrollo, pero no se recomienda en un entorno de producción.

Spring Session (con Spring Security) proporciona una estrategia simple para crear y validar el token basado en el encabezado (id de sesión), se puede usar para proteger las API RESTful.

Además de estos, Spring Security OAuth (un subproyecto bajo Spring Security) proporciona una solución completa de autorización OAuth, incluidas las implementaciones de todos los roles definidos en el protocolo OAuth2, como el servidor de autorización, el servidor de recursos, el cliente OAuth2, etc. Spring Cloud agrega inicio de sesión único capacidad para `OAuth2 Client` a través de su subproyecto Spring Cloud Security. En la solución basada en Spring Security OAuth, el contenido del token de acceso puede ser un token JWT firmado o un valor opaco, y tenemos que seguir el flujo de autorización estándar de OAuth2 para obtener el token de acceso.

Pero para aquellas aplicaciones que son propiedad del propietario del recurso y no hay un plan para exponer estas API a aplicaciones de terceros, una simple autorización basada en token JWT es más simple y razonable (no necesitamos administrar las credenciales de aplicaciones cliente de terceros). Spring Security en sí no ofrece esa opción; afortunadamente, no es difícil implementarlo entretejiendo nuestro filtro personalizado en la Cadena de filtros de Spring Security. En esta publicación, crearemos una solución de autenticación JWT personalizada.

En esta aplicación de test, el flujo de autenticación basado en token JWT personalizado se puede designar como los siguientes pasos.


1. Obtener el token basado en JWT del punto final de autenticación, por ejemplo, `/auth/signin`.
2. Extraer el token del resultado de la autenticación.
3. Establezca el valor de `Authorization` del encabezado HTTP como `Bearer jwt_token`.
4. Luego enviamos una solicitud para acceder a los recursos protegidos.
5. Si el recurso solicitado está protegido, Spring Security usará nuestro `Filter` personalizado para validar el token JWT, y creará un objeto de` Authentication` y lo configurará en el `SecurityContextHolder` específico de Spring Security para completar el progreso de la autenticación.
6. Si el token JWT es válido, devolverá el recurso solicitado al cliente.

## Paso 1 - REST, configuración BBDD y spring security por defecto
Es importante que no hagáis sólo copy&paste e intentéis entender qué significa cada cosa. Las anotaciones que se utilizan, cada método, etc.

Copiar y pegar sabemos hacerlo todos. Pero es importante que al menos intentemos comprender el código que vamos a ir viendo.

Vamos allá!

### Genera el esqueleto del proyecto

La forma más rápida de crear un nuevo proyecto Spring Boot es usar [Spring Initializr](http://start.spring.io) para generar los códigos base.

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


    @GetMapping
    public ResponseEntity all() {
        return ok(this.vehicles.findAll());
    }

    @PostMapping
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
    
    @ExceptionHandler(value = {InvalidJwtAuthenticationException.class})
    public ResponseEntity invalidJwtAuthentication(InvalidJwtAuthenticationException ex, WebRequest request) {
        log.debug("handling InvalidJwtAuthenticationException...");
        return status(UNAUTHORIZED).build();
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

## Paso 3 - Securizar nuestra Api REST

Ahora crearemos un filtro de autenticación basado en token JWT personalizado para validar el token JWT.

Nos harán falta unas cuantas dependencias nuevas (y descomentamos la de spring security)

```XML

	<properties>
		<java.version>11</java.version>
		<jjwt.version>0.11.2</jjwt.version>
	</properties>

    //...

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <!-- JWT-->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>${jjwt.version}</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>${jjwt.version}</version>
        <scope>runtime</scope>
    </dependency>
    <!-- or jjwt-gson if Gson is preferred -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>${jjwt.version}</version>
        <scope>runtime</scope>
    </dependency>

```

Crea un filtro de nombre `JwtTokenFilter` para la validación del token JWT.

```java
public class JwtTokenFilter extends GenericFilterBean {

    private JwtTokenProvider jwtTokenProvider;

    public JwtTokenFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain filterChain)
        throws IOException, ServletException {

        String token = jwtTokenProvider.resolveToken((HttpServletRequest) req);
        if (token != null && jwtTokenProvider.validateToken(token)) {
            Authentication auth = token != null ? jwtTokenProvider.getAuthentication(token) : null;
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        filterChain.doFilter(req, res);
    }
}
```

Usa `JwtTokenProvider` para tratar con JWT, como generar token JWT, analizar reclamaciones JWT. Si os fijáis aquí está obteniendo dos variables de las properties a través de la anotación @Value. Tendremos que introducirlas en nuestro fichero yml o usará las que hemos marcado por defecto (que son muy cortas). 

```java

@Component
public class JwtTokenProvider {

    @Value("${security.jwt.token.secret-key:secret}")
    private String secretKey = "secret";

    @Value("${security.jwt.token.expire-length:3600000}")
    private long validityInMilliseconds = 3600000; // 1h

    @Autowired
    private UserDetailsService userDetailsService;

    @PostConstruct
    protected void init() {
        secretKey = Base64.getEncoder().encodeToString(secretKey.getBytes());
    }

    public String createToken(String username, List<String> roles) {

        Claims claims = Jwts.claims().setSubject(username);
        claims.put("roles", roles);

        Date now = new Date();
        Date validity = new Date(now.getTime() + validityInMilliseconds);

        return Jwts.builder()//
            .setClaims(claims)//
            .setIssuedAt(now)//
            .setExpiration(validity)//
            .signWith(SignatureAlgorithm.HS256, secretKey)//
            .compact();
    }

    public Authentication getAuthentication(String token) {
        UserDetails userDetails = this.userDetailsService.loadUserByUsername(getUsername(token));
        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
    }

    public String getUsername(String token) {
        return Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token).getBody().getSubject();
    }

    public String resolveToken(HttpServletRequest req) {
        String bearerToken = req.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7, bearerToken.length());
        }
        return null;
    }

    public boolean validateToken(String token) {
        try {
            Jws<Claims> claims = Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token);

            if (claims.getBody().getExpiration().before(new Date())) {
                return false;
            }

            return true;
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidJwtAuthenticationException("Expired or invalid JWT token");
        }
    }

}
```

```properties
security:
  jwt:
    token:
      secret-key: nuestra_secret-key_que es larga 234232_
      expire-length: 3600000
```


Creamos la excepción

```java
public class InvalidJwtAuthenticationException extends AuthenticationException {
    public InvalidJwtAuthenticationException(String e) {
        super(e);
    }
}
```

Creamos una clase `Configurer` para configurar `JwtTokenFilter`.

```java
public class JwtConfigurer extends SecurityConfigurerAdapter<DefaultSecurityFilterChain, HttpSecurity> {

    private JwtTokenProvider jwtTokenProvider;

    public JwtConfigurer(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public void configure(HttpSecurity http) throws Exception {
        JwtTokenFilter customFilter = new JwtTokenFilter(jwtTokenProvider);
        http.addFilterBefore(customFilter, UsernamePasswordAuthenticationFilter.class);
    }
}
```

Usaremos este configurador en nuestra aplicación de ámbito `SecurityConfig`. Autorizamos algunas rutas para que no estén securizadas, como por ejemplo las url que utiliza swagger

```java
@Configuration
public class SecurityConfig extends WebSecurityConfigurerAdapter {


    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @Bean
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        //@formatter:off
        http
            .httpBasic().disable()
            .csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
                .authorizeRequests()
                .antMatchers("/auth/signin").permitAll()
                .antMatchers(AUTH_WHITELIST).permitAll()
                .antMatchers(HttpMethod.GET, "/vehicles/**").permitAll()
                .antMatchers(HttpMethod.DELETE, "/vehicles/**").hasRole("ADMIN")
                .antMatchers(HttpMethod.GET, "/v1/vehicles/**").permitAll()
                .anyRequest().authenticated()
            .and()
            .apply(new JwtConfigurer(jwtTokenProvider));
        //@formatter:on
    }
    
    private static final String[] AUTH_WHITELIST = {
            "/swagger-ui/**",
            "/swagger-resources/**",
            "/swagger-ui.html",
            "/v3/api-docs",
            "/webjars/**"
    };
}
```

Para habilitar Spring Security, tenemos que proporcionar un bean `UserDetailsService` personalizado en tiempo de ejecución.

El `CustomUserDetailsService` está tratando de obtener datos de usuario por nombre de usuario de la base de datos.

El `User` es una entidad JPA estándar, y para simplificar el trabajo, también implementa la interfaz específica de Spring Security` UserDetails`.


```java
@Entity
@Table(name="users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    Long id;

    @NotEmpty
    private String username;

    @NotEmpty
    private String password;

    @ElementCollection(fetch = FetchType.EAGER)
    @Builder.Default
    private List<String> roles = new ArrayList<>();

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return this.roles.stream().map(SimpleGrantedAuthority::new).collect(toList());
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    @Override
    public String getUsername() {
        return this.username;
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
}
```

Creamos una interfaz `Repository` para la entidad `User`.

```java
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

}
```

Creamos un controlador para la autentificación del usuario

```java
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @Autowired
    UserRepository users;

    @PostMapping("/signin")
    public ResponseEntity signin(@RequestBody AuthenticationRequest data) {

        try {
            String username = data.getUsername();
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, data.getPassword()));
            String token = jwtTokenProvider.createToken(username, this.users.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("Username " + username + "not found")).getRoles());

            Map<Object, Object> model = new HashMap<>();
            model.put("username", username);
            model.put("token", token);
            return ok(model);
        } catch (AuthenticationException e) {
            throw new BadCredentialsException("Invalid username/password supplied");
        }
    }
}
```

Creamos una clase AuthenticationRequest

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticationRequest implements Serializable {
    private String username;
    private String password;
}

```

Cree un endpoint para obtener la información del usuario actual.

```java
@RestController()
public class UserInfoController {

    @GetMapping("/me")
    public ResponseEntity currentUser(@AuthenticationPrincipal UserDetails userDetails){
        Map<Object, Object> model = new HashMap<>();
        model.put("username", userDetails.getUsername());
        model.put("roles", userDetails.getAuthorities()
            .stream()
            .map(a -> ((GrantedAuthority) a).getAuthority())
            .collect(toList())
        );
        return ok(model);
    }
}
```

Cuando el usuario actual está autenticado, `@AuthenticationPrincipal` se vinculará al principal actual.

Agregamos dos usuarios con fines de prueba en nuestra clase de inicialización.

```java
@Component
@Slf4j
public class DataInitializer implements CommandLineRunner {

	//...

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        //...

        this.users.save(User.builder()
            .username("user")
            .password(this.passwordEncoder.encode("password"))
            .roles(Arrays.asList( "ROLE_USER"))
            .build()
        );

        this.users.save(User.builder()
            .username("admin")
            .password(this.passwordEncoder.encode("password"))
            .roles(Arrays.asList("ROLE_USER", "ROLE_ADMIN"))
            .build()
        );

        log.debug("printing all users...");
        this.users.findAll().forEach(v -> log.debug(" User :" + v.toString()));
    }
}
```

Si lo arrancásemos ahora nos daría un error porque no encuentra ningún Bean de tipo `PasswordEncoder` definido así que definiremos uno en la clase de nuestra aplicación principal.

Vamos a `RestApiWithSpringSecurityAndJwtApplication` y añadimos las siguientes líneas:

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
}
```

Compilamos todo y arrancamos de nuevo! :) 


Como ya sabemos podemos hacer la prueba del login desde diferentes clientes, en este caso usaremos `curl`:

```
curl -X POST http://localhost:8080/auth/signin -H "Content-Type:application/json" -d "{\"username\":\"user\", \"password\":\"password\"}"
```

Debería devolveros algo similar a esto:

```
{
    "username":"user",
    "token":"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyIiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTYyOTU2MDgxNiwiZXhwIjoxNjI5NTY0NDE2fQ.HWH6smHNAZ3Ejl3S6uNKKBMw9W2oYCKMt6SDb0RI8cw"
}
```

Nos está devolviendo el token que tendremos que pasar en todas las consultas que sean securizadas para que sean válidas :)

Ponemos el token en la cabecera HTTP `Authorization`, añadimos este valor como `Bearer token` y ahora ya podemos acceder a la información

```
curl -X GET http://localhost:8080/me -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyIiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTYyOTU2MDgxNiwiZXhwIjoxNjI5NTY0NDE2fQ.HWH6smHNAZ3Ejl3S6uNKKBMw9W2oYCKMt6SDb0RI8cw"
```

Nos devolverá la info de nuestro usuario logueado
```
{
    "roles":["ROLE_USER"],
    "username":"user"
}
```

## Paso 4 - Configurar Swagger para usar JWT

Si hemos intentado lanzar alguna consulta desde Swagger UI nos habremos dado cuenta de que sólo funcionan los endpoints que no están securizados y no tenemos posibilidad de añadir las cabeceras.

Esto sucede porque todavía no hemos configurado Swagger. Hasta ahora hemos utilizado la configuración por defecto pero necesitamos poder añadir el token cuando vamos a realizar una request.

Para ello crearemos una clase nueva  `OpenApi30Config ` que definirá nuestro  `ApiKey ` para incluir JWT como autorización en la cabecera y entonces configuraremos el JWT SecurityContext con el global AuthorizationScope.

Y luego, configuramos nuestro bean API Docket para incluir información de API, contextos de seguridad y esquemas de seguridad

```java
@Configuration
public class OpenApi30Config {

    private ApiKey apiKey() {
        return new ApiKey("JWT", "Authorization", "header");
    }

    private SecurityContext securityContext() {
        return SecurityContext.builder().securityReferences(defaultAuth()).build();
    }

    private List<SecurityReference> defaultAuth() {
        AuthorizationScope authorizationScope = new AuthorizationScope("global", "accessEverything");
        AuthorizationScope[] authorizationScopes = new AuthorizationScope[1];
        authorizationScopes[0] = authorizationScope;
        return Arrays.asList(new SecurityReference("JWT", authorizationScopes));
    }

    @Bean
    public Docket api() {
        return new Docket(DocumentationType.SWAGGER_2)
                .apiInfo(apiInfo())
                .securityContexts(Arrays.asList(securityContext()))
                .securitySchemes(Arrays.asList(apiKey()))
                .select()
                .apis(RequestHandlerSelectors.any())
                .paths(PathSelectors.any())
                .build();
    }

    private ApiInfo apiInfo() {
        return new ApiInfo(
                "My REST API",
                "Ejemplo Spring Rest Api con Spring Security JWT",
                "1.0",
                "Terms of service",
                new Contact("Jose", "www.google.com", "email_de_contacto@gmail.com"),
                "License of API",
                "API license URL",
                Collections.emptyList());
    }
}
```

Si arrancamos Spring boot de nuevo y accedemos a la url de swagger veremos que ahora nos aparece un botón con un candado. 

Intentamos obtener todos los usuarios y obtenemos lo siguiente:

```
{
    "timestamp": "XXXX-XX-XXTXX:XX:XX.XXX+00:00",
    "status": 403,
    "error": "Forbidden",
    "path": "/users"
}
```

Bien, es lo normal. Primero necesitamos loguearnos. Obtenemos el token con la operación `auth/signin` y con el token pulsamos en el candado y ponemos `Bearer ` y el token obtenido

```
Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyIiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTYyOTU4MDg3OSwiZXhwIjoxNjI5NTg0NDc5fQ.QgdLt0mcBt7LD9FC909hVv8yYVjzzeTAMEYn-glOZ3c
```

Ahora el candado está cerrado y ya no necesitamos meter más veces el token. Si comprobamos los endpoint deberían funcionar todos sin problemas :)


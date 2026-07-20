package com.biblioteca.blackbox.security;

import com.biblioteca.model.Usuario;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box (real HTTP, over the wire) regression tests for confirmed access-control
 * vulnerabilities in the running application. These tests hit the app exactly as an external
 * client would: no mocks, no MockMvc, no direct service calls -- only HTTP requests against a
 * randomly bound port, backed by a real (in-memory H2) database.
 *
 * All tests below are EXPECTED TO FAIL until the corresponding production bug is fixed. They
 * exist to document and pin down each vulnerability as an executable regression test, not to
 * validate correct behavior. Do not "fix" these tests by relaxing the assertion -- fix the app.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AccessControlSecurityTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private void registrar(String nombre, String correo, String contrasena, String rol) {
        String rolJson = rol == null ? "" : ",\"rol\":\"" + rol + "\"";
        String body = "{\"nombre\":\"" + nombre + "\",\"correo\":\"" + correo + "\","
                + "\"contrasena\":\"" + contrasena + "\"" + rolJson + "}";
        ResponseEntity<String> respuesta = restTemplate.postForEntity(
                "/api/usuarios/registro", new HttpEntity<>(body, jsonHeaders()), String.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * Logs in via the real form-login flow (loginProcessingUrl=/api/login, using the
     * UsernamePasswordAuthenticationFilter default param names "username"/"password" since
     * SecurityConfig never customizes them) and returns headers carrying the resulting session
     * cookie for use on subsequent authenticated requests.
     */
    private HttpHeaders login(String correo, String contrasena) {
        HttpHeaders formHeaders = new HttpHeaders();
        formHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", correo);
        form.add("password", contrasena);

        ResponseEntity<String> loginResponse = restTemplate.postForEntity(
                "/api/login", new HttpEntity<>(form, formHeaders), String.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<String> setCookies = loginResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).as("el login debería devolver una cookie de sesión").isNotNull();

        HttpHeaders sessionHeaders = new HttpHeaders();
        sessionHeaders.add(HttpHeaders.COOKIE,
                setCookies.stream().map(c -> c.split(";")[0]).collect(Collectors.joining("; ")));
        return sessionHeaders;
    }

    // ---------- Vulnerabilidad 1: escalada de privilegios por mass assignment ----------
    // UsuarioService.registrarUsuario solo fuerza rol="USUARIO" cuando el campo llega nulo o
    // vacío; si el cliente no autenticado envía "rol":"BIBLIOTECARIO" en /api/usuarios/registro
    // (permitAll), ese valor se persiste tal cual.
    @Test
    void registro_conRolBibliotecarioEnElBody_noDeberiaQuedarPersistidoComoBibliotecario() {
        String correo = "attacker-mass-assignment@test.com";
        String contrasena = "ClaveSegura123456";
        registrar("Attacker", correo, contrasena, "BIBLIOTECARIO");

        HttpHeaders sessionHeaders = login(correo, contrasena);
        ResponseEntity<Usuario> me = restTemplate.exchange(
                "/api/usuarios/me", HttpMethod.GET, new HttpEntity<>(sessionHeaders), Usuario.class);

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Comportamiento SEGURO esperado: el backend debería forzar rol=USUARIO en un
        // auto-registro no autenticado. HOY el rol enviado por el cliente se persiste tal cual,
        // así que esta aserción falla y documenta la escalada de privilegios.
        assertThat(me.getBody().getRol())
                .as("DEFECTO: un cliente no autenticado pudo auto-registrarse como BIBLIOTECARIO")
                .isEqualTo("USUARIO");
    }

    // ---------- Vulnerabilidad 2: orden de matchers deja /api/libros/isbn/** abierto ----------
    // SecurityConfig registra ".requestMatchers(\"/api/libros\",\"/api/libros/**\").permitAll()"
    // ANTES que ".requestMatchers(HttpMethod.DELETE, \"/api/libros/isbn/**\")
    // .hasAuthority(\"BIBLIOTECARIO\")". Spring Security evalúa los matchers en orden y aplica la
    // primera coincidencia; el permitAll no filtra por método HTTP, así que también absorbe el
    // DELETE (y el PUT, que ni siquiera tiene una regla de rol propia), dejando la protección
    // declarada inalcanzable.
    @Test
    void deleteLibroPorIsbn_sinAutenticar_deberiaSerRechazado() {
        ResponseEntity<String> respuesta = restTemplate.exchange(
                "/api/libros/isbn/9999999999999", HttpMethod.DELETE, HttpEntity.EMPTY, String.class);

        // Comportamiento SEGURO esperado: sin credenciales, el filtro de seguridad debería cortar
        // la petición con 401/403 antes de llegar al controller. HOY el permitAll deja pasar la
        // petición (que falla más adelante dentro de la lógica de negocio, típicamente con 500),
        // así que la aserción de abajo falla y documenta el control de acceso roto.
        assertThat(respuesta.getStatusCode())
                .as("DEFECTO: DELETE /api/libros/isbn/** no está protegido por el orden de "
                        + "matchers en SecurityConfig (respuesta real: " + respuesta.getStatusCode() + ")")
                .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    // Misma familia que el caso anterior: PUT /api/libros/isbn/** también cae bajo el permitAll
    // de "/api/libros/**" antes de que pueda aplicarse ningún control de rol.
    @Test
    void putLibroPorIsbn_sinAutenticar_deberiaSerRechazado() {
        String body = "{\"titulo\":\"Hackeado\",\"autor\":\"Atacante\",\"genero\":\"N/A\","
                + "\"editorial\":\"N/A\",\"anio\":2024,\"cantidad\":1,\"sinopsis\":\"N/A\"}";

        ResponseEntity<String> respuesta = restTemplate.exchange(
                "/api/libros/isbn/9999999999999", HttpMethod.PUT,
                new HttpEntity<>(body, jsonHeaders()), String.class);

        assertThat(respuesta.getStatusCode())
                .as("DEFECTO: PUT /api/libros/isbn/** no está protegido por el orden de matchers "
                        + "en SecurityConfig (respuesta real: " + respuesta.getStatusCode() + ")")
                .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    // ---------- Vulnerabilidad 3 (rol faltante): verificar amonestación ajena ----------
    // Controller.verificarAmonestacion(Integer id) no recibe Authentication ni valida rol; la
    // única barrera es SecurityConfig.anyRequest().authenticated(), que acepta cualquier rol.
    @Test
    void verificarAmonestacion_conRolUsuario_deberiaSerRechazadaPorFaltaDePermisoBibliotecario() {
        String correo = "usuario-comun-idor@test.com";
        String contrasena = "ClaveSegura123456";
        registrar("Usuario Comun", correo, contrasena, "USUARIO");
        HttpHeaders sessionHeaders = login(correo, contrasena);

        ResponseEntity<String> respuesta = restTemplate.exchange(
                "/api/amonestaciones-usuario/verificar/1", HttpMethod.PUT,
                new HttpEntity<>(sessionHeaders), String.class);

        // Comportamiento SEGURO esperado: solo BIBLIOTECARIO debería poder verificar
        // amonestaciones -> 403 para un usuario con rol USUARIO. HOY no hay ningún chequeo de rol
        // (ni en SecurityConfig ni en el controller), así que la petición llega a la lógica de
        // negocio sin ser rechazada por permisos (404 si el id no existe, 200 si existe -- nunca
        // 403), y la aserción de abajo falla.
        assertThat(respuesta.getStatusCode())
                .as("DEFECTO: un usuario con rol USUARIO pudo llegar a verificar una amonestación "
                        + "(respuesta real: " + respuesta.getStatusCode() + ", debería ser 403)")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}

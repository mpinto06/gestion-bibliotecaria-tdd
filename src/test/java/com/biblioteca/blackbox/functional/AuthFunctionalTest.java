package com.biblioteca.blackbox.functional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box smoke tests for the registration/login flows, exercised over real HTTP against a
 * randomly bound port backed by an in-memory H2 database. These are expected to PASS today; they
 * exist to prove the black-box CI pipeline works end-to-end on the happy paths, complementing the
 * vulnerability regression tests in {@code blackbox.security}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthFunctionalTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ResponseEntity<String> registrar(String nombre, String correo, String contrasena) {
        String body = "{\"nombre\":\"" + nombre + "\",\"correo\":\"" + correo + "\","
                + "\"contrasena\":\"" + contrasena + "\",\"rol\":\"USUARIO\"}";
        return restTemplate.postForEntity(
                "/api/usuarios/registro", new HttpEntity<>(body, jsonHeaders()), String.class);
    }

    private ResponseEntity<String> login(String correo, String contrasena) {
        HttpHeaders formHeaders = new HttpHeaders();
        formHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", correo);
        form.add("password", contrasena);
        return restTemplate.postForEntity("/api/login", new HttpEntity<>(form, formHeaders), String.class);
    }

    @Test
    void registro_conDatosValidos_devuelve200YMensajeDeExito() {
        ResponseEntity<String> respuesta = registrar(
                "Nuevo Usuario", "nuevo-funcional@test.com", "ClaveSegura123456");

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isEqualTo("Usuario registrado con éxito.");
    }

    @Test
    void registro_conCorreoYaRegistrado_devuelveMensajeDeRechazo() {
        String correo = "duplicado-funcional@test.com";
        registrar("Duplicado", correo, "ClaveSegura123456");

        ResponseEntity<String> segundoIntento = registrar("Duplicado Otra Vez", correo, "OtraClave123456");

        assertThat(segundoIntento.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(segundoIntento.getBody()).isEqualTo("Correo ya registrado.");
    }

    @Test
    void login_conCredencialesValidas_autenticaCorrectamente() {
        String correo = "login-ok-funcional@test.com";
        String contrasena = "ClaveSegura123456";
        registrar("Login OK", correo, contrasena);

        ResponseEntity<String> respuesta = login(correo, contrasena);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // NOTA: SecurityConfig no define un failureHandler propio para el formLogin, así que Spring
    // Security aplica el default: en credenciales inválidas responde 302 hacia "/login?error", no
    // un 401 directo. TestRestTemplate sigue redirects por defecto, así que la respuesta final
    // observable aquí es un 200 con la página de login autogenerada por Spring Security (HTML),
    // bien distinta del successHandler explícito de SecurityConfig (que solo hace
    // response.setStatus(200), sin cuerpo ni Content-Type). Se verifica ese contraste en vez de
    // un 401 que este endpoint, tal como está configurado hoy, nunca produce.
    @Test
    void login_conContrasenaIncorrecta_noEstableceSesionAutenticadaYSirvePaginaDeError() {
        String correo = "login-fail-funcional@test.com";
        registrar("Login Fail", correo, "ClaveSegura123456");

        ResponseEntity<String> respuesta = login(correo, "ClaveIncorrecta999");

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getHeaders().getContentType())
                .as("credenciales inválidas deberían servir la página de error HTML de Spring "
                        + "Security, no una respuesta de login exitoso (que no trae Content-Type)")
                .isNotNull();
        assertThat(respuesta.getBody()).contains("<html");
    }
}

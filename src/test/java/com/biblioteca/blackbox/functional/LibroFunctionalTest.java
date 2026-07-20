package com.biblioteca.blackbox.functional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box smoke tests for the public Libro read endpoints, exercised over real HTTP against a
 * randomly bound port backed by an in-memory H2 database. Expected to PASS today.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LibroFunctionalTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void listarLibros_devuelve200() {
        ResponseEntity<String> respuesta = restTemplate.getForEntity("/api/libros", String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void buscarLibroPorIsbnInexistente_devuelve404() {
        ResponseEntity<String> respuesta = restTemplate.getForEntity(
                "/api/libros/isbn/9999999999999", String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}

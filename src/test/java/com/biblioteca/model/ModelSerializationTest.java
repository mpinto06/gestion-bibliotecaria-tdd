package com.biblioteca.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias de seguridad sobre la serialización JSON de los modelos.
 * Aislado de Spring: se usa un com.fasterxml.jackson.databind.ObjectMapper "a pelo" (es una
 * librería, no levanta contexto de Spring), sin @WebMvcTest ni HttpMessageConverters reales.
 * Se registra JavaTimeModule manualmente porque fuera de Spring Boot no se autoconfigura.
 * Cubre hallazgo D1 del análisis de Fase 1.
 */
class ModelSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private Usuario usuarioConContrasena() {
        Usuario usuario = new Usuario();
        usuario.setId(1);
        usuario.setNombre("Ana");
        usuario.setCorreo("ana@test.com");
        usuario.setContrasena("$2a$10$hashSuperSecreto1234567890");
        usuario.setRol("USUARIO");
        return usuario;
    }

    // Doble: ninguno (serialización real con Jackson puro, no requiere mocks ni contexto web).
    // Verifica: ISO25010 Confidencialidad; ISO27001 A.8.3; ASVS V8.3.4 (minimización de
    // exposición de datos sensibles en respuestas)
    // DEFECTO DETECTADO: Usuario.contrasena no tiene @JsonIgnore / @JsonProperty(WRITE_ONLY);
    // serializar la entidad directamente (como ocurre en cualquier endpoint que no la "limpie" a
    // mano, p.ej. si se devuelve un Usuario desde un futuro endpoint) expone el hash BCrypt.
    @Test
    void usuario_DEFECTO_serializacionExponeHashDeContrasena() throws Exception {
        // Arrange
        Usuario usuario = usuarioConContrasena();

        // Act
        String json = objectMapper.writeValueAsString(usuario);

        // Assert (comportamiento SEGURO esperado: el JSON nunca debería contener el hash;
        // hoy esto falla porque el campo se serializa igual que cualquier otro)
        assertFalse(json.contains("hashSuperSecreto"),
                "DEFECTO DETECTADO: la serialización de Usuario expone la contraseña -> " + json);
    }

    // Doble: ninguno (serialización real con Jackson puro).
    // Verifica: ISO25010 Confidencialidad; ISO27001 A.8.3; ASVS V8.3.4
    // DEFECTO DETECTADO: mismo defecto raíz que el anterior (Usuario sin @JsonIgnore en
    // contrasena), pero manifestado en Prestamo.usuario, que NO tiene @JsonIgnoreProperties como
    // sí lo tiene Amonestacion.usuario. Cualquier endpoint que serialice un Prestamo
    // (GET /api/prestamos, /api/prestamos/activos, etc.) filtra el hash del usuario asociado.
    @Test
    void prestamo_DEFECTO_serializacionExponeHashDeContrasenaDelUsuarioAnidado() throws Exception {
        // Arrange
        Libro libro = new Libro();
        libro.setId(1);
        libro.setTitulo("Titulo");
        libro.setAutor("Autor");
        libro.setEditorial("Editorial");
        libro.setGenero("Genero");
        libro.setIsbn(1234567890123L);
        libro.setAnio(2020);
        libro.setCantidad(1);

        Prestamo prestamo = new Prestamo();
        prestamo.setId(1);
        prestamo.setUsuario(usuarioConContrasena());
        prestamo.setLibro(libro);
        prestamo.setFechaPrestamo(LocalDate.now());
        prestamo.setEstado("activo");

        // Act
        String json = objectMapper.writeValueAsString(prestamo);

        // Assert
        assertFalse(json.contains("hashSuperSecreto"),
                "DEFECTO DETECTADO: la serialización de Prestamo expone la contraseña del usuario anidado -> "
                        + json);
    }

    // Doble: ninguno (serialización real con Jackson puro).
    // Verifica: ISO25010 Confidencialidad; ISO27001 A.8.3 (control positivo)
    // Confirma que @JsonIgnoreProperties({"contrasena"}) en Amonestacion.usuario sí protege este
    // caso específico, a diferencia de Prestamo.usuario.
    @Test
    void amonestacion_serializacionOcultaContrasenaDelUsuarioAnidado() throws Exception {
        // Arrange
        Amonestacion amonestacion = new Amonestacion();
        amonestacion.setId(1);
        amonestacion.setUsuario(usuarioConContrasena());
        amonestacion.setMonto(100.0);
        amonestacion.setPagada(false);
        amonestacion.setVerificada(false);

        // Act
        String json = objectMapper.writeValueAsString(amonestacion);

        // Assert
        assertFalse(json.contains("hashSuperSecreto"),
                "Amonestacion no debería exponer la contraseña del usuario anidado -> " + json);
    }
}

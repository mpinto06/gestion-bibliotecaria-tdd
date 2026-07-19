package com.biblioteca.integration;

import com.biblioteca.config.SecurityConfig;
import com.biblioteca.controller.Controller;
import com.biblioteca.model.ComentarioResena;
import com.biblioteca.model.Resena;
import com.biblioteca.service.AmonestacionService;
import com.biblioteca.service.ComentarioResenaService;
import com.biblioteca.service.LibroService;
import com.biblioteca.service.PrestamoService;
import com.biblioteca.service.ResenaService;
import com.biblioteca.service.UsuarioService;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de integración de Bean Validation (@Valid) sobre los cuerpos de petición (@RequestBody).
 *
 * Tipo elegido: @WebMvcTest(Controller.class) + @Import(SecurityConfig.class). @Valid se dispara
 * en la capa de binding de argumentos de Spring MVC (HandlerMethodArgumentResolver), antes de
 * invocar el método del controller; no requiere persistencia real ni contexto completo. Se
 * importa SecurityConfig porque varias de estas rutas exigen autenticación/rol para llegar a la
 * capa de binding.
 *
 * Cubre: alcance 3 del encargo (Bean Validation en @RequestBody) sobre Usuario (registro), Libro
 * (actualización por ISBN), ResenaRequest, ComentarioResenaRequest y PrestamoRequest.
 */
@WebMvcTest(Controller.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class BeanValidationRequestBodyTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UsuarioService usuarioService;
    @MockitoBean
    private LibroService libroService;
    @MockitoBean
    private PrestamoService prestamoService;
    @MockitoBean
    private ResenaService resenaService;
    @MockitoBean
    private ComentarioResenaService comentarioResenaService;
    @MockitoBean
    private AmonestacionService amonestacionService;

    // Doble: STUB de usuarioService.registrarUsuario (para que, si el binding no rechaza el
    // payload, el controller no falle por otra razón distinta a la validación).
    // Verifica: ASVS V5.1.3 (validación de entrada positiva) / ISO27001 A.8.26
    // DEFECTO DETECTADO: Usuario no tiene anotaciones de Bean Validation (@NotBlank, @Email) y el
    // controller no usa @Valid en registrarUsuario(@RequestBody Usuario usuario). Nombre vacío,
    // correo vacío, correo con formato inválido y contraseña vacía se aceptan igual que datos
    // válidos, cuando deberían rechazarse con 400 Bad Request.
    @ParameterizedTest(name = "payload de registro inválido #{index}")
    @ValueSource(strings = {
            "{\"nombre\":\"\",\"correo\":\"ana@test.com\",\"contrasena\":\"ClaveSegura123456\",\"rol\":\"USUARIO\"}",
            "{\"nombre\":\"Ana\",\"correo\":\"\",\"contrasena\":\"ClaveSegura123456\",\"rol\":\"USUARIO\"}",
            "{\"nombre\":\"Ana\",\"correo\":\"esto-no-es-un-correo\",\"contrasena\":\"ClaveSegura123456\",\"rol\":\"USUARIO\"}",
            "{\"nombre\":\"Ana\",\"correo\":\"ana@test.com\",\"contrasena\":\"\",\"rol\":\"USUARIO\"}"
    })
    void postRegistroUsuario_DEFECTO_aceptaPayloadInvalido(String jsonInvalido) throws Exception {
        // Arrange
        when(usuarioService.registrarUsuario(any())).thenReturn("Usuario registrado con éxito.");

        // Act & Assert (comportamiento SEGURO esperado: 400 Bad Request; hoy responde 200 porque
        // no existe @Valid ni anotaciones de validación en Usuario)
        mockMvc.perform(post("/api/usuarios/registro")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonInvalido))
                .andExpect(status().isBadRequest());
    }

    // Doble: STUB de libroService.actualizarLibroPorIsbn.
    // Verifica: ASVS V5.1.3 / ISO27001 A.8.26
    // DEFECTO DETECTADO: Libro no tiene anotaciones de Bean Validation y
    // actualizarLibroPorIsbn(@RequestBody Libro ...) no usa @Valid. Título vacío y cantidad
    // negativa se aceptan igual que datos válidos (complementa, a nivel de contrato HTTP, el
    // hallazgo E2 ya probado a nivel unitario sobre la falta de validación de negocio).
    @ParameterizedTest(name = "payload de libro inválido #{index}")
    @ValueSource(strings = {
            "{\"titulo\":\"\",\"autor\":\"Autor\",\"editorial\":\"Ed\",\"genero\":\"Genero\",\"isbn\":1234567890123,\"anio\":2020,\"cantidad\":5}",
            "{\"titulo\":\"Titulo\",\"autor\":\"Autor\",\"editorial\":\"Ed\",\"genero\":\"Genero\",\"isbn\":1234567890123,\"anio\":2020,\"cantidad\":-5}"
    })
    @WithMockUser(authorities = "BIBLIOTECARIO")
    void putLibroPorIsbn_DEFECTO_aceptaPayloadInvalido(String jsonInvalido) throws Exception {
        // Arrange
        when(libroService.actualizarLibroPorIsbn(anyLong(), any(), anyString()))
                .thenReturn("Libro actualizado exitosamente.");

        // Act & Assert
        mockMvc.perform(put("/api/libros/isbn/1234567890123")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonInvalido))
                .andExpect(status().isBadRequest());
    }

    // Doble: STUB de resenaService.save.
    // Verifica: ASVS V5.1.3
    // DEFECTO DETECTADO: ResenaRequest no tiene anotaciones de Bean Validation y
    // crearResena(@RequestBody ResenaRequest) no usa @Valid. IDs nulos y texto vacío se aceptan.
    @ParameterizedTest(name = "payload de reseña inválido #{index}")
    @ValueSource(strings = {
            "{\"libroId\":null,\"usuarioId\":1,\"texto\":\"Buen libro\"}",
            "{\"libroId\":1,\"usuarioId\":1,\"texto\":\"\"}"
    })
    @WithMockUser(authorities = "USUARIO")
    void postResenas_DEFECTO_aceptaPayloadInvalido(String jsonInvalido) throws Exception {
        // Arrange
        when(resenaService.save(anyInt(), anyInt(), anyString())).thenReturn(new Resena());

        // Act & Assert
        mockMvc.perform(post("/api/resenas")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonInvalido))
                .andExpect(status().isBadRequest());
    }

    // Doble: STUB de comentarioResenaService.save.
    // Verifica: ASVS V5.1.3
    // DEFECTO DETECTADO: mismo patrón que ResenaRequest, aplicado a ComentarioResenaRequest.
    @ParameterizedTest(name = "payload de comentario inválido #{index}")
    @ValueSource(strings = {
            "{\"resenaId\":null,\"usuarioId\":1,\"texto\":\"comentario\"}",
            "{\"resenaId\":1,\"usuarioId\":1,\"texto\":\"\"}"
    })
    @WithMockUser(authorities = "USUARIO")
    void postComentariosResena_DEFECTO_aceptaPayloadInvalido(String jsonInvalido) throws Exception {
        // Arrange
        when(comentarioResenaService.save(anyInt(), anyInt(), anyString())).thenReturn(new ComentarioResena());

        // Act & Assert
        mockMvc.perform(post("/api/comentarios-resena")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonInvalido))
                .andExpect(status().isBadRequest());
    }

    // Doble: STUB de prestamoService.crearPrestamo.
    // Verifica: ASVS V5.1.3
    // DEFECTO DETECTADO: PrestamoRequest no tiene anotaciones de Bean Validation y
    // registrarPrestamo(@RequestBody PrestamoRequest) no usa @Valid. correoUsuario vacío e isbn
    // nulo se aceptan igual que datos válidos.
    @ParameterizedTest(name = "payload de préstamo inválido #{index}")
    @ValueSource(strings = {
            "{\"correoUsuario\":\"\",\"isbn\":1234567890123,\"fechaPrestamo\":\"2026-07-19\"}",
            "{\"correoUsuario\":\"cliente@test.com\",\"isbn\":null,\"fechaPrestamo\":\"2026-07-19\"}"
    })
    @WithMockUser(authorities = "BIBLIOTECARIO")
    void postPrestar_DEFECTO_aceptaPayloadInvalido(String jsonInvalido) throws Exception {
        // Arrange
        when(prestamoService.crearPrestamo(anyString(), any(), anyString()))
                .thenReturn("Préstamo registrado con éxito.");

        // Act & Assert
        mockMvc.perform(post("/api/prestar")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonInvalido))
                .andExpect(status().isBadRequest());
    }
}

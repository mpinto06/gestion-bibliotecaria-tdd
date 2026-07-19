package com.biblioteca.integration;

import com.biblioteca.config.SecurityConfig;
import com.biblioteca.controller.Controller;
import com.biblioteca.model.Resena;
import com.biblioteca.service.AmonestacionService;
import com.biblioteca.service.ComentarioResenaService;
import com.biblioteca.service.LibroService;
import com.biblioteca.service.PrestamoService;
import com.biblioteca.service.ResenaService;
import com.biblioteca.service.UsuarioService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de integración de la protección CSRF configurada (o no) en SecurityConfig.
 *
 * Tipo elegido: @WebMvcTest(Controller.class) + @Import(SecurityConfig.class). Igual que en
 * SecurityConfigAccessRulesTest, solo se necesita la cadena real de filtros de seguridad (en
 * particular, la presencia o ausencia del CsrfFilter) para observar si una petición de escritura
 * sin token CSRF es aceptada o rechazada; no se requiere persistencia real.
 *
 * Cubre: alcance 2 del encargo (protección CSRF), específicamente el efecto de
 * ".csrf(csrf -> csrf.disable())" en SecurityConfig.
 */
@WebMvcTest(Controller.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class CsrfProtectionIntegrationTest {

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

    // Doble: STUB de resenaService.save para que, si la petición llega al controller, no falle
    // por NullPointerException; lo relevante es únicamente si el CsrfFilter bloquea la petición.
    // Verifica: ISO25010 Integridad; ISO27001 A.8.26; ASVS V4.2.2 (anti-CSRF en apps basadas en
    // sesión/cookie -- esta app usa JSESSIONID vía formLogin, por lo que sí aplica)
    // DEFECTO DETECTADO: SecurityConfig llama ".csrf(csrf -> csrf.disable())", eliminando el
    // CsrfFilter de la cadena por completo. Una petición de escritura autenticada, sin ningún
    // token CSRF, es aceptada cuando -- para una app que usa autenticación por sesión/cookie
    // (JSESSIONID) -- debería rechazarse con 403 si la protección CSRF estuviera activa.
    @Test
    @WithMockUser(authorities = "USUARIO", username = "cliente@test.com")
    void postResenas_DEFECTO_sinTokenCsrfDeberiaSerRechazada() throws Exception {
        // Arrange
        when(resenaService.save(anyInt(), anyInt(), anyString())).thenReturn(new Resena());
        String body = "{\"libroId\":1,\"usuarioId\":1,\"texto\":\"Buen libro\"}";

        // Act & Assert (comportamiento SEGURO esperado: sin token CSRF debería ser 403;
        // hoy responde 200 porque csrf().disable() quitó el filtro de la cadena)
        mockMvc.perform(post("/api/resenas")
                        // deliberadamente SIN .with(csrf()) para demostrar el defecto
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // Doble: STUB de resenaService.save (control positivo, no requiere verificación de
    // interacción adicional).
    // Verifica: ASVS V4.2.2 (control positivo: con token explícito la petición se acepta)
    @Test
    @WithMockUser(authorities = "USUARIO", username = "cliente@test.com")
    void postResenas_conTokenCsrfExplicito_esAceptada() throws Exception {
        // Arrange
        when(resenaService.save(anyInt(), anyInt(), anyString())).thenReturn(new Resena());
        String body = "{\"libroId\":1,\"usuarioId\":1,\"texto\":\"Buen libro\"}";

        // Act & Assert
        mockMvc.perform(post("/api/resenas")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }
}

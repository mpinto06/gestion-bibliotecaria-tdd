package com.biblioteca.integration;

import com.biblioteca.model.Usuario;
import com.biblioteca.repository.UsuarioRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de integración de extremo a extremo para el hallazgo A3 (escalada de privilegios en el
 * registro público).
 *
 * Tipo elegido: @SpringBootTest(webEnvironment = MOCK) + @AutoConfigureMockMvc. A diferencia de
 * las demás clases de esta ronda, aquí SÍ hace falta el contexto completo de la aplicación: el
 * objetivo es demostrar el impacto real de principio a fin (petición HTTP no autenticada -> pasa
 * el filtro permitAll de SecurityConfig -> Controller -> UsuarioService -> persistencia JPA real
 * en la base H2 de pruebas), consultando después el UsuarioRepository real para confirmar qué
 * quedó efectivamente guardado. Con servicios mockeados (como en las demás clases) solo se
 * probaría, otra vez, lo que ya cubre UsuarioServiceTest a nivel unitario; aquí se verifica que
 * el defecto es explotable a través de la pila completa, no solo en aislamiento.
 *
 * Cubre: hallazgo A3 de la Fase 1 ("Pendiente para integración": comportamiento real end-to-end
 * de la escalada de privilegios contra /api/usuarios/registro con permitAll).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PrivilegeEscalationEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    // Doble: ninguno -- es la única clase de esta ronda que usa la pila real completa
    // (SecurityFilterChain real + UsuarioService real + UsuarioRepository real + H2 real), sin
    // mocks, para verificar el efecto persistido de principio a fin.
    // Verifica: ISO25010 Integridad/Autenticidad; ISO27001 A.8.2, A.5.15; ASVS V4.1.5, V4.2.1
    // DEFECTO DETECTADO (A3, end-to-end): un cliente HTTP no autenticado puede registrarse con
    // rol "BIBLIOTECARIO" a través de /api/usuarios/registro (permitAll en SecurityConfig) y ese
    // rol queda realmente persistido en la base de datos, confirmando que la escalada de
    // privilegios detectada a nivel unitario (UsuarioServiceTest) es explotable de extremo a
    // extremo vía HTTP, no solo en el servicio aislado.
    @Test
    void registro_sinAutenticar_DEFECTO_persisteRolBibliotecarioEnBaseDeDatosReal() throws Exception {
        // Arrange
        String body = "{\"nombre\":\"Atacante\",\"correo\":\"atacante-e2e@test.com\","
                + "\"contrasena\":\"ClaveSegura123456\",\"rol\":\"BIBLIOTECARIO\"}";

        // Act
        mockMvc.perform(post("/api/usuarios/registro")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // Assert (comportamiento SEGURO esperado: debería haberse persistido como "USUARIO";
        // hoy queda persistido tal cual el rol enviado por el cliente no autenticado)
        Optional<Usuario> guardado = usuarioRepository.findByCorreo("atacante-e2e@test.com");
        assertTrue(guardado.isPresent(), "El usuario debería haberse registrado");
        assertEquals("USUARIO", guardado.get().getRol(),
                "DEFECTO DETECTADO: se persistió rol=" + guardado.get().getRol()
                        + " enviado por un cliente no autenticado");
    }

    // Doble: ninguno (mismo motivo que el test anterior).
    // Verifica: ISO25010 Integridad (control positivo)
    @Test
    void registro_sinRolEspecificado_persisteRolUsuarioPorDefectoEnBaseDeDatosReal() throws Exception {
        // Arrange
        String body = "{\"nombre\":\"Cliente Normal\",\"correo\":\"normal-e2e@test.com\","
                + "\"contrasena\":\"ClaveSegura123456\"}";

        // Act
        mockMvc.perform(post("/api/usuarios/registro")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // Assert
        Optional<Usuario> guardado = usuarioRepository.findByCorreo("normal-e2e@test.com");
        assertTrue(guardado.isPresent());
        assertEquals("USUARIO", guardado.get().getRol());
    }
}

package com.biblioteca.integration;

import com.biblioteca.config.SecurityConfig;
import com.biblioteca.controller.Controller;
import com.biblioteca.service.AmonestacionService;
import com.biblioteca.service.ComentarioResenaService;
import com.biblioteca.service.LibroService;
import com.biblioteca.service.PrestamoService;
import com.biblioteca.service.ResenaService;
import com.biblioteca.service.UsuarioService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de integración de las reglas de control de acceso declaradas en SecurityConfig.
 *
 * Tipo elegido: @WebMvcTest(Controller.class) + @Import(SecurityConfig.class). Se justifica
 * porque estas pruebas solo necesitan la capa web + la cadena real de filtros de seguridad
 * (SecurityFilterChain de SecurityConfig); no requieren persistencia real ni el contexto
 * completo de la aplicación, así que se mockean los 6 servicios con @MockitoBean. SecurityConfig
 * no se detecta automáticamente en un slice @WebMvcTest (no es un @Controller/@ControllerAdvice),
 * por eso se importa explícitamente para obtener las reglas .hasAuthority/.permitAll reales en
 * vez de la configuración de seguridad por defecto que generaría Spring Boot.
 *
 * Cubre: reglas de SecurityConfig (permitAll / authenticated / hasAuthority), hallazgos B7 y B8
 * del análisis de Fase 1, y dos hallazgos nuevos detectados al diseñar esta ronda (orden de
 * reglas en SecurityConfig y el flujo real de /api/login).
 */
@WebMvcTest(Controller.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class SecurityConfigAccessRulesIntegrationTest {

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

    // ---------- Rutas permitAll ----------

    // Doble: ninguno adicional (MockMvc + filtros reales de SecurityConfig; servicio mockeado
    // solo para evitar NullPointerException, sin relevancia para lo que se verifica aquí).
    // Verifica: ASVS V4.1.1 (la regla de acceso configurada se cumple tal como está declarada)
    @Test
    @WithAnonymousUser
    void getLibros_sinAutenticar_esAccesible_permitAll() throws Exception {
        // Arrange (SecurityConfig: .requestMatchers("/api/libros","/api/libros/**").permitAll())

        // Act & Assert
        mockMvc.perform(get("/api/libros"))
                .andExpect(status().isOk());
    }

    // Doble: ninguno adicional.
    // Verifica: ASVS V4.1.1
    @Test
    @WithAnonymousUser
    void postRegistroUsuario_sinAutenticar_esAccesible_permitAll() throws Exception {
        // Arrange
        String body = "{\"nombre\":\"Ana\",\"correo\":\"ana@test.com\",\"contrasena\":\"ClaveSegura123456\",\"rol\":\"USUARIO\"}";

        // Act & Assert
        mockMvc.perform(post("/api/usuarios/registro")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    // Doble: ninguno adicional.
    // Verifica: ASVS V4.1.1
    @Test
    @WithAnonymousUser
    void getResenasPorLibro_sinAutenticar_esAccesible_permitAllSoloGet() throws Exception {
        // Arrange (SecurityConfig: GET "/api/resenas/**" permitAll)

        // Act & Assert
        mockMvc.perform(get("/api/resenas/libro/1"))
                .andExpect(status().isOk());
    }

    // ---------- Rutas que exigen solo autenticación (sin rol específico) ----------

    // Doble: ninguno adicional.
    // Verifica: ASVS V4.1.1 (rechazo de escritura anónima en ruta "authenticated()")
    @Test
    @WithAnonymousUser
    void postResenas_sinAutenticar_esRechazada() throws Exception {
        // Arrange
        String body = "{\"libroId\":1,\"usuarioId\":1,\"texto\":\"Buen libro\"}";

        // Act & Assert (con formLogin configurado, el acceso anónimo a una ruta protegida no
        // responde 401/403 directo sino un 302 hacia la página de login generada por defecto;
        // en cualquier caso NO se concede acceso al recurso, que es lo que aquí se verifica)
        mockMvc.perform(post("/api/resenas")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is3xxRedirection());
    }

    // Doble: ninguno adicional.
    // Verifica: ASVS V4.1.1 (cualquier rol autenticado basta, no se exige uno específico)
    @ParameterizedTest(name = "rol autenticado suficiente: \"{0}\"")
    @ValueSource(strings = {"USUARIO", "BIBLIOTECARIO"})
    void postResenas_autenticadoConCualquierRol_esAceptada(String rol) throws Exception {
        // Arrange
        String body = "{\"libroId\":1,\"usuarioId\":1,\"texto\":\"Buen libro\"}";

        // Act & Assert
        mockMvc.perform(post("/api/resenas")
                        .with(csrf())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user("cliente@test.com").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(rol)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    // ---------- Rutas con hasAuthority("BIBLIOTECARIO") ----------

    // Doble: ninguno adicional.
    // Verifica: ISO27001 A.8.2; ASVS V4.1.3 (mínimo privilegio aplicado por el filtro HTTP)
    @Test
    @WithMockUser(authorities = "USUARIO")
    void postPrestar_conRolUsuario_esRechazadaPorElFiltro() throws Exception {
        // Arrange
        String body = "{\"correoUsuario\":\"cliente@test.com\",\"isbn\":1234567890123,\"fechaPrestamo\":\"2026-07-19\"}";

        // Act & Assert
        mockMvc.perform(post("/api/prestar")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // Doble: ninguno adicional.
    // Verifica: ISO27001 A.8.2; ASVS V4.1.1
    @Test
    @WithMockUser(authorities = "BIBLIOTECARIO")
    void postPrestar_conRolBibliotecario_esAceptadaPorElFiltro() throws Exception {
        // Arrange
        org.mockito.Mockito.when(prestamoService.crearPrestamo(anyString(), org.mockito.ArgumentMatchers.anyLong(), anyString()))
                .thenReturn("Préstamo registrado con éxito.");
        String body = "{\"correoUsuario\":\"cliente@test.com\",\"isbn\":1234567890123,\"fechaPrestamo\":\"2026-07-19\"}";

        // Act & Assert
        mockMvc.perform(post("/api/prestar")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    // Doble: ninguno adicional.
    // Verifica: ISO27001 A.8.2; ASVS V4.1.3
    @Test
    @WithMockUser(authorities = "USUARIO")
    void getPrestamos_conRolUsuario_esRechazadaPorElFiltro() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/prestamos"))
                .andExpect(status().isForbidden());
    }

    // ---------- DEFECTO: regla de DELETE sobre /api/libros/isbn/** inalcanzable ----------

    // Doble: STUB del service (para que, si la petición llegara al controller, no falle por
    // NullPointerException) — lo relevante aquí es únicamente si el FILTRO deja pasar la
    // petición, no qué hace el servicio.
    // Verifica: ISO27001 A.8.2; ASVS V4.1.1 (la regla de acceso configurada debe cumplirse)
    // DEFECTO DETECTADO: en SecurityConfig, ".requestMatchers(\"/api/libros\",\"/api/libros/**\").permitAll()"
    // se declara ANTES que ".requestMatchers(HttpMethod.DELETE, \"/api/libros/isbn/**\").hasAuthority(\"BIBLIOTECARIO\")".
    // Spring Security evalúa los requestMatchers EN ORDEN y aplica la primera coincidencia; como
    // la regla permitAll no restringe método HTTP, también coincide con un DELETE a esa misma
    // ruta y la regla específica de rol queda inalcanzable (código muerto). Resultado: cualquier
    // usuario AUTENTICADO, sin importar su rol, puede llegar al controller de borrado por ISBN a
    // nivel de filtro HTTP (el chequeo de rol dentro de LibroService sigue existiendo como
    // respaldo, pero la protección declarada en SecurityConfig no cumple su propósito). Se usa un
    // usuario autenticado con rol USUARIO (no anónimo) porque, para una petición realmente
    // anónima, Spring Security no expone un Principal en el request (getUserPrincipal() devuelve
    // null para AnonymousAuthenticationToken), y el parámetro Authentication del controller
    // llegaría null y lanzaría NullPointerException antes de completar la operación -- por lo que
    // el escenario explotable en la práctica es el de un usuario logueado con un rol distinto al
    // exigido, no un visitante anónimo.
    @Test
    @WithMockUser(authorities = "USUARIO")
    void deleteLibroPorIsbn_DEFECTO_reglaPermitAllPrevaleceSobreReglaDeRolEspecifica() throws Exception {
        // Arrange
        org.mockito.Mockito.when(libroService.eliminarLibroPorIsbn(org.mockito.ArgumentMatchers.anyLong(), anyString()))
                .thenReturn("Libro eliminado correctamente.");

        // Act & Assert (comportamiento SEGURO esperado: sin rol BIBLIOTECARIO debería ser 403;
        // hoy responde 200 porque la regla permitAll declarada antes absorbe la petición)
        mockMvc.perform(delete("/api/libros/isbn/1234567890123")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ---------- DEFECTO B7: verificarAmonestacion sin control de rol ----------

    // Doble: STUB del service.
    // Verifica: ISO27001 A.8.2, A.5.15; ASVS V4.1.3
    // DEFECTO DETECTADO (B7): la ruta "/api/amonestaciones-usuario/verificar/**" no tiene una
    // regla .hasAuthority en SecurityConfig; solo cae en el catch-all ".anyRequest().authenticated()".
    // Cualquier usuario autenticado, sin importar su rol, puede verificar/aprobar amonestaciones.
    @Test
    @WithMockUser(authorities = "USUARIO")
    void putVerificarAmonestacion_DEFECTO_rolUsuarioNoDeberiaPoderVerificar() throws Exception {
        // Arrange
        com.biblioteca.model.Amonestacion amonestacion = new com.biblioteca.model.Amonestacion();
        amonestacion.setId(1);
        org.mockito.Mockito.when(amonestacionService.findById(1)).thenReturn(amonestacion);

        // Act & Assert (comportamiento SEGURO esperado: 403 para un rol no BIBLIOTECARIO;
        // hoy responde 200 porque SecurityConfig no restringe esta ruta por rol)
        mockMvc.perform(put("/api/amonestaciones-usuario/verificar/1").with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ---------- DEFECTO B8: getTodasAmonestaciones sin control de rol ----------

    // Doble: ninguno adicional (findAll() sin stub devuelve null, suficiente para verificar status).
    // Verifica: ISO27001 A.8.2, A.8.3; ASVS V4.1.3
    // DEFECTO DETECTADO (B8): igual que B7, "/api/amonestaciones-usuario/todas" solo exige estar
    // autenticado; expone amonestaciones (datos financieros) de todos los usuarios a cualquier rol.
    @Test
    @WithMockUser(authorities = "USUARIO")
    void getTodasAmonestaciones_DEFECTO_rolUsuarioNoDeberiaVerTodas() throws Exception {
        // Act & Assert (comportamiento SEGURO esperado: 403 para un rol no BIBLIOTECARIO;
        // hoy responde 200)
        mockMvc.perform(get("/api/amonestaciones-usuario/todas"))
                .andExpect(status().isForbidden());
    }

    // ---------- DEFECTO: /api/login JSON nunca llega al controller ----------

    // Doble: mock+verify(never) — la aserción central es que el service NUNCA es invocado,
    // porque la petición debería ser interceptada antes de llegar al controller.
    // Verifica: ISO27001 A.5.17, A.8.26; ASVS V2.2.2, V4.1.1 (consistencia entre el código
    // revisado y el flujo realmente expuesto por la aplicación)
    // DEFECTO DETECTADO: SecurityConfig configura ".formLogin(form -> form.loginProcessingUrl(\"/api/login\")...)".
    // El UsernamePasswordAuthenticationFilter de Spring Security intercepta CUALQUIER POST a esa
    // URL antes de que llegue al DispatcherServlet, y espera parámetros de formulario
    // "username"/"password" -- no el cuerpo JSON {"correo":...,"contrasena":...} que implementa
    // Controller.loginUsuario. En la práctica, el método loginUsuario (y su mensaje de error
    // genérico ya probado a nivel unitario) es código muerto: un cliente real que envíe JSON a
    // /api/login jamás ejecuta esa lógica.
    @Test
    @WithAnonymousUser
    void postLogin_conCuerpoJson_DEFECTO_nuncaInvocaAlControllerReal() throws Exception {
        // Arrange
        String body = "{\"correo\":\"cliente@test.com\",\"contrasena\":\"ClaveSegura123456\"}";

        // Act
        mockMvc.perform(post("/api/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));

        // Assert (comportamiento FUNCIONAL esperado: el JSON enviado debería procesarse en
        // Controller.loginUsuario, invocando usuarioService.autenticarYObtenerUsuario; hoy esto
        // falla porque el filtro de formLogin consume la petición antes de llegar al controller)
        verify(usuarioService).autenticarYObtenerUsuario(anyString(), anyString());
    }
}

package com.biblioteca.controller;

import com.biblioteca.dto.ComentarioResenaRequest;
import com.biblioteca.dto.ResenaRequest;
import com.biblioteca.model.Amonestacion;
import com.biblioteca.model.ComentarioResena;
import com.biblioteca.model.Resena;
import com.biblioteca.model.Usuario;
import com.biblioteca.service.AmonestacionService;
import com.biblioteca.service.ComentarioResenaService;
import com.biblioteca.service.LibroService;
import com.biblioteca.service.PrestamoService;
import com.biblioteca.service.ResenaService;
import com.biblioteca.service.UsuarioService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de seguridad para Controller.
 * Aislado de Spring: el controller se instancia con "new" y sus dependencias @Autowired se
 * inyectan por reflexión pura de Java (java.lang.reflect.Field), sin arrancar contexto ni
 * MockMvc. Cubre hallazgos B7, B8, C1-C5 del análisis de Fase 1.
 */
@ExtendWith(MockitoExtension.class)
class ControllerAuthorizationTest {

    @Mock private UsuarioService usuarioService;
    @Mock private LibroService libroService;
    @Mock private PrestamoService prestamoService;
    @Mock private ResenaService resenaService;
    @Mock private ComentarioResenaService comentarioResenaService;
    @Mock private AmonestacionService amonestacionService;
    @Mock private Authentication authentication;

    private Controller controller;

    @BeforeEach
    void setUp() throws Exception {
        controller = new Controller();
        setField("usuarioService", usuarioService);
        setField("libroService", libroService);
        setField("prestamoService", prestamoService);
        setField("resenaService", resenaService);
        setField("comentarioResenaService", comentarioResenaService);
        setField("amonestacionService", amonestacionService);
    }

    private void setField(String name, Object value) throws Exception {
        Field f = Controller.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(controller, value);
    }

    private Usuario usuarioConId(Integer id, String correo) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setCorreo(correo);
        return u;
    }

    // ---------- C1: IDOR en edición/eliminación de reseñas ----------

    // Doble: STUB (findById, buscarPorCorreo) — verifica el código de estado devuelto.
    // Verifica: ASVS V4.2.1 (protección contra IDOR/BOLA en edición de recurso ajeno)
    @Test
    void editarResena_usuarioDistintoAlAutor_devuelve403() {
        // Arrange
        Usuario autor = usuarioConId(1, "autor@test.com");
        Usuario atacante = usuarioConId(2, "atacante@test.com");
        Resena resena = new Resena();
        resena.setId(10);
        resena.setUsuario(autor);
        when(authentication.getName()).thenReturn("atacante@test.com");
        when(usuarioService.buscarPorCorreo("atacante@test.com")).thenReturn(atacante);
        when(resenaService.findById(10)).thenReturn(resena);

        // Act
        ResponseEntity<?> respuesta = controller.editarResena(10, Map.of("texto", "hackeado"), authentication);

        // Assert
        assertEquals(403, respuesta.getStatusCode().value());
        verify(resenaService, never()).guardar(any());
    }

    // Doble: STUB + mock+verify para confirmar que sí se persiste cuando el dueño coincide.
    // Verifica: ASVS V4.2.1 (positivo: el dueño legítimo sí puede editar)
    @Test
    void editarResena_usuarioAutor_permiteEdicion() {
        // Arrange
        Usuario autor = usuarioConId(1, "autor@test.com");
        Resena resena = new Resena();
        resena.setId(10);
        resena.setUsuario(autor);
        when(authentication.getName()).thenReturn("autor@test.com");
        when(usuarioService.buscarPorCorreo("autor@test.com")).thenReturn(autor);
        when(resenaService.findById(10)).thenReturn(resena);

        // Act
        ResponseEntity<?> respuesta = controller.editarResena(10, Map.of("texto", "actualizado"), authentication);

        // Assert
        assertEquals(200, respuesta.getStatusCode().value());
        verify(resenaService).guardar(resena);
        assertEquals("actualizado", resena.getTexto());
    }

    // Doble: STUB — verifica el código de estado devuelto.
    // Verifica: ASVS V4.2.1
    @Test
    void eliminarResena_usuarioDistintoAlAutor_devuelve403() {
        // Arrange
        Usuario autor = usuarioConId(1, "autor@test.com");
        Usuario atacante = usuarioConId(2, "atacante@test.com");
        Resena resena = new Resena();
        resena.setId(10);
        resena.setUsuario(autor);
        when(authentication.getName()).thenReturn("atacante@test.com");
        when(usuarioService.buscarPorCorreo("atacante@test.com")).thenReturn(atacante);
        when(resenaService.findById(10)).thenReturn(resena);

        // Act
        ResponseEntity<?> respuesta = controller.eliminarResena(10, authentication);

        // Assert
        assertEquals(403, respuesta.getStatusCode().value());
        verify(resenaService, never()).eliminar(any());
    }

    // ---------- C2: IDOR en edición/eliminación de comentarios de reseña ----------

    // Doble: STUB — verifica el código de estado devuelto.
    // Verifica: ASVS V4.2.1
    @Test
    void editarComentarioResena_usuarioDistintoAlAutor_devuelve403() {
        // Arrange
        Usuario autor = usuarioConId(1, "autor@test.com");
        Usuario atacante = usuarioConId(2, "atacante@test.com");
        ComentarioResena comentario = new ComentarioResena();
        comentario.setId(20);
        comentario.setUsuario(autor);
        when(authentication.getName()).thenReturn("atacante@test.com");
        when(usuarioService.buscarPorCorreo("atacante@test.com")).thenReturn(atacante);
        when(comentarioResenaService.findById(20)).thenReturn(comentario);

        // Act
        ResponseEntity<?> respuesta = controller.editarComentarioResena(20, Map.of("texto", "hack"), authentication);

        // Assert
        assertEquals(403, respuesta.getStatusCode().value());
        verify(comentarioResenaService, never()).guardar(any());
    }

    // Doble: STUB — verifica el código de estado devuelto.
    // Verifica: ASVS V4.2.1
    @Test
    void eliminarComentarioResena_usuarioDistintoAlAutor_devuelve403() {
        // Arrange
        Usuario autor = usuarioConId(1, "autor@test.com");
        Usuario atacante = usuarioConId(2, "atacante@test.com");
        ComentarioResena comentario = new ComentarioResena();
        comentario.setId(20);
        comentario.setUsuario(autor);
        when(authentication.getName()).thenReturn("atacante@test.com");
        when(usuarioService.buscarPorCorreo("atacante@test.com")).thenReturn(atacante);
        when(comentarioResenaService.findById(20)).thenReturn(comentario);

        // Act
        ResponseEntity<?> respuesta = controller.eliminarComentarioResena(20, authentication);

        // Assert
        assertEquals(403, respuesta.getStatusCode().value());
        verify(comentarioResenaService, never()).eliminar(any());
    }

    // ---------- C3: IDOR en pago de amonestaciones ----------

    // Doble: STUB — verifica el código de estado devuelto.
    // Verifica: ASVS V4.2.1
    @Test
    void pagarAmonestacion_usuarioDistintoAlPropietario_devuelve403() {
        // Arrange
        Usuario propietario = usuarioConId(1, "propietario@test.com");
        Usuario atacante = usuarioConId(2, "atacante@test.com");
        Amonestacion amonestacion = new Amonestacion();
        amonestacion.setId(30);
        amonestacion.setUsuario(propietario);
        when(authentication.getName()).thenReturn("atacante@test.com");
        when(usuarioService.buscarPorCorreo("atacante@test.com")).thenReturn(atacante);
        when(amonestacionService.findById(30)).thenReturn(amonestacion);

        // Act
        ResponseEntity<?> respuesta = controller.pagarAmonestacion(authentication,
                Map.of("amonestacionId", 30, "metodoPago", "tarjeta", "comprobantePago", "abc"));

        // Assert
        assertEquals(403, respuesta.getStatusCode().value());
        verify(amonestacionService, never()).guardar(any());
    }

    // Doble: STUB + mock+verify (positivo: el propietario sí puede pagar)
    // Verifica: ASVS V4.2.1
    @Test
    void pagarAmonestacion_usuarioPropietario_permitePago() {
        // Arrange
        Usuario propietario = usuarioConId(1, "propietario@test.com");
        Amonestacion amonestacion = new Amonestacion();
        amonestacion.setId(30);
        amonestacion.setUsuario(propietario);
        when(authentication.getName()).thenReturn("propietario@test.com");
        when(usuarioService.buscarPorCorreo("propietario@test.com")).thenReturn(propietario);
        when(amonestacionService.findById(30)).thenReturn(amonestacion);

        // Act
        ResponseEntity<?> respuesta = controller.pagarAmonestacion(authentication,
                Map.of("amonestacionId", 30, "metodoPago", "tarjeta", "comprobantePago", "abc"));

        // Assert
        assertEquals(200, respuesta.getStatusCode().value());
        verify(amonestacionService).guardar(amonestacion);
        assertTrue(amonestacion.isPagada());
    }

    // ---------- B7 / B8 / C4 / C5: ausencia estructural de control de acceso ----------
    // Doble: ninguno (introspección por reflexión pura, no requiere mocks). Se usa cuando el
    // defecto es la AUSENCIA de un parámetro de seguridad -- no hay rama de código que estubear
    // ni interacción que verificar, por lo que se prueba la firma del método directamente.

    // Verifica: ISO27001 A.8.2/A.5.15; ASVS V4.1.3 (mínimo privilegio server-side)
    // DEFECTO DETECTADO: verificarAmonestacion no recibe Authentication ni ningún dato de rol;
    // el único control existente es el catch-all ".anyRequest().authenticated()" de
    // SecurityConfig, que permite a CUALQUIER usuario autenticado (no solo BIBLIOTECARIO)
    // verificar/aprobar el pago de una amonestación ajena.
    @Test
    void verificarAmonestacion_DEFECTO_metodoNoValidaRolNiPropietario() throws NoSuchMethodException {
        // Arrange
        Method metodo = Controller.class.getMethod("verificarAmonestacion", Integer.class);

        // Act
        boolean tieneParametroDeSeguridad = Arrays.asList(metodo.getParameterTypes())
                .contains(Authentication.class);

        // Assert (comportamiento SEGURO esperado: el método debería recibir Authentication para
        // poder validar rol BIBLIOTECARIO antes de mutar el estado de la amonestación)
        assertTrue(tieneParametroDeSeguridad,
                "DEFECTO DETECTADO: verificarAmonestacion(Integer) no recibe Authentication; "
                        + "cualquier usuario autenticado puede verificar amonestaciones ajenas");
    }

    // Verifica: ISO27001 A.8.2/A.8.3; ASVS V4.1.3
    // DEFECTO DETECTADO: getTodasAmonestaciones no recibe Authentication; expone amonestaciones
    // (datos financieros/personales) de TODOS los usuarios a cualquier usuario autenticado, sin
    // restringir a BIBLIOTECARIO.
    @Test
    void getTodasAmonestaciones_DEFECTO_metodoNoValidaRol() throws NoSuchMethodException {
        // Arrange
        Method metodo = Controller.class.getMethod("getTodasAmonestaciones");

        // Act
        boolean tieneParametroDeSeguridad = Arrays.asList(metodo.getParameterTypes())
                .contains(Authentication.class);

        // Assert
        assertTrue(tieneParametroDeSeguridad,
                "DEFECTO DETECTADO: getTodasAmonestaciones() no recibe Authentication; expone "
                        + "amonestaciones de todos los usuarios a cualquier autenticado");
    }

    // Verifica: ISO25010 Autenticidad; ASVS V4.2.1 (IDOR en creación, no solo en edición/borrado)
    // DEFECTO DETECTADO: crearResena no recibe Authentication y usa request.getUsuarioId() tal
    // cual llega del cliente, permitiendo publicar una reseña "como" otro usuario.
    @Test
    void crearResena_DEFECTO_noCruzaUsuarioIdConAutenticado() throws NoSuchMethodException {
        // Arrange
        Method metodo = Controller.class.getMethod("crearResena", ResenaRequest.class);

        // Act
        boolean tieneParametroDeSeguridad = Arrays.asList(metodo.getParameterTypes())
                .contains(Authentication.class);

        // Assert
        assertTrue(tieneParametroDeSeguridad,
                "DEFECTO DETECTADO: crearResena(ResenaRequest) no recibe Authentication; el "
                        + "usuarioId de la reseña se toma sin validar tal cual llega del cliente");
    }

    // Verifica: ISO25010 Autenticidad; ASVS V4.2.1
    // DEFECTO DETECTADO: mismo patrón que crearResena, aplicado a comentarios de reseña.
    @Test
    void crearComentarioResena_DEFECTO_noCruzaUsuarioIdConAutenticado() throws NoSuchMethodException {
        // Arrange
        Method metodo = Controller.class.getMethod("crearComentarioResena", ComentarioResenaRequest.class);

        // Act
        boolean tieneParametroDeSeguridad = Arrays.asList(metodo.getParameterTypes())
                .contains(Authentication.class);

        // Assert
        assertTrue(tieneParametroDeSeguridad,
                "DEFECTO DETECTADO: crearComentarioResena(ComentarioResenaRequest) no recibe "
                        + "Authentication; el usuarioId se toma sin validar tal cual llega del cliente");
    }

    // ---------- D2/D3: mitigaciones manuales de exposición de contraseña (control positivo) ----------

    // Doble: STUB — verifica que la respuesta de login nunca incluya el hash de contraseña.
    // Verifica: ISO25010 Confidencialidad; ISO27001 A.8.3; ASVS V8.3.4
    @Test
    void loginUsuario_credencialesValidas_noExponeContrasenaEnRespuesta() {
        // Arrange
        Usuario autenticado = usuarioConId(1, "login@test.com");
        autenticado.setContrasena("$2a$10$hashSecreto");
        autenticado.setNombre("Login Test");
        autenticado.setRol("USUARIO");
        Usuario credenciales = new Usuario();
        credenciales.setCorreo("login@test.com");
        credenciales.setContrasena("ClaveReal123!");
        when(usuarioService.autenticarYObtenerUsuario("login@test.com", "ClaveReal123!"))
                .thenReturn(autenticado);

        // Act
        ResponseEntity<?> respuesta = controller.loginUsuario(credenciales);

        // Assert
        assertEquals(200, respuesta.getStatusCode().value());
        Usuario cuerpo = (Usuario) respuesta.getBody();
        assertNull(cuerpo.getContrasena());
    }

    // Doble: STUB (autenticarYObtenerUsuario -> null)
    // Verifica: ASVS V2.2.2 (mensaje genérico, no distingue causa del fallo)
    @Test
    void loginUsuario_credencialesInvalidas_devuelve401MensajeGenerico() {
        // Arrange
        Usuario credenciales = new Usuario();
        credenciales.setCorreo("noexiste@test.com");
        credenciales.setContrasena("cualquiera");
        when(usuarioService.autenticarYObtenerUsuario("noexiste@test.com", "cualquiera")).thenReturn(null);

        // Act
        ResponseEntity<?> respuesta = controller.loginUsuario(credenciales);

        // Assert
        assertEquals(401, respuesta.getStatusCode().value());
        assertEquals("Credenciales inválidas", respuesta.getBody());
    }

    // Doble: STUB — verifica que /usuarios/me nunca exponga el hash de contraseña.
    // Verifica: ISO25010 Confidencialidad; ASVS V8.3.4
    @Test
    void getUsuarioAutenticado_devuelveUsuarioSinContrasena() {
        // Arrange
        Usuario usuario = usuarioConId(1, "me@test.com");
        usuario.setContrasena("$2a$10$hashSecreto");
        when(authentication.getName()).thenReturn("me@test.com");
        when(usuarioService.buscarPorCorreo("me@test.com")).thenReturn(usuario);

        // Act
        ResponseEntity<Usuario> respuesta = controller.getUsuarioAutenticado(authentication);

        // Assert
        assertNull(respuesta.getBody().getContrasena());
    }
}

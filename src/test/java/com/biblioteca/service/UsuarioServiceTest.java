package com.biblioteca.service;

import com.biblioteca.model.ComentarioResena;
import com.biblioteca.model.Resena;
import com.biblioteca.model.Usuario;
import com.biblioteca.repository.AmonestacionRepository;
import com.biblioteca.repository.ComentarioResenaRepository;
import com.biblioteca.repository.PrestamoRepository;
import com.biblioteca.repository.ResenaRepository;
import com.biblioteca.repository.UsuarioRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de seguridad para UsuarioService.
 * Aislado de Spring: solo JUnit5 + Mockito, sin contexto ni MockMvc.
 * Cubre hallazgos A1-A6 del análisis de Fase 1.
 */
@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PrestamoRepository prestamoRepository;
    @Mock
    private AmonestacionRepository amonestacionRepository;
    @Mock
    private ResenaRepository resenaRepository;
    @Mock
    private ComentarioResenaRepository comentarioResenaRepository;

    @InjectMocks
    private UsuarioService usuarioService;

    private Usuario nuevoUsuario(String correo, String contrasena, String rol) {
        Usuario u = new Usuario();
        u.setNombre("Test");
        u.setCorreo(correo);
        u.setContrasena(contrasena);
        u.setRol(rol);
        return u;
    }

    // Doble: STUB (existsByCorreo/save no requieren verificación de interacción, solo respuesta fija)
    // + ArgumentCaptor para interceptar el objeto persistido y validar el hash antes del save.
    // Verifica: ISO27001 A.8.28 (codificación segura) / ASVS V2.4.1 (hash aprobado, BCrypt)
    @Test
    void registrarUsuario_debeAlmacenarContrasenaHasheadaConBCrypt_V2_4_1() {
        // Arrange
        when(usuarioRepository.existsByCorreo("ana@test.com")).thenReturn(false);
        Usuario entrada = nuevoUsuario("ana@test.com", "ClaveSegura123!", "USUARIO");
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);

        // Act
        usuarioService.registrarUsuario(entrada);

        // Assert
        verify(usuarioRepository).save(captor.capture());
        String hashGuardado = captor.getValue().getContrasena();
        assertNotEquals("ClaveSegura123!", hashGuardado);
        assertTrue(hashGuardado.startsWith("$2a$") || hashGuardado.startsWith("$2b$"));
    }

    // Doble: STUB (existsByCorreo devuelve true) + mock+verify para confirmar que NO se persiste.
    // Verifica: ISO25010 Integridad (no duplicar identidades)
    @Test
    void registrarUsuario_correoYaRegistrado_noPersisteYRechaza() {
        // Arrange
        when(usuarioRepository.existsByCorreo("dup@test.com")).thenReturn(true);
        Usuario entrada = nuevoUsuario("dup@test.com", "ClaveSegura123!", "USUARIO");

        // Act
        String resultado = usuarioService.registrarUsuario(entrada);

        // Assert
        assertEquals("Correo ya registrado.", resultado);
        verify(usuarioRepository, never()).save(any());
    }

    // Doble: STUB + ArgumentCaptor para verificar el rol asignado por defecto.
    // Verifica: ISO27001 A.8.2 (derechos de acceso privilegiado) / ASVS V4.1.3 (mínimo privilegio)
    @Test
    void registrarUsuario_sinRolEspecificado_asignaRolUsuarioPorDefecto() {
        // Arrange
        when(usuarioRepository.existsByCorreo("juan@test.com")).thenReturn(false);
        Usuario entrada = nuevoUsuario("juan@test.com", "ClaveSegura123!", null);
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);

        // Act
        usuarioService.registrarUsuario(entrada);

        // Assert
        verify(usuarioRepository).save(captor.capture());
        assertEquals("USUARIO", captor.getValue().getRol());
    }

    // Doble: STUB + ArgumentCaptor.
    // Verifica: ISO25010 Autenticidad/Integridad; ISO27001 A.8.2, A.5.15; ASVS V4.1.5/V4.2.1
    // DEFECTO DETECTADO: el registro público (/api/usuarios/registro es permitAll) acepta el campo
    // "rol" tal cual llega en el JSON del cliente. Un atacante no autenticado puede autoasignarse
    // el rol BIBLIOTECARIO (o cualquier otro) durante el registro -> escalada de privilegios
    // (mass assignment). El servicio debería forzar SIEMPRE rol=USUARIO en el alta pública.
    @ParameterizedTest(name = "rol malicioso enviado por el cliente: {0}")
    @ValueSource(strings = {"BIBLIOTECARIO", "ADMIN", "SUPERUSER"})
    void registrarUsuario_DEFECTO_permiteEscaladaDePrivilegiosPorRolArbitrario(String rolMalicioso) {
        // Arrange
        when(usuarioRepository.existsByCorreo("atacante@test.com")).thenReturn(false);
        Usuario entrada = nuevoUsuario("atacante@test.com", "ClaveSegura123!", rolMalicioso);
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);

        // Act
        usuarioService.registrarUsuario(entrada);

        // Assert (comportamiento SEGURO esperado: el registro público nunca debe persistir un rol
        // distinto de USUARIO; hoy esto falla porque el servicio persiste el rol tal cual llega)
        verify(usuarioRepository).save(captor.capture());
        assertEquals("USUARIO", captor.getValue().getRol(),
                "DEFECTO DETECTADO: el registro público permite autoasignarse rol=" + rolMalicioso);
    }

    // Doble: STUB + mock+verify (never) para probar ausencia de rechazo.
    // Verifica: ASVS V2.1.1 (longitud mínima de contraseña de 12 caracteres)
    // DEFECTO DETECTADO: no existe ninguna validación de longitud/fortaleza de contraseña antes
    // de hashear y persistir; contraseñas vacías o triviales se aceptan igual que una fuerte.
    @ParameterizedTest(name = "contraseña débil: \"{0}\"")
    @ValueSource(strings = {"", "1", "abc", "aaaaaaaaaa"})
    void registrarUsuario_DEFECTO_aceptaContrasenaDebilSinValidarFortaleza(String contrasenaDebil) {
        // Arrange
        when(usuarioRepository.existsByCorreo("debil@test.com")).thenReturn(false);
        Usuario entrada = nuevoUsuario("debil@test.com", contrasenaDebil, "USUARIO");

        // Act
        usuarioService.registrarUsuario(entrada);

        // Assert (comportamiento SEGURO esperado: no debería persistirse una contraseña que no
        // cumple el mínimo de 12 caracteres; hoy esto falla porque siempre se guarda)
        verify(usuarioRepository, never()).save(any());
    }

    // Doble: STUB de findByCorreo devolviendo Optional.empty()
    // Verifica: ASVS V2.2.2 (prevención de enumeración de usuarios)
    @Test
    void autenticarYObtenerUsuario_correoInexistente_devuelveNull() {
        // Arrange
        when(usuarioRepository.findByCorreo("noexiste@test.com")).thenReturn(Optional.empty());

        // Act
        Usuario resultado = usuarioService.autenticarYObtenerUsuario("noexiste@test.com", "cualquiera");

        // Assert
        assertNull(resultado);
    }

    // Doble: STUB de findByCorreo con un hash BCrypt real (no un mock de PasswordEncoder,
    // porque el encoder es interno a UsuarioService: se usa una contraseña real hasheada).
    // Verifica: ASVS V2.2.2 (mensaje/resultado no distingue causa del fallo)
    @Test
    void autenticarYObtenerUsuario_contrasenaIncorrecta_devuelveNullSinDistinguirCausa() {
        // Arrange
        Usuario existente = nuevoUsuario("bob@test.com",
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("ClaveReal123!"),
                "USUARIO");
        when(usuarioRepository.findByCorreo("bob@test.com")).thenReturn(Optional.of(existente));

        // Act
        Usuario resultado = usuarioService.autenticarYObtenerUsuario("bob@test.com", "ClaveIncorrecta");

        // Assert
        assertNull(resultado);
    }

    // Doble: STUB con hash BCrypt real.
    // Verifica: ASVS V2.4.1 (verificación correcta contra hash almacenado)
    @Test
    void autenticarYObtenerUsuario_credencialesCorrectas_devuelveUsuario() {
        // Arrange
        Usuario existente = nuevoUsuario("carla@test.com",
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("ClaveReal123!"),
                "USUARIO");
        when(usuarioRepository.findByCorreo("carla@test.com")).thenReturn(Optional.of(existente));

        // Act
        Usuario resultado = usuarioService.autenticarYObtenerUsuario("carla@test.com", "ClaveReal123!");

        // Assert
        assertNotNull(resultado);
        assertEquals("carla@test.com", resultado.getCorreo());
    }

    // Doble: STUB (matches -> false vía hash real) + mock+verify(never)
    // Verifica: ASVS V2.1.6 (cambio de contraseña exige la contraseña actual)
    @Test
    void cambiarContrasena_actualIncorrecta_rechazaYNoPersiste() {
        // Arrange
        Usuario existente = nuevoUsuario("dora@test.com",
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("ClaveOriginal123!"),
                "USUARIO");
        when(usuarioRepository.findByCorreo("dora@test.com")).thenReturn(Optional.of(existente));

        // Act
        String resultado = usuarioService.cambiarContrasena("dora@test.com", "ClaveIncorrecta", "NuevaClave123!");

        // Assert
        assertEquals("La contraseña actual no es correcta.", resultado);
        verify(usuarioRepository, never()).save(any());
    }

    // Doble: STUB + ArgumentCaptor para validar que el hash cambia de verdad.
    // Verifica: ASVS V2.1.6 + V2.4.1
    @Test
    void cambiarContrasena_actualCorrecta_actualizaHash() {
        // Arrange
        String hashOriginal = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                .encode("ClaveOriginal123!");
        Usuario existente = nuevoUsuario("eva@test.com", hashOriginal, "USUARIO");
        when(usuarioRepository.findByCorreo("eva@test.com")).thenReturn(Optional.of(existente));
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);

        // Act
        String resultado = usuarioService.cambiarContrasena("eva@test.com", "ClaveOriginal123!", "NuevaClave456!");

        // Assert
        assertEquals("Contraseña actualizada correctamente.", resultado);
        verify(usuarioRepository).save(captor.capture());
        assertNotEquals(hashOriginal, captor.getValue().getContrasena());
    }

    // Doble: STUB + mock+verify(never)
    // Verifica: ASVS V2.1.1 (longitud mínima de contraseña)
    // DEFECTO DETECTADO: cambiarContrasena no valida la fortaleza/longitud de la contraseña nueva;
    // se puede fijar una contraseña vacía o trivial siempre que se conozca la actual.
    @ParameterizedTest(name = "contraseña nueva débil: \"{0}\"")
    @ValueSource(strings = {"", "1", "abc"})
    void cambiarContrasena_DEFECTO_aceptaContrasenaNuevaDebil(String nuevaDebil) {
        // Arrange
        String hashOriginal = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                .encode("ClaveOriginal123!");
        Usuario existente = nuevoUsuario("fer@test.com", hashOriginal, "USUARIO");
        when(usuarioRepository.findByCorreo("fer@test.com")).thenReturn(Optional.of(existente));

        // Act
        usuarioService.cambiarContrasena("fer@test.com", "ClaveOriginal123!", nuevaDebil);

        // Assert (comportamiento SEGURO esperado: no debería persistirse una contraseña débil;
        // hoy esto falla porque siempre se guarda si la actual es correcta)
        verify(usuarioRepository, never()).save(any());
    }

    // Doble: STUB + assertThrows
    // Verifica: ISO25010 Integridad (regla de negocio: no eliminar cuentas con préstamos activos)
    @Test
    void eliminarUsuario_conPrestamosActivos_lanzaExcepcionYNoElimina() {
        // Arrange
        Usuario existente = nuevoUsuario("gus@test.com", "hash", "USUARIO");
        existente.setId(1);
        when(usuarioRepository.findByCorreo("gus@test.com")).thenReturn(Optional.of(existente));
        when(prestamoRepository.existsByUsuarioIdAndFechaDevolucionIsNull(1)).thenReturn(true);

        // Act & Assert
        assertThrows(RuntimeException.class, () -> usuarioService.eliminarUsuario("gus@test.com"));
        verify(usuarioRepository, never()).delete(any());
    }

    // Doble: STUB + assertThrows
    // Verifica: ISO25010 Integridad (regla de negocio: no eliminar cuentas con amonestaciones pendientes)
    @Test
    void eliminarUsuario_conAmonestacionesPendientes_lanzaExcepcionYNoElimina() {
        // Arrange
        Usuario existente = nuevoUsuario("hilda@test.com", "hash", "USUARIO");
        existente.setId(2);
        when(usuarioRepository.findByCorreo("hilda@test.com")).thenReturn(Optional.of(existente));
        when(prestamoRepository.existsByUsuarioIdAndFechaDevolucionIsNull(2)).thenReturn(false);
        when(amonestacionRepository.existsByUsuarioIdAndPagadaFalse(2)).thenReturn(true);

        // Act & Assert
        assertThrows(RuntimeException.class, () -> usuarioService.eliminarUsuario("hilda@test.com"));
        verify(usuarioRepository, never()).delete(any());
    }

    // Doble: STUB + mock+verify para confirmar el orden de limpieza de datos asociados.
    // Verifica: ISO25010 Integridad (borrado consistente de datos relacionados)
    @Test
    void eliminarUsuario_sinBloqueos_eliminaComentariosResenasYUsuario() {
        // Arrange
        Usuario existente = nuevoUsuario("ivan@test.com", "hash", "USUARIO");
        existente.setId(3);
        when(usuarioRepository.findByCorreo("ivan@test.com")).thenReturn(Optional.of(existente));
        when(prestamoRepository.existsByUsuarioIdAndFechaDevolucionIsNull(3)).thenReturn(false);
        when(amonestacionRepository.existsByUsuarioIdAndPagadaFalse(3)).thenReturn(false);
        List<ComentarioResena> comentarios = Collections.singletonList(new ComentarioResena());
        List<Resena> resenas = Collections.singletonList(new Resena());
        when(comentarioResenaRepository.findByUsuarioId(3)).thenReturn(comentarios);
        when(resenaRepository.findByUsuarioId(3)).thenReturn(resenas);

        // Act
        usuarioService.eliminarUsuario("ivan@test.com");

        // Assert
        verify(comentarioResenaRepository).deleteAll(comentarios);
        verify(resenaRepository).deleteAll(resenas);
        verify(usuarioRepository).delete(existente);
    }
}

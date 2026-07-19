package com.biblioteca.service;

import com.biblioteca.model.Amonestacion;
import com.biblioteca.model.Libro;
import com.biblioteca.model.Prestamo;
import com.biblioteca.model.Usuario;
import com.biblioteca.repository.AmonestacionRepository;
import com.biblioteca.repository.LibroRepository;
import com.biblioteca.repository.PrestamoRepository;
import com.biblioteca.repository.UsuarioRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de seguridad para PrestamoService.
 * Aislado de Spring: solo JUnit5 + Mockito, sin contexto ni MockMvc.
 * Cubre hallazgos B4-B5 y E3-E5 del análisis de Fase 1.
 */
@ExtendWith(MockitoExtension.class)
class PrestamoServiceTest {

    @Mock
    private PrestamoRepository prestamoRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private LibroRepository libroRepository;
    @Mock
    private AmonestacionRepository amonestacionRepository;

    @InjectMocks
    private PrestamoService prestamoService;

    private Usuario usuarioConRol(Integer id, String rol) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setCorreo("user@test.com");
        u.setRol(rol);
        return u;
    }

    private Libro libroDisponible(long isbn, int cantidad) {
        Libro l = new Libro();
        l.setIsbn(isbn);
        l.setCantidad(cantidad);
        l.setTitulo("Titulo");
        l.setAutor("Autor");
        l.setEditorial("Editorial");
        l.setGenero("Genero");
        l.setAnio(2020);
        return l;
    }

    // Doble: STUB (repos con respuestas fijas) + ArgumentCaptor sobre el libro guardado.
    // Verifica: ISO27001 A.8.2; ASVS V4.1.1/V4.1.3 (autorización aplicada en capa de servicio)
    @Test
    void crearPrestamo_conRolUsuarioYStockDisponible_registraYDecrementaStock() {
        // Arrange
        Usuario usuario = usuarioConRol(1, "USUARIO");
        Libro libro = libroDisponible(111L, 3);
        when(usuarioRepository.findByCorreo("user@test.com")).thenReturn(Optional.of(usuario));
        when(prestamoRepository.countByUsuarioIdAndFechaDevolucionIsNull(1)).thenReturn(0L);
        when(amonestacionRepository.existsByUsuarioIdAndVerificadaFalse(1)).thenReturn(false);
        when(libroRepository.findByIsbn(111L)).thenReturn(Optional.of(libro));
        ArgumentCaptor<Libro> captor = ArgumentCaptor.forClass(Libro.class);

        // Act
        String resultado = prestamoService.crearPrestamo("user@test.com", 111L, "2026-07-19");

        // Assert
        assertTrue(resultado.startsWith("Préstamo registrado"));
        verify(prestamoRepository).save(any(Prestamo.class));
        verify(libroRepository).save(captor.capture());
        assertEquals(2, captor.getValue().getCantidad());
    }

    // Doble: STUB + mock+verify(never)
    // Verifica: ISO27001 A.8.2; ASVS V4.1.3 (mínimo privilegio: solo rol USUARIO pide préstamos)
    @ParameterizedTest(name = "rol no habilitado para pedir préstamos: \"{0}\"")
    @ValueSource(strings = {"BIBLIOTECARIO", "ADMIN", ""})
    void crearPrestamo_conRolNoUsuario_rechazaYNoPersiste(String rolNoUsuario) {
        // Arrange
        when(usuarioRepository.findByCorreo("user@test.com")).thenReturn(Optional.of(usuarioConRol(1, rolNoUsuario)));

        // Act
        String resultado = prestamoService.crearPrestamo("user@test.com", 111L, "2026-07-19");

        // Assert
        assertEquals("Solo se pueden asociar préstamos a usuarios con rol USUARIO.", resultado);
        verify(prestamoRepository, never()).save(any());
    }

    // Doble: STUB + mock+verify(never)
    // Verifica: ASVS V11.1 (límites de negocio: máximo de préstamos simultáneos)
    @Test
    void crearPrestamo_conDosPrestamosActivos_rechazaYNoPersiste() {
        // Arrange
        when(usuarioRepository.findByCorreo("user@test.com")).thenReturn(Optional.of(usuarioConRol(1, "USUARIO")));
        when(prestamoRepository.countByUsuarioIdAndFechaDevolucionIsNull(1)).thenReturn(2L);

        // Act
        String resultado = prestamoService.crearPrestamo("user@test.com", 111L, "2026-07-19");

        // Assert
        assertEquals("El usuario ya tiene 2 préstamos activos.", resultado);
        verify(prestamoRepository, never()).save(any());
    }

    // Doble: STUB + mock+verify(never)
    // Verifica: ASVS V11.1 (límites de negocio: amonestaciones pendientes bloquean nuevos préstamos)
    @Test
    void crearPrestamo_conAmonestacionesPendientes_rechazaYNoPersiste() {
        // Arrange
        when(usuarioRepository.findByCorreo("user@test.com")).thenReturn(Optional.of(usuarioConRol(1, "USUARIO")));
        when(prestamoRepository.countByUsuarioIdAndFechaDevolucionIsNull(1)).thenReturn(0L);
        when(amonestacionRepository.existsByUsuarioIdAndVerificadaFalse(1)).thenReturn(true);

        // Act
        String resultado = prestamoService.crearPrestamo("user@test.com", 111L, "2026-07-19");

        // Assert
        assertEquals("El usuario tiene amonestaciones activas.", resultado);
        verify(prestamoRepository, never()).save(any());
    }

    // Doble: STUB + mock+verify(never)
    // Verifica: ASVS V11.1 (no permitir préstamo de un libro sin stock)
    @Test
    void crearPrestamo_libroSinStock_rechazaYNoPersiste() {
        // Arrange
        when(usuarioRepository.findByCorreo("user@test.com")).thenReturn(Optional.of(usuarioConRol(1, "USUARIO")));
        when(prestamoRepository.countByUsuarioIdAndFechaDevolucionIsNull(1)).thenReturn(0L);
        when(amonestacionRepository.existsByUsuarioIdAndVerificadaFalse(1)).thenReturn(false);
        when(libroRepository.findByIsbn(111L)).thenReturn(Optional.of(libroDisponible(111L, 0)));

        // Act
        String resultado = prestamoService.crearPrestamo("user@test.com", 111L, "2026-07-19");

        // Assert
        assertEquals("Libro no disponible para préstamo.", resultado);
        verify(prestamoRepository, never()).save(any());
    }

    // Doble: STUB de findByCorreo devolviendo Optional.empty()
    // Verifica: ISO25010 Integridad (no operar sobre identidades inexistentes)
    @Test
    void crearPrestamo_usuarioNoRegistrado_rechaza() {
        // Arrange
        when(usuarioRepository.findByCorreo("fantasma@test.com")).thenReturn(Optional.empty());

        // Act
        String resultado = prestamoService.crearPrestamo("fantasma@test.com", 111L, "2026-07-19");

        // Assert
        assertEquals("Usuario no registrado.", resultado);
    }

    // Doble: STUB + mock+verify(never) sobre el propio prestamoRepository
    // Verifica: ASVS V11.1 (no permitir doble devolución del mismo préstamo)
    @Test
    void devolverPrestamo_yaDevuelto_rechazaYNoDuplicaEfectos() {
        // Arrange
        Prestamo prestamo = new Prestamo();
        prestamo.setFechaDevolucion(LocalDate.of(2026, 7, 1));
        when(prestamoRepository.findById(1)).thenReturn(Optional.of(prestamo));

        // Act
        String resultado = prestamoService.devolverPrestamo(1);

        // Assert
        assertEquals("El préstamo ya fue devuelto.", resultado);
        verify(libroRepository, never()).save(any());
    }

    // Doble: STUB + ArgumentCaptor para interceptar la amonestación generada por mora.
    // Verifica: ISO25010 Integridad; ASVS V11.1 (regla de negocio: mora genera sanción)
    @Test
    void devolverPrestamo_vencido_generaAmonestacion() {
        // Arrange
        Usuario usuario = usuarioConRol(1, "USUARIO");
        Libro libro = libroDisponible(111L, 0);
        Prestamo prestamo = new Prestamo();
        prestamo.setUsuario(usuario);
        prestamo.setLibro(libro);
        prestamo.setFechaLimite(LocalDate.now().minusDays(3));
        when(prestamoRepository.findById(1)).thenReturn(Optional.of(prestamo));
        ArgumentCaptor<Amonestacion> captor = ArgumentCaptor.forClass(Amonestacion.class);

        // Act
        prestamoService.devolverPrestamo(1);

        // Assert
        verify(amonestacionRepository).save(captor.capture());
        assertEquals(100.0, captor.getValue().getMonto());
        assertFalse(captor.getValue().isPagada());
    }

    // Doble: STUB + mock+verify(never) sobre amonestacionRepository
    // Verifica: ISO25010 Integridad (no sancionar devoluciones a tiempo)
    @Test
    void devolverPrestamo_aTiempo_noGeneraAmonestacion() {
        // Arrange
        Usuario usuario = usuarioConRol(1, "USUARIO");
        Libro libro = libroDisponible(111L, 0);
        Prestamo prestamo = new Prestamo();
        prestamo.setUsuario(usuario);
        prestamo.setLibro(libro);
        prestamo.setFechaLimite(LocalDate.now().plusDays(3));
        when(prestamoRepository.findById(1)).thenReturn(Optional.of(prestamo));

        // Act
        prestamoService.devolverPrestamo(1);

        // Assert
        verify(amonestacionRepository, never()).save(any());
    }

    // Doble: mock+verify(never) puro (no hay repos que estubear: el rechazo ocurre antes)
    // Verifica: ISO27001 A.8.2; ASVS V4.1.1 (rol resuelto server-side, no confiado del cliente)
    @ParameterizedTest(name = "rol no habilitado para renovar: \"{0}\"")
    @ValueSource(strings = {"USUARIO", ""})
    void renovarPrestamo_conRolNoBibliotecario_rechaza(String rolNoBibliotecario) {
        // Act
        String resultado = prestamoService.renovarPrestamo(1, rolNoBibliotecario);

        // Assert
        assertEquals("Solo los bibliotecarios pueden renovar préstamos.", resultado);
        verify(prestamoRepository, never()).findById(any());
    }

    // Doble: STUB
    // Verifica: ISO25010 Integridad
    @Test
    void renovarPrestamo_noEncontrado_rechaza() {
        // Arrange
        when(prestamoRepository.findById(99)).thenReturn(Optional.empty());

        // Act
        String resultado = prestamoService.renovarPrestamo(99, "BIBLIOTECARIO");

        // Assert
        assertEquals("Préstamo no encontrado.", resultado);
    }

    // Doble: STUB
    // Verifica: ASVS V11.1 (solo se renuevan préstamos ya finalizados)
    @Test
    void renovarPrestamo_noFinalizado_rechaza() {
        // Arrange
        Prestamo prestamo = new Prestamo();
        prestamo.setFechaDevolucion(null);
        when(prestamoRepository.findById(1)).thenReturn(Optional.of(prestamo));

        // Act
        String resultado = prestamoService.renovarPrestamo(1, "BIBLIOTECARIO");

        // Assert
        assertEquals("Solo se pueden renovar préstamos finalizados.", resultado);
    }

    // Doble: STUB
    // Verifica: ASVS V11.1 (no renovar con sanciones pendientes de verificación)
    @Test
    void renovarPrestamo_conAmonestacionesPendientes_rechaza() {
        // Arrange
        Usuario usuario = usuarioConRol(1, "USUARIO");
        Prestamo prestamo = new Prestamo();
        prestamo.setUsuario(usuario);
        prestamo.setFechaDevolucion(LocalDate.now());
        when(prestamoRepository.findById(1)).thenReturn(Optional.of(prestamo));
        when(amonestacionRepository.existsByUsuarioIdAndVerificadaFalse(1)).thenReturn(true);

        // Act
        String resultado = prestamoService.renovarPrestamo(1, "BIBLIOTECARIO");

        // Assert
        assertEquals("No se puede renovar el préstamo. El usuario tiene amonestaciones pendientes de verificación.",
                resultado);
        verify(prestamoRepository, never()).save(any());
    }

    // Doble: STUB + ArgumentCaptor
    // Verifica: ISO25010 Integridad (estado consistente tras renovación)
    @Test
    void renovarPrestamo_conStockDisponible_actualizaFechaLimiteYEstado() {
        // Arrange
        Usuario usuario = usuarioConRol(1, "USUARIO");
        Libro libro = libroDisponible(111L, 5);
        Prestamo prestamo = new Prestamo();
        prestamo.setUsuario(usuario);
        prestamo.setLibro(libro);
        prestamo.setFechaDevolucion(LocalDate.now());
        when(prestamoRepository.findById(1)).thenReturn(Optional.of(prestamo));
        when(amonestacionRepository.existsByUsuarioIdAndVerificadaFalse(1)).thenReturn(false);
        ArgumentCaptor<Prestamo> captor = ArgumentCaptor.forClass(Prestamo.class);

        // Act
        String resultado = prestamoService.renovarPrestamo(1, "BIBLIOTECARIO");

        // Assert
        assertTrue(resultado.startsWith("Préstamo renovado con éxito"));
        verify(prestamoRepository).save(captor.capture());
        assertEquals("activo", captor.getValue().getEstado());
        assertNull(captor.getValue().getFechaDevolucion());
    }

    // Doble: STUB + ArgumentCaptor sobre el libro guardado.
    // Verifica: ISO25010 Integridad; ASVS V11.1.4 (límites/reglas de negocio aplicados de forma
    // consistente antes de mutar estado)
    // DEFECTO DETECTADO: renovarPrestamo decrementa libro.getCantidad()-1 sin comprobar primero
    // que haya stock disponible (a diferencia de crearPrestamo, que sí valida cantidad>=1 antes de
    // decrementar). Con cantidad=0 el inventario queda en -1, un estado inconsistente/corrupto.
    @Test
    void renovarPrestamo_DEFECTO_decrementaStockSinVerificarDisponibilidad() {
        // Arrange
        Usuario usuario = usuarioConRol(1, "USUARIO");
        Libro libroSinStock = libroDisponible(111L, 0);
        Prestamo prestamo = new Prestamo();
        prestamo.setUsuario(usuario);
        prestamo.setLibro(libroSinStock);
        prestamo.setFechaDevolucion(LocalDate.now());
        when(prestamoRepository.findById(1)).thenReturn(Optional.of(prestamo));
        when(amonestacionRepository.existsByUsuarioIdAndVerificadaFalse(1)).thenReturn(false);
        ArgumentCaptor<Libro> captor = ArgumentCaptor.forClass(Libro.class);

        // Act
        prestamoService.renovarPrestamo(1, "BIBLIOTECARIO");

        // Assert (comportamiento SEGURO esperado: el stock nunca debería quedar negativo;
        // hoy esto falla porque siempre se decrementa sin chequear disponibilidad)
        verify(libroRepository).save(captor.capture());
        assertTrue(captor.getValue().getCantidad() >= 0,
                "DEFECTO DETECTADO: renovarPrestamo dejó el stock en " + captor.getValue().getCantidad());
    }
}

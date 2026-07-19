package com.biblioteca.service;

import com.biblioteca.model.Libro;
import com.biblioteca.model.Usuario;
import com.biblioteca.repository.LibroRepository;
import com.biblioteca.repository.UsuarioRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de seguridad para LibroService.
 * Aislado de Spring: solo JUnit5 + Mockito, sin contexto ni MockMvc.
 * Cubre hallazgos B1-B3 y E1-E2 del análisis de Fase 1.
 */
@ExtendWith(MockitoExtension.class)
class LibroServiceTest {

    @Mock
    private LibroRepository libroRepository;
    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private LibroService libroService;

    private Usuario usuarioConRol(String rol) {
        Usuario u = new Usuario();
        u.setCorreo("op@test.com");
        u.setRol(rol);
        return u;
    }

    private Libro libroValido(long isbn) {
        Libro libro = new Libro();
        libro.setTitulo("Titulo");
        libro.setAutor("Autor");
        libro.setEditorial("Editorial");
        libro.setGenero("Genero");
        libro.setIsbn(isbn);
        libro.setAnio(2020);
        libro.setCantidad(5);
        return libro;
    }

    // Doble: STUB (usuarioRepository/libroRepository con respuestas fijas) + ArgumentCaptor.
    // Verifica: ISO27001 A.8.2; ASVS V4.1.1/V4.1.3 (autorización aplicada en capa de servicio)
    @Test
    void registrarLibroConImagen_conRolBibliotecario_persisteLibro() {
        // Arrange
        when(usuarioRepository.findByCorreo("op@test.com")).thenReturn(Optional.of(usuarioConRol("BIBLIOTECARIO")));
        when(libroRepository.findByIsbn(1234567890123L)).thenReturn(Optional.empty());
        Libro libro = libroValido(1234567890123L);
        ArgumentCaptor<Libro> captor = ArgumentCaptor.forClass(Libro.class);

        // Act
        String resultado = libroService.registrarLibroConImagen(libro, null, "op@test.com");

        // Assert
        assertEquals("Libro registrado exitosamente.", resultado);
        verify(libroRepository).save(captor.capture());
        assertEquals(1234567890123L, captor.getValue().getIsbn());
    }

    // Doble: STUB de usuarioRepository + assertThrows (no requiere verificar interacción con save).
    // Verifica: ISO27001 A.8.2 (derechos de acceso privilegiado); ASVS V4.1.3 (mínimo privilegio)
    @ParameterizedTest(name = "rol sin privilegio: \"{0}\"")
    @ValueSource(strings = {"USUARIO", "ADMIN", "bibliotecario"})
    void registrarLibroConImagen_conRolNoBibliotecario_lanzaAccessDeniedException(String rolSinPrivilegio) {
        // Arrange
        when(usuarioRepository.findByCorreo("op@test.com")).thenReturn(Optional.of(usuarioConRol(rolSinPrivilegio)));
        Libro libro = libroValido(1234567890123L);

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> libroService.registrarLibroConImagen(libro, null, "op@test.com"));
        verify(libroRepository, never()).save(any());
    }

    // Doble: STUB + mock+verify(never)
    // Verifica: ISO25010 Integridad (no duplicar identificadores únicos de negocio)
    @Test
    void registrarLibroConImagen_isbnDuplicado_rechazaYNoPersiste() {
        // Arrange
        when(usuarioRepository.findByCorreo("op@test.com")).thenReturn(Optional.of(usuarioConRol("BIBLIOTECARIO")));
        Libro existente = libroValido(1234567890123L);
        when(libroRepository.findByIsbn(1234567890123L)).thenReturn(Optional.of(existente));
        Libro libro = libroValido(1234567890123L);

        // Act
        String resultado = libroService.registrarLibroConImagen(libro, null, "op@test.com");

        // Assert
        assertEquals("El libro con ese ISBN ya existe.", resultado);
        verify(libroRepository, never()).save(any());
    }

    // Doble: STUB + mock+verify(never)
    // Verifica: ASVS V5.1.3 (validación de entrada positiva: cantidad debe ser >0)
    @ParameterizedTest(name = "cantidad inválida: {0}")
    @ValueSource(ints = {0, -1, -100})
    void registrarLibroConImagen_cantidadNoPositiva_rechazaYNoPersiste(int cantidadInvalida) {
        // Arrange
        when(usuarioRepository.findByCorreo("op@test.com")).thenReturn(Optional.of(usuarioConRol("BIBLIOTECARIO")));
        when(libroRepository.findByIsbn(1234567890123L)).thenReturn(Optional.empty());
        Libro libro = libroValido(1234567890123L);
        libro.setCantidad(cantidadInvalida);

        // Act
        String resultado = libroService.registrarLibroConImagen(libro, null, "op@test.com");

        // Assert
        assertEquals("Debe haber al menos una copia física del libro.", resultado);
        verify(libroRepository, never()).save(any());
    }

    // Doble: STUB + mock+verify(never)
    // Verifica: ASVS V5.1.3 (validación de entrada positiva sobre el formato del ISBN)
    // DEFECTO DETECTADO: la validación de longitud usa String.valueOf(isbn).length()==13; en un
    // ISBN negativo de 12 dígitos de magnitud (p.ej. -123456789012) el signo "-" ocupa el lugar
    // de un dígito y la longitud del string da igualmente 13, colando un ISBN inválido/negativo
    // que nunca debería aceptarse como identificador de negocio.
    @Test
    void registrarLibroConImagen_DEFECTO_isbnNegativoBurlaValidacionDeLongitud() {
        // Arrange
        long isbnNegativo = -123456789012L; // "-123456789012" -> length() == 13
        when(usuarioRepository.findByCorreo("op@test.com")).thenReturn(Optional.of(usuarioConRol("BIBLIOTECARIO")));
        when(libroRepository.findByIsbn(isbnNegativo)).thenReturn(Optional.empty());
        Libro libro = libroValido(isbnNegativo);

        // Act
        libroService.registrarLibroConImagen(libro, null, "op@test.com");

        // Assert (comportamiento SEGURO esperado: un ISBN negativo nunca debería persistirse;
        // hoy esto falla porque el chequeo de longitud lo deja pasar)
        verify(libroRepository, never()).save(any());
    }

    // Doble: STUB + ArgumentCaptor sobre los campos actualizados.
    // Verifica: ISO27001 A.8.2; ASVS V4.1.1
    @Test
    void actualizarLibroPorIsbn_conRolBibliotecario_actualizaCamposPermitidos() {
        // Arrange
        when(usuarioRepository.findByCorreo("op@test.com")).thenReturn(Optional.of(usuarioConRol("BIBLIOTECARIO")));
        Libro existente = libroValido(1234567890123L);
        when(libroRepository.findByIsbn(1234567890123L)).thenReturn(Optional.of(existente));
        Libro datosNuevos = libroValido(1234567890123L);
        datosNuevos.setTitulo("Nuevo Titulo");
        datosNuevos.setCantidad(9);
        ArgumentCaptor<Libro> captor = ArgumentCaptor.forClass(Libro.class);

        // Act
        String resultado = libroService.actualizarLibroPorIsbn(1234567890123L, datosNuevos, "op@test.com");

        // Assert
        assertEquals("Libro actualizado exitosamente.", resultado);
        verify(libroRepository).save(captor.capture());
        assertEquals("Nuevo Titulo", captor.getValue().getTitulo());
        assertEquals(9, captor.getValue().getCantidad());
    }

    // Doble: STUB + assertThrows
    // Verifica: ISO27001 A.8.2; ASVS V4.1.3
    @ParameterizedTest(name = "rol sin privilegio: \"{0}\"")
    @ValueSource(strings = {"USUARIO", ""})
    void actualizarLibroPorIsbn_conRolNoBibliotecario_lanzaAccessDeniedException(String rolSinPrivilegio) {
        // Arrange
        when(usuarioRepository.findByCorreo("op@test.com")).thenReturn(Optional.of(usuarioConRol(rolSinPrivilegio)));
        Libro datosNuevos = libroValido(1234567890123L);

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> libroService.actualizarLibroPorIsbn(1234567890123L, datosNuevos, "op@test.com"));
        verify(libroRepository, never()).save(any());
    }

    // Doble: STUB + mock+verify(never)
    // Verifica: ISO25010 Integridad; ASVS V5.1.3 (validación server-side consistente en todos los
    // endpoints, no solo en la creación)
    // DEFECTO DETECTADO: actualizarLibroPorIsbn copia datosActualizados.getCantidad() sin repetir
    // la validación ">0" que sí existe en el alta. Permite dejar el inventario en negativo vía PUT.
    @ParameterizedTest(name = "cantidad inválida vía PUT: {0}")
    @ValueSource(ints = {-1, -50})
    void actualizarLibroPorIsbn_DEFECTO_permiteCantidadNegativa(int cantidadInvalida) {
        // Arrange
        when(usuarioRepository.findByCorreo("op@test.com")).thenReturn(Optional.of(usuarioConRol("BIBLIOTECARIO")));
        Libro existente = libroValido(1234567890123L);
        when(libroRepository.findByIsbn(1234567890123L)).thenReturn(Optional.of(existente));
        Libro datosNuevos = libroValido(1234567890123L);
        datosNuevos.setCantidad(cantidadInvalida);

        // Act
        libroService.actualizarLibroPorIsbn(1234567890123L, datosNuevos, "op@test.com");

        // Assert (comportamiento SEGURO esperado: no debería persistirse cantidad negativa;
        // hoy esto falla porque siempre se guarda)
        verify(libroRepository, never()).save(any());
    }

    // Doble: STUB + assertThrows
    // Verifica: ISO25010 Integridad (no operar sobre recursos inexistentes)
    @Test
    void actualizarLibroPorIsbn_libroNoEncontrado_lanzaExcepcion() {
        // Arrange
        when(usuarioRepository.findByCorreo("op@test.com")).thenReturn(Optional.of(usuarioConRol("BIBLIOTECARIO")));
        when(libroRepository.findByIsbn(999L)).thenReturn(Optional.empty());
        Libro datosNuevos = libroValido(999L);

        // Act & Assert
        assertThrows(RuntimeException.class,
                () -> libroService.actualizarLibroPorIsbn(999L, datosNuevos, "op@test.com"));
    }

    // Doble: STUB + mock+verify
    // Verifica: ISO27001 A.8.2; ASVS V4.1.1
    @Test
    void eliminarLibroPorIsbn_conRolBibliotecario_eliminaLibro() {
        // Arrange
        when(usuarioRepository.findByCorreo("op@test.com")).thenReturn(Optional.of(usuarioConRol("BIBLIOTECARIO")));
        Libro existente = libroValido(1234567890123L);
        when(libroRepository.findByIsbn(1234567890123L)).thenReturn(Optional.of(existente));

        // Act
        String resultado = libroService.eliminarLibroPorIsbn(1234567890123L, "op@test.com");

        // Assert
        assertEquals("Libro eliminado correctamente.", resultado);
        verify(libroRepository).delete(existente);
    }

    // Doble: STUB + assertThrows
    // Verifica: ISO27001 A.8.2; ASVS V4.1.3
    @ParameterizedTest(name = "rol sin privilegio: \"{0}\"")
    @ValueSource(strings = {"USUARIO", "ADMIN"})
    void eliminarLibroPorIsbn_conRolNoBibliotecario_lanzaAccessDeniedException(String rolSinPrivilegio) {
        // Arrange
        when(usuarioRepository.findByCorreo("op@test.com")).thenReturn(Optional.of(usuarioConRol(rolSinPrivilegio)));

        // Act & Assert
        assertThrows(AccessDeniedException.class,
                () -> libroService.eliminarLibroPorIsbn(1234567890123L, "op@test.com"));
        verify(libroRepository, never()).delete(any());
    }

    // Doble: STUB + assertThrows
    // Verifica: ISO25010 Integridad
    @Test
    void eliminarLibroPorIsbn_libroNoEncontrado_lanzaExcepcion() {
        // Arrange
        when(usuarioRepository.findByCorreo("op@test.com")).thenReturn(Optional.of(usuarioConRol("BIBLIOTECARIO")));
        when(libroRepository.findByIsbn(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> libroService.eliminarLibroPorIsbn(999L, "op@test.com"));
    }
}

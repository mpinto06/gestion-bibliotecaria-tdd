package com.biblioteca.service;

import com.biblioteca.repository.AmonestacionRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de seguridad para AmonestacionService.
 * Aislado de Spring: solo JUnit5 + Mockito, sin contexto ni MockMvc.
 * Cubre hallazgo B6 del análisis de Fase 1.
 */
@ExtendWith(MockitoExtension.class)
class AmonestacionServiceTest {

    @Mock
    private AmonestacionRepository amonestacionRepository;

    @InjectMocks
    private AmonestacionService amonestacionService;

    // Doble: STUB (existsById) + mock+verify sobre deleteById.
    // Verifica: ISO27001 A.8.2; ASVS V4.1.1/V4.1.3 (rol resuelto server-side por el controller)
    @Test
    void eliminarAmonestacion_conRolBibliotecario_elimina() {
        // Arrange
        when(amonestacionRepository.existsById(5)).thenReturn(true);

        // Act
        String resultado = amonestacionService.eliminarAmonestacion(5, "BIBLIOTECARIO");

        // Assert
        assertEquals("Amonestación eliminada con éxito.", resultado);
        verify(amonestacionRepository).deleteById(5);
    }

    // Doble: mock+verify(never) puro (el rechazo ocurre antes de tocar el repositorio)
    // Verifica: ISO27001 A.8.2 (derechos de acceso privilegiado); ASVS V4.1.3 (mínimo privilegio)
    @ParameterizedTest(name = "rol sin privilegio: \"{0}\"")
    @ValueSource(strings = {"USUARIO", "", "admin"})
    void eliminarAmonestacion_conRolNoBibliotecario_rechazaYNoConsultaRepositorio(String rolSinPrivilegio) {
        // Act
        String resultado = amonestacionService.eliminarAmonestacion(5, rolSinPrivilegio);

        // Assert
        assertEquals("Solo los bibliotecarios pueden eliminar amonestaciones.", resultado);
        verify(amonestacionRepository, never()).existsById(any());
        verify(amonestacionRepository, never()).deleteById(any());
    }

    // Doble: STUB (existsById -> false) + mock+verify(never)
    // Verifica: ISO25010 Integridad (no operar sobre recursos inexistentes)
    @Test
    void eliminarAmonestacion_noEncontrada_rechaza() {
        // Arrange
        when(amonestacionRepository.existsById(404)).thenReturn(false);

        // Act
        String resultado = amonestacionService.eliminarAmonestacion(404, "BIBLIOTECARIO");

        // Assert
        assertEquals("Amonestación no encontrada.", resultado);
        verify(amonestacionRepository, never()).deleteById(any());
    }
}

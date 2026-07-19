package com.biblioteca.security;

import com.biblioteca.model.Usuario;
import com.biblioteca.repository.UsuarioRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de seguridad para CustomUserDetailsService.
 * Aislado de Spring: la clase implementa una interfaz de Spring Security pero se instancia
 * como un POJO normal (new + Mockito), sin levantar contexto ni filtros.
 * Cubre hallazgo A7 del análisis de Fase 1.
 */
@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    // Doble: STUB (findByCorreo devuelve un Usuario fijo)
    // Verifica: ISO27001 A.5.17 (información de autenticación); ASVS V2.4.1
    // (el UserDetails construido debe reflejar exactamente el hash y el rol almacenados)
    @Test
    void loadUserByUsername_usuarioExistente_devuelveUserDetailsConHashYRolCorrectos() {
        // Arrange
        Usuario usuario = new Usuario();
        usuario.setCorreo("lucia@test.com");
        usuario.setContrasena("$2a$10$hashSimuladoDePrueba");
        usuario.setRol("BIBLIOTECARIO");
        when(usuarioRepository.findByCorreo("lucia@test.com")).thenReturn(Optional.of(usuario));

        // Act
        UserDetails userDetails = customUserDetailsService.loadUserByUsername("lucia@test.com");

        // Assert
        assertEquals("lucia@test.com", userDetails.getUsername());
        assertEquals("$2a$10$hashSimuladoDePrueba", userDetails.getPassword());
        assertEquals(1, userDetails.getAuthorities().size());
        GrantedAuthority authority = userDetails.getAuthorities().iterator().next();
        assertEquals("BIBLIOTECARIO", authority.getAuthority());
    }

    // Doble: STUB (findByCorreo devuelve Optional.empty())
    // Verifica: ASVS V2.2.2 (no revelar si la cuenta existe mediante una excepción distinta)
    @Test
    void loadUserByUsername_usuarioInexistente_lanzaUsernameNotFoundException() {
        // Arrange
        when(usuarioRepository.findByCorreo("fantasma@test.com")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(UsernameNotFoundException.class,
                () -> customUserDetailsService.loadUserByUsername("fantasma@test.com"));
    }
}

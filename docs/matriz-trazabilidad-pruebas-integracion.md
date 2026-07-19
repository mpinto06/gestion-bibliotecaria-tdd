# Matriz de Trazabilidad — Pruebas de Integración de Seguridad

> Complementa a `docs/matriz-trazabilidad-pruebas-unitarias.md`. Esta ronda cubre exclusivamente
> lo que la ronda unitaria dejó fuera por depender del contexto de Spring: reglas de
> `SecurityConfig` a nivel de filtro HTTP, protección CSRF, y Bean Validation (`@Valid`) en los
> `@RequestBody`. No repite autorización escrita a mano en código, IDOR por comparación de IDs,
> ni serialización de contraseñas — eso ya está cubierto y en verde/rojo documentado en la matriz
> unitaria.

Fecha: 2026-07-19

## Resumen ejecutivo

| Métrica | Valor |
|---|---|
| Clases de test creadas | 4 |
| Ejecuciones totales | 29 |
| Ejecuciones en verde | 11 |
| Ejecuciones en rojo (defectos reales) | 18 |
| Defectos nuevos detectados solo por esta ronda (no vistos en la unitaria) | 3 (regla `DELETE /api/libros/isbn/**` inalcanzable, `/api/login` JSON como código muerto, ausencia total de Bean Validation) |

### Archivos de test creados

- `src/test/java/com/biblioteca/integration/SecurityConfigAccessRulesTest.java`
- `src/test/java/com/biblioteca/integration/CsrfProtectionTest.java`
- `src/test/java/com/biblioteca/integration/BeanValidationRequestBodyTest.java`
- `src/test/java/com/biblioteca/integration/PrivilegeEscalationEndToEndTest.java`

### Configuración agregada

- `pom.xml`: `spring-security-test:6.4.5`, `spring-boot-starter-validation:3.4.5`,
  `com.h2database:h2:2.3.232` (versiones fijadas explícitamente para que coincidan con las que
  gestiona `spring-boot-starter-parent:3.4.5`).
- `src/test/resources/application-test.properties`: datasource H2 en memoria, modo de
  compatibilidad MySQL, `ddl-auto=create-drop`. Todas las clases de esta ronda usan
  `@ActiveProfiles("test")`.

---

## 1. `SecurityConfigAccessRulesTest` — 13 ejecuciones, 9 verdes, 4 rojas

**Tipo:** `@WebMvcTest(Controller.class)` + `@Import(SecurityConfig.class)`. Solo se necesita la
capa web + la cadena real de filtros de seguridad; los 6 servicios se mockean con
`@MockitoBean` porque no hace falta persistencia para verificar reglas de acceso HTTP.
`SecurityConfig` se importa explícitamente porque un slice `@WebMvcTest` no lo detecta
automáticamente (no es un `@Controller`/`@ControllerAdvice`).

| Test | ISO 25010 | ISO 27001 | OWASP ASVS | Resultado | Defecto documentado |
|---|---|---|---|---|---|
| `getLibros_sinAutenticar_esAccesible_permitAll` | — | — | V4.1.1 | 🟢 Verde | — |
| `postRegistroUsuario_sinAutenticar_esAccesible_permitAll` | — | — | V4.1.1 | 🟢 Verde | — |
| `getResenasPorLibro_sinAutenticar_esAccesible_permitAllSoloGet` | — | — | V4.1.1 | 🟢 Verde | — |
| `postResenas_sinAutenticar_esRechazada` | — | — | V4.1.1 | 🟢 Verde | — |
| `postResenas_autenticadoConCualquierRol_esAceptada` (×2: `USUARIO`, `BIBLIOTECARIO`) | — | — | V4.1.1 | 🟢 Verde | — |
| `postPrestar_conRolUsuario_esRechazadaPorElFiltro` | — | A.8.2 | V4.1.3 | 🟢 Verde | — |
| `postPrestar_conRolBibliotecario_esAceptadaPorElFiltro` | — | A.8.2 | V4.1.1 | 🟢 Verde | — |
| `getPrestamos_conRolUsuario_esRechazadaPorElFiltro` | — | A.8.2 | V4.1.3 | 🟢 Verde | — |
| `deleteLibroPorIsbn_DEFECTO_reglaPermitAllPrevaleceSobreReglaDeRolEspecifica` | Integridad | A.8.2 | V4.1.1 | 🔴 Rojo | **Sí (nuevo)** — la regla `.requestMatchers("/api/libros","/api/libros/**").permitAll()` se declara antes que la regla específica de `DELETE` con `hasAuthority("BIBLIOTECARIO")`; Spring Security evalúa en orden y aplica la primera coincidencia, dejando la regla de rol inalcanzable |
| `putVerificarAmonestacion_DEFECTO_rolUsuarioNoDeberiaPoderVerificar` | Integridad/Autenticidad | A.8.2, A.5.15 | V4.1.3 | 🔴 Rojo | **Sí (B7, confirmado a nivel HTTP)** |
| `getTodasAmonestaciones_DEFECTO_rolUsuarioNoDeberiaVerTodas` | Confidencialidad | A.8.2, A.8.3 | V4.1.3 | 🔴 Rojo | **Sí (B8, confirmado a nivel HTTP)** |
| `postLogin_conCuerpoJson_DEFECTO_nuncaInvocaAlControllerReal` | Autenticidad | A.5.17, A.8.26 | V2.2.2, V4.1.1 | 🔴 Rojo | **Sí (nuevo)** — `formLogin().loginProcessingUrl("/api/login")` intercepta cualquier POST a esa URL antes del `DispatcherServlet` y espera parámetros de formulario, no el JSON `{correo,contrasena}`; `Controller.loginUsuario` es código muerto en producción |

## 2. `CsrfProtectionTest` — 2 ejecuciones, 1 verde, 1 roja

**Tipo:** `@WebMvcTest(Controller.class)` + `@Import(SecurityConfig.class)`. Mismo motivo que la
clase anterior: solo se necesita observar si el `CsrfFilter` está presente en la cadena real.

| Test | ISO 25010 | ISO 27001 | OWASP ASVS | Resultado | Defecto documentado |
|---|---|---|---|---|---|
| `postResenas_DEFECTO_sinTokenCsrfDeberiaSerRechazada` | Integridad | A.8.26 | V4.2.2 | 🔴 Rojo | **Sí** — `csrf().disable()` elimina el `CsrfFilter`; una escritura autenticada sin token se acepta, cuando la app usa autenticación por sesión/cookie (`JSESSIONID`) y debería exigirlo |
| `postResenas_conTokenCsrfExplicito_esAceptada` | — | — | V4.2.2 | 🟢 Verde | — (control positivo/comparación) |

## 3. `BeanValidationRequestBodyTest` — 12 ejecuciones, 0 verdes, 12 rojas

**Tipo:** `@WebMvcTest(Controller.class)` + `@Import(SecurityConfig.class)`. `@Valid` se dispara
en el binding de Spring MVC antes de tocar el servicio; no requiere persistencia.

| Test | DTO/Entidad | ISO 25010 | ISO 27001 | OWASP ASVS | Resultado | Defecto documentado |
|---|---|---|---|---|---|---|
| `postRegistroUsuario_DEFECTO_aceptaPayloadInvalido` (×4: nombre vacío, correo vacío, correo con formato inválido, contraseña vacía) | `Usuario` | Integridad | A.8.26 | V5.1.3 | 🔴 Rojo | **Sí** — sin `@NotBlank`/`@Email` ni `@Valid`, se acepta cualquier dato |
| `putLibroPorIsbn_DEFECTO_aceptaPayloadInvalido` (×2: título vacío, cantidad negativa) | `Libro` | Integridad | A.8.26 | V5.1.3 | 🔴 Rojo | **Sí** |
| `postResenas_DEFECTO_aceptaPayloadInvalido` (×2: `libroId` nulo, texto vacío) | `ResenaRequest` | Integridad | A.8.26 | V5.1.3 | 🔴 Rojo | **Sí** |
| `postComentariosResena_DEFECTO_aceptaPayloadInvalido` (×2: `resenaId` nulo, texto vacío) | `ComentarioResenaRequest` | Integridad | A.8.26 | V5.1.3 | 🔴 Rojo | **Sí** |
| `postPrestar_DEFECTO_aceptaPayloadInvalido` (×2: correo vacío, ISBN nulo) | `PrestamoRequest` | Integridad | A.8.26 | V5.1.3 | 🔴 Rojo | **Sí** |

## 4. `PrivilegeEscalationEndToEndTest` — 2 ejecuciones, 1 verde, 1 roja

**Tipo:** `@SpringBootTest(webEnvironment = MOCK)` + `@AutoConfigureMockMvc`. Única clase de esta
ronda con contexto completo: se necesita persistencia real (JPA + H2) para demostrar el impacto
de principio a fin (HTTP → filtros → controller → service → repositorio real), no solo que el
servicio lo permite en aislamiento (eso ya lo prueba `UsuarioServiceTest` a nivel unitario).

| Test | ISO 25010 | ISO 27001 | OWASP ASVS | Resultado | Defecto documentado |
|---|---|---|---|---|---|
| `registro_sinAutenticar_DEFECTO_persisteRolBibliotecarioEnBaseDeDatosReal` | Integridad/Autenticidad | A.8.2, A.5.15 | V4.1.5, V4.2.1 | 🔴 Rojo | **Sí (A3, confirmado end-to-end)** — un cliente HTTP no autenticado registra un usuario con `rol:"BIBLIOTECARIO"` y ese rol queda realmente persistido en la base de datos |
| `registro_sinRolEspecificado_persisteRolUsuarioPorDefectoEnBaseDeDatosReal` | Integridad | A.8.2 | V4.1.3 | 🟢 Verde | — (control positivo) |

---

## Confirmación de no duplicación con la ronda unitaria

Ningún test de esta ronda repite lo ya verificado en `docs/matriz-trazabilidad-pruebas-unitarias.md`:

- **Autorización escrita a mano en servicios** (`LibroService`, `PrestamoService`,
  `AmonestacionService` comparando `rol` por `.equals`) — cubierta solo a nivel unitario; aquí
  únicamente se prueba que el **filtro HTTP** delante de esos servicios aplica (o no) la regla
  declarada en `SecurityConfig`, algo que un test unitario no puede observar.
- **IDOR por comparación de IDs** (`editarResena`, `eliminarResena`, `pagarAmonestacion`, etc.) —
  cubierta con mocks a nivel unitario en `ControllerAuthorizationTest`; no se repite aquí.
- **Serialización de contraseña** (`Usuario`/`Prestamo` sin `@JsonIgnore`) — cubierta con Jackson
  puro en `ModelSerializationTest`; no se repite aquí.
- **Escalada de privilegios en `UsuarioService.registrarUsuario`** — el hallazgo A3 se probó en
  aislamiento a nivel unitario (`UsuarioServiceTest`); aquí se agrega la confirmación end-to-end
  con persistencia real, que es información adicional (impacto real), no una repetición.
- Los hallazgos B7 y B8 se probaron a nivel unitario mediante **introspección de firma de método**
  (ausencia de parámetro `Authentication`); aquí se confirma el **efecto real a nivel HTTP** (código
  de estado devuelto), que es la pieza que el test unitario no podía observar por definición.

## Resumen final

- **29 ejecuciones** en esta ronda: **11 verdes**, **18 rojas**.
- **18 rojas documentan 8 defectos reales distintos**:
  1. `verificarAmonestacion` sin control de rol (B7, confirmado a nivel HTTP)
  2. `getTodasAmonestaciones` sin control de rol (B8, confirmado a nivel HTTP)
  3. **[Nuevo]** Regla `DELETE /api/libros/isbn/**` inalcanzable por orden de declaración en `SecurityConfig`
  4. **[Nuevo]** `POST /api/login` con JSON nunca ejecuta `Controller.loginUsuario` (interceptado por el filtro de `formLogin`)
  5. **[Nuevo]** CSRF deshabilitado globalmente acepta escrituras sin token
  6. **[Nuevo]** Ausencia total de Bean Validation en 5 DTOs/entidades usados como `@RequestBody`
  7. Escalada de privilegios en registro (A3), confirmada de extremo a extremo con persistencia real en H2
- Sumando ambas rondas: **112 pruebas totales** (83 unitarias + 29 de integración), **73 verdes**,
  **38 rojas** documentando defectos de seguridad reales, sin ningún test ajustado
  artificialmente para pasar.

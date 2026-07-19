# Matriz de Trazabilidad — Pruebas Unitarias de Seguridad

> Este documento cubre **únicamente pruebas unitarias** (JUnit 5 + Mockito, sin contexto de
> Spring, sin `@SpringBootTest`/`@WebMvcTest`/`MockMvc`). Las reglas de `SecurityConfig`
> (`.hasAuthority`, `permitAll`, CSRF) y la validación `@Valid` de los `@RequestBody` requieren
> contexto de Spring y quedan fuera de esta ronda — ver la sección **Pendiente para pruebas de
> integración** al final. Un documento equivalente para pruebas de integración se generará como
> `docs/matriz-trazabilidad-pruebas-integracion.md`.

Fecha: 2026-07-19
Alcance: `SecurityConfig`, `Controller`, `Service`, `Repository`, `Model`, `DTO` del backend
`gestion-bibliotecaria-tdd`.

## Resumen ejecutivo

| Métrica | Valor |
|---|---|
| Clases de test creadas | 7 |
| Métodos de test (incluye `@ParameterizedTest` como 1 método) | 63 |
| Ejecuciones totales (`mvn test`, incluye cada caso parametrizado) | 82 (+1 test preexistente de contexto Spring, fuera de este alcance) |
| Ejecuciones en verde | 62 |
| Ejecuciones en rojo (defectos reales documentados) | 20 |
| Métodos de test que documentan un defecto | 12 |

### Archivos de test creados

- `src/test/java/com/biblioteca/service/UsuarioServiceTest.java`
- `src/test/java/com/biblioteca/security/CustomUserDetailsServiceTest.java`
- `src/test/java/com/biblioteca/service/LibroServiceTest.java`
- `src/test/java/com/biblioteca/service/PrestamoServiceTest.java`
- `src/test/java/com/biblioteca/service/AmonestacionServiceTest.java`
- `src/test/java/com/biblioteca/controller/ControllerAuthorizationTest.java`
- `src/test/java/com/biblioteca/model/ModelSerializationTest.java`

### Nota sobre el entorno de ejecución

Para poder correr `mvn test` en esta máquina fue necesario un ajuste de **configuración de build**
(no de código de negocio) en `pom.xml`, debido a que el JDK 21 instalado no tiene una cadena de
certificados válida hacia Maven Central y el JDK 17 no traía en caché la versión exacta de
`maven-surefire-plugin`/`junit-platform-launcher` que exige `spring-boot-starter-parent`:

- Se fijó `maven-surefire-plugin` en `3.5.4` (versión ya cacheada localmente).
- Se sobrescribió la property `junit-jupiter.version` a `5.12.2` para alinear `junit-jupiter` y
  `junit-platform-launcher` a versiones mutuamente compatibles ya cacheadas.
- Ejecución con `JAVA_HOME` apuntando al JDK 17 y `-Dmaven.compiler.release=17` (el `pom.xml`
  declara `java.version=21`, pero el JDK 21 local no puede compilar sin acceso a red).

---

## UsuarioServiceTest (14 métodos / 21 ejecuciones)

| Test | Patrón/Doble usado | ISO 25010 | ISO 27001 | OWASP ASVS | Resultado | Defecto documentado |
|---|---|---|---|---|---|---|
| `registrarUsuario_debeAlmacenarContrasenaHasheadaConBCrypt_V2_4_1` | Stub + ArgumentCaptor | Confidencialidad | A.8.28 | V2.4.1 | 🟢 Verde | — |
| `registrarUsuario_correoYaRegistrado_noPersisteYRechaza` | Stub + mock/verify(never) | Integridad | A.8.2 | V4.1.3 | 🟢 Verde | — |
| `registrarUsuario_sinRolEspecificado_asignaRolUsuarioPorDefecto` | Stub + ArgumentCaptor | Integridad | A.8.2 | V4.1.3 | 🟢 Verde | — |
| `registrarUsuario_DEFECTO_permiteEscaladaDePrivilegiosPorRolArbitrario` (×3: `BIBLIOTECARIO`/`ADMIN`/`SUPERUSER`) | Stub + ArgumentCaptor | Integridad / Autenticidad | A.8.2, A.5.15 | V4.1.5, V4.2.1 | 🔴 Rojo | **Sí** — registro público permite autoasignarse rol privilegiado (mass assignment / escalada de privilegios) |
| `registrarUsuario_DEFECTO_aceptaContrasenaDebilSinValidarFortaleza` (×4) | Stub + mock/verify(never) | Confidencialidad | A.8.28 | V2.1.1 | 🔴 Rojo | **Sí** — no hay validación de longitud/fortaleza de contraseña |
| `autenticarYObtenerUsuario_correoInexistente_devuelveNull` | Stub | Autenticidad | A.5.17 | V2.2.2 | 🟢 Verde | — |
| `autenticarYObtenerUsuario_contrasenaIncorrecta_devuelveNullSinDistinguirCausa` | Stub (hash BCrypt real) | Autenticidad | A.5.17 | V2.2.2 | 🟢 Verde | — |
| `autenticarYObtenerUsuario_credencialesCorrectas_devuelveUsuario` | Stub (hash BCrypt real) | Autenticidad | A.5.17 | V2.4.1 | 🟢 Verde | — |
| `cambiarContrasena_actualIncorrecta_rechazaYNoPersiste` | Stub + mock/verify(never) | Autenticidad | A.8.28 | V2.1.6 | 🟢 Verde | — |
| `cambiarContrasena_actualCorrecta_actualizaHash` | Stub + ArgumentCaptor | Confidencialidad | A.8.28 | V2.1.6, V2.4.1 | 🟢 Verde | — |
| `cambiarContrasena_DEFECTO_aceptaContrasenaNuevaDebil` (×3) | Stub + mock/verify(never) | Confidencialidad | A.8.28 | V2.1.1 | 🔴 Rojo | **Sí** — no valida fortaleza de la contraseña nueva |
| `eliminarUsuario_conPrestamosActivos_lanzaExcepcionYNoElimina` | Stub + assertThrows | Integridad | — | V11.1 | 🟢 Verde | — |
| `eliminarUsuario_conAmonestacionesPendientes_lanzaExcepcionYNoElimina` | Stub + assertThrows | Integridad | — | V11.1 | 🟢 Verde | — |
| `eliminarUsuario_sinBloqueos_eliminaComentariosResenasYUsuario` | Stub + mock/verify | Integridad | — | V11.1 | 🟢 Verde | — |

## CustomUserDetailsServiceTest (2 métodos / 2 ejecuciones)

| Test | Patrón/Doble usado | ISO 25010 | ISO 27001 | OWASP ASVS | Resultado | Defecto documentado |
|---|---|---|---|---|---|---|
| `loadUserByUsername_usuarioExistente_devuelveUserDetailsConHashYRolCorrectos` | Stub | Autenticidad | A.5.17 | V2.4.1 | 🟢 Verde | — |
| `loadUserByUsername_usuarioInexistente_lanzaUsernameNotFoundException` | Stub + assertThrows | Autenticidad | A.5.17 | V2.2.2 | 🟢 Verde | — |

## LibroServiceTest (12 métodos / 19 ejecuciones)

| Test | Patrón/Doble usado | ISO 25010 | ISO 27001 | OWASP ASVS | Resultado | Defecto documentado |
|---|---|---|---|---|---|---|
| `registrarLibroConImagen_conRolBibliotecario_persisteLibro` | Stub + ArgumentCaptor | Integridad | A.8.2 | V4.1.1 | 🟢 Verde | — |
| `registrarLibroConImagen_conRolNoBibliotecario_lanzaAccessDeniedException` (×3) | Stub + assertThrows | Integridad | A.8.2 | V4.1.3 | 🟢 Verde | — |
| `registrarLibroConImagen_isbnDuplicado_rechazaYNoPersiste` | Stub + mock/verify(never) | Integridad | — | V5.1.3 | 🟢 Verde | — |
| `registrarLibroConImagen_cantidadNoPositiva_rechazaYNoPersiste` (×3) | Stub + mock/verify(never) | Integridad | — | V5.1.3 | 🟢 Verde | — |
| `registrarLibroConImagen_DEFECTO_isbnNegativoBurlaValidacionDeLongitud` | Stub + mock/verify(never) | Integridad | A.8.26 | V5.1.3 | 🔴 Rojo | **Sí** — un ISBN negativo de 12 dígitos de magnitud burla el chequeo de longitud (13 chars con el signo) |
| `actualizarLibroPorIsbn_conRolBibliotecario_actualizaCamposPermitidos` | Stub + ArgumentCaptor | Integridad | A.8.2 | V4.1.1 | 🟢 Verde | — |
| `actualizarLibroPorIsbn_conRolNoBibliotecario_lanzaAccessDeniedException` (×2) | Stub + assertThrows | Integridad | A.8.2 | V4.1.3 | 🟢 Verde | — |
| `actualizarLibroPorIsbn_DEFECTO_permiteCantidadNegativa` (×2) | Stub + mock/verify(never) | Integridad | A.8.26 | V5.1.3 | 🔴 Rojo | **Sí** — el PUT no repite la validación `cantidad>0` que sí existe en el alta |
| `actualizarLibroPorIsbn_libroNoEncontrado_lanzaExcepcion` | Stub + assertThrows | Integridad | — | — | 🟢 Verde | — |
| `eliminarLibroPorIsbn_conRolBibliotecario_eliminaLibro` | Stub + mock/verify | Integridad | A.8.2 | V4.1.1 | 🟢 Verde | — |
| `eliminarLibroPorIsbn_conRolNoBibliotecario_lanzaAccessDeniedException` (×2) | Stub + assertThrows | Integridad | A.8.2 | V4.1.3 | 🟢 Verde | — |
| `eliminarLibroPorIsbn_libroNoEncontrado_lanzaExcepcion` | Stub + assertThrows | Integridad | — | — | 🟢 Verde | — |

## PrestamoServiceTest (15 métodos / 18 ejecuciones)

| Test | Patrón/Doble usado | ISO 25010 | ISO 27001 | OWASP ASVS | Resultado | Defecto documentado |
|---|---|---|---|---|---|---|
| `crearPrestamo_conRolUsuarioYStockDisponible_registraYDecrementaStock` | Stub + mock/verify + ArgumentCaptor | Integridad | A.8.2 | V4.1.1 | 🟢 Verde | — |
| `crearPrestamo_conRolNoUsuario_rechazaYNoPersiste` (×3) | Stub + mock/verify(never) | Integridad | A.8.2 | V4.1.3 | 🟢 Verde | — |
| `crearPrestamo_conDosPrestamosActivos_rechazaYNoPersiste` | Stub + mock/verify(never) | Integridad | — | V11.1 | 🟢 Verde | — |
| `crearPrestamo_conAmonestacionesPendientes_rechazaYNoPersiste` | Stub + mock/verify(never) | Integridad | — | V11.1 | 🟢 Verde | — |
| `crearPrestamo_libroSinStock_rechazaYNoPersiste` | Stub + mock/verify(never) | Integridad | — | V11.1 | 🟢 Verde | — |
| `crearPrestamo_usuarioNoRegistrado_rechaza` | Stub | Integridad | — | — | 🟢 Verde | — |
| `devolverPrestamo_yaDevuelto_rechazaYNoDuplicaEfectos` | Stub + mock/verify(never) | Integridad | — | V11.1 | 🟢 Verde | — |
| `devolverPrestamo_vencido_generaAmonestacion` | Stub + ArgumentCaptor | Integridad | — | V11.1 | 🟢 Verde | — |
| `devolverPrestamo_aTiempo_noGeneraAmonestacion` | Stub + mock/verify(never) | Integridad | — | V11.1 | 🟢 Verde | — |
| `renovarPrestamo_conRolNoBibliotecario_rechaza` (×2) | mock/verify(never) puro | Integridad | A.8.2 | V4.1.1 | 🟢 Verde | — |
| `renovarPrestamo_noEncontrado_rechaza` | Stub | Integridad | — | — | 🟢 Verde | — |
| `renovarPrestamo_noFinalizado_rechaza` | Stub | Integridad | — | V11.1 | 🟢 Verde | — |
| `renovarPrestamo_conAmonestacionesPendientes_rechaza` | Stub + mock/verify(never) | Integridad | — | V11.1 | 🟢 Verde | — |
| `renovarPrestamo_conStockDisponible_actualizaFechaLimiteYEstado` | Stub + ArgumentCaptor | Integridad | — | — | 🟢 Verde | — |
| `renovarPrestamo_DEFECTO_decrementaStockSinVerificarDisponibilidad` | Stub + ArgumentCaptor | Integridad | A.8.26 | V11.1.4 | 🔴 Rojo | **Sí** — decrementa inventario sin comprobar disponibilidad; deja `cantidad` en -1 |

## AmonestacionServiceTest (3 métodos / 5 ejecuciones)

| Test | Patrón/Doble usado | ISO 25010 | ISO 27001 | OWASP ASVS | Resultado | Defecto documentado |
|---|---|---|---|---|---|---|
| `eliminarAmonestacion_conRolBibliotecario_elimina` | Stub + mock/verify | Integridad | A.8.2 | V4.1.1 | 🟢 Verde | — |
| `eliminarAmonestacion_conRolNoBibliotecario_rechazaYNoConsultaRepositorio` (×3) | mock/verify(never) puro | Integridad | A.8.2 | V4.1.3 | 🟢 Verde | — |
| `eliminarAmonestacion_noEncontrada_rechaza` | Stub + mock/verify(never) | Integridad | — | — | 🟢 Verde | — |

## ControllerAuthorizationTest (14 métodos / 14 ejecuciones)

Instanciación pura (`new Controller()`) con inyección de mocks vía `java.lang.reflect.Field`, sin
Spring. Los defectos B7/B8/C4/C5 se prueban por **introspección de firma del método**
(`Method.getParameterTypes()`), porque el defecto es la *ausencia* de un parámetro de seguridad —
no hay rama de código ejecutable que estubear ni interacción que verificar.

| Test | Patrón/Doble usado | ISO 25010 | ISO 27001 | OWASP ASVS | Resultado | Defecto documentado |
|---|---|---|---|---|---|---|
| `editarResena_usuarioDistintoAlAutor_devuelve403` | Stub | Autenticidad | — | V4.2.1 | 🟢 Verde | — |
| `editarResena_usuarioAutor_permiteEdicion` | Stub + mock/verify | Autenticidad | — | V4.2.1 | 🟢 Verde | — |
| `eliminarResena_usuarioDistintoAlAutor_devuelve403` | Stub + mock/verify(never) | Autenticidad | — | V4.2.1 | 🟢 Verde | — |
| `editarComentarioResena_usuarioDistintoAlAutor_devuelve403` | Stub + mock/verify(never) | Autenticidad | — | V4.2.1 | 🟢 Verde | — |
| `eliminarComentarioResena_usuarioDistintoAlAutor_devuelve403` | Stub + mock/verify(never) | Autenticidad | — | V4.2.1 | 🟢 Verde | — |
| `pagarAmonestacion_usuarioDistintoAlPropietario_devuelve403` | Stub + mock/verify(never) | Autenticidad | — | V4.2.1 | 🟢 Verde | — |
| `pagarAmonestacion_usuarioPropietario_permitePago` | Stub + mock/verify | Autenticidad | — | V4.2.1 | 🟢 Verde | — |
| `verificarAmonestacion_DEFECTO_metodoNoValidaRolNiPropietario` | Introspección por reflexión (sin mocks) | Integridad / Autenticidad | A.8.2, A.5.15 | V4.1.3 | 🔴 Rojo | **Sí** — cualquier usuario autenticado (no solo BIBLIOTECARIO) puede verificar amonestaciones ajenas |
| `getTodasAmonestaciones_DEFECTO_metodoNoValidaRol` | Introspección por reflexión (sin mocks) | Confidencialidad | A.8.2, A.8.3 | V4.1.3 | 🔴 Rojo | **Sí** — expone amonestaciones (datos financieros) de todos los usuarios a cualquier autenticado |
| `crearResena_DEFECTO_noCruzaUsuarioIdConAutenticado` | Introspección por reflexión (sin mocks) | Autenticidad | — | V4.2.1 | 🔴 Rojo | **Sí** — IDOR en creación: `usuarioId` se toma del body sin cruzar contra el autenticado |
| `crearComentarioResena_DEFECTO_noCruzaUsuarioIdConAutenticado` | Introspección por reflexión (sin mocks) | Autenticidad | — | V4.2.1 | 🔴 Rojo | **Sí** — mismo patrón que el anterior, en comentarios de reseña |
| `loginUsuario_credencialesValidas_noExponeContrasenaEnRespuesta` | Stub | Confidencialidad | A.8.3 | V8.3.4 | 🟢 Verde | — |
| `loginUsuario_credencialesInvalidas_devuelve401MensajeGenerico` | Stub | Autenticidad | A.5.17 | V2.2.2 | 🟢 Verde | — |
| `getUsuarioAutenticado_devuelveUsuarioSinContrasena` | Stub | Confidencialidad | A.8.3 | V8.3.4 | 🟢 Verde | — |

## ModelSerializationTest (3 métodos / 3 ejecuciones)

Usa `com.fasterxml.jackson.databind.ObjectMapper` puro (librería, no contexto Spring) con
`JavaTimeModule` registrado manualmente.

| Test | Patrón/Doble usado | ISO 25010 | ISO 27001 | OWASP ASVS | Resultado | Defecto documentado |
|---|---|---|---|---|---|---|
| `usuario_DEFECTO_serializacionExponeHashDeContrasena` | Ninguno (serialización real) | Confidencialidad | A.8.3 | V8.3.4 | 🔴 Rojo | **Sí** — `Usuario.contrasena` no tiene `@JsonIgnore`; se serializa el hash BCrypt |
| `prestamo_DEFECTO_serializacionExponeHashDeContrasenaDelUsuarioAnidado` | Ninguno (serialización real) | Confidencialidad | A.8.3 | V8.3.4 | 🔴 Rojo | **Sí** — `Prestamo.usuario` no tiene `@JsonIgnoreProperties` (a diferencia de `Amonestacion.usuario`); expone el hash en `GET /api/prestamos` |
| `amonestacion_serializacionOcultaContrasenaDelUsuarioAnidado` | Ninguno (serialización real) | Confidencialidad | A.8.3 | V8.3.4 | 🟢 Verde | — (control positivo: `@JsonIgnoreProperties({"contrasena"})` sí funciona aquí) |

---

## Resumen final

- **83 ejecuciones totales** (`mvn test`, suite completa), de las cuales 1 corresponde al test
  preexistente `__1.spring_boot.GestionBibliotecariaApplicationTests` (`@SpringBootTest`, fuera
  del alcance de esta entrega — no se tocó).
- **82 ejecuciones dentro del alcance de esta ronda**: **62 verdes**, **20 rojas**.
- **12 métodos de test** (algunos parametrizados) documentan **9 defectos de seguridad reales**
  distintos:
  1. Escalada de privilegios en el registro público (rol arbitrario) — `UsuarioServiceTest`
  2. Sin validación de fortaleza de contraseña en registro — `UsuarioServiceTest`
  3. Sin validación de fortaleza de contraseña en cambio de clave — `UsuarioServiceTest`
  4. ISBN negativo burla la validación de longitud — `LibroServiceTest`
  5. `actualizarLibroPorIsbn` permite `cantidad` negativa — `LibroServiceTest`
  6. `renovarPrestamo` decrementa stock sin verificar disponibilidad — `PrestamoServiceTest`
  7. `verificarAmonestacion` sin control de rol — `ControllerAuthorizationTest`
  8. `getTodasAmonestaciones` sin control de rol — `ControllerAuthorizationTest`
  9. IDOR en creación de reseñas/comentarios (`crearResena`/`crearComentarioResena`) — `ControllerAuthorizationTest`
  10. `Usuario`/`Prestamo` exponen el hash de la contraseña al serializar — `ModelSerializationTest`

  (nota: el punto 9 son dos métodos de test para el mismo patrón de defecto, y el punto 10 son
  dos métodos para la misma causa raíz; por eso 12 métodos documentan 9 causas raíz distintas)

---

## Pendiente para pruebas de integración

Hallazgos de seguridad identificados en la Fase 1 que **no** se pudieron convertir en pruebas
unitarias puras porque dependen de contexto de Spring (filtros, `@Valid`, arranque de
`SecurityFilterChain`). Quedan como candidatos para
`docs/matriz-trazabilidad-pruebas-integracion.md`:

| Hallazgo | Por qué requiere integración |
|---|---|
| Reglas de `SecurityConfig` (`.hasAuthority("BIBLIOTECARIO")`, `permitAll`, `anyRequest().authenticated()`) | Solo se pueden ejercitar levantando el `SecurityFilterChain` real (`@SpringBootTest` + `MockMvc`, o `@WebMvcTest` con `spring-security-test`) |
| Confirmación HTTP de que `verificarAmonestacion` y `getTodasAmonestaciones` son alcanzables por cualquier rol autenticado (no solo BIBLIOTECARIO) | La prueba unitaria (B7/B8) solo demuestra la ausencia del parámetro `Authentication` en el método; el impacto real (qué rol HTTP puede llegar a ese endpoint) depende de las reglas de `SecurityConfig` |
| CSRF deshabilitado globalmente (`csrf.disable()`) | Es una configuración de filtro HTTP, no código de negocio invocable de forma aislada |
| Ausencia de `@Valid`/Bean Validation en los `@RequestBody` (`Usuario`, `ResenaRequest`, `ComentarioResenaRequest`, `PrestamoRequest`) — sin anotaciones `@NotNull`/`@Email`/`@Size` | Su disparo depende del pipeline de `HandlerMethodArgumentResolver` de Spring MVC; sin él, no hay ninguna rama de código que unitariamente falle o pase |
| Rate limiting / anti-automatización en `/api/login` (ASVS V2.2.1) | No existe en código; sería un filtro o interceptor, no lógica de servicio |
| Comportamiento real end-to-end de la escalada de privilegios (A3) contra el endpoint `POST /api/usuarios/registro` con `permitAll` | La prueba unitaria ya prueba la causa raíz en `UsuarioService`; falta la prueba de extremo a extremo confirmando que un cliente no autenticado puede explotarlo vía HTTP |

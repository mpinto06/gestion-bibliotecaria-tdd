# Reporte de Pruebas de Integración de Seguridad — Detalle por Test

> Complementa a `docs/matriz-trazabilidad-pruebas-integracion.md` (tabla compacta) y sigue el
> mismo formato que `docs/reporte-pruebas-seguridad.md` (ronda unitaria). Explica, test por test,
> qué hace exactamente, qué requisito de seguridad verifica y por qué quedó verde o rojo. Última
> ejecución: `mvn test` → **29 pruebas de esta ronda, 11 verdes, 18 rojas**.

Leyenda: 🟢 Verde (pasa, control funciona) · 🔴 Rojo (falla a propósito, documenta un defecto real)

---

## 1. `SecurityConfigAccessRulesIntegrationTest` — 13 ejecuciones, 9 verdes, 4 rojas

`@WebMvcTest(Controller.class)` + `@Import(SecurityConfig.class)`: carga la capa web y la cadena
real de filtros de seguridad; los 6 servicios están mockeados con `@MockitoBean` porque estas
pruebas no necesitan persistencia, solo observar qué deja pasar el filtro HTTP.

### 🟢 `getLibros_sinAutenticar_esAccesible_permitAll`
**Qué prueba:** un `GET /api/libros` sin ningún usuario autenticado responde `200`.
**Requisito:** ASVS V4.1.1 — confirma que la regla `.requestMatchers("/api/libros","/api/libros/**").permitAll()` se cumple tal como está declarada.

### 🟢 `postRegistroUsuario_sinAutenticar_esAccesible_permitAll`
**Qué prueba:** un `POST /api/usuarios/registro` sin autenticar (con token CSRF) responde `200`.
**Requisito:** ASVS V4.1.1 — confirma el `permitAll` explícito sobre esta ruta.

### 🟢 `getResenasPorLibro_sinAutenticar_esAccesible_permitAllSoloGet`
**Qué prueba:** `GET /api/resenas/libro/1` sin autenticar responde `200`.
**Requisito:** ASVS V4.1.1 — confirma el `permitAll` restringido a `GET` sobre `/api/resenas/**`.

### 🟢 `postResenas_sinAutenticar_esRechazada`
**Qué prueba:** un `POST /api/resenas` sin autenticar es rechazado. Como la app usa `formLogin`,
Spring Security no responde `401` directo sino un **redirect 302** hacia la página de login por
defecto; el test verifica `is3xxRedirection()` en vez de asumir un código 4xx, precisamente
porque en un primer intento asumí mal el código y tuve que corregirlo.
**Requisito:** ASVS V4.1.1 — confirma que la regla `POST /api/resenas/** → authenticated()` bloquea a un anónimo (aunque el mecanismo de bloqueo sea un redirect, no un 401/403 plano).

### 🟢 `postResenas_autenticadoConCualquierRol_esAceptada` (×2: `USUARIO`, `BIBLIOTECARIO`)
**Qué prueba:** con cualquiera de los dos roles, un `POST /api/resenas` autenticado responde `200` — la regla solo exige `authenticated()`, no un rol específico.
**Requisito:** ASVS V4.1.1.

### 🟢 `postPrestar_conRolUsuario_esRechazadaPorElFiltro`
**Qué prueba:** un usuario con rol `USUARIO` (no `BIBLIOTECARIO`) que llama `POST /api/prestar` recibe `403` **antes** de tocar el controller/servicio — el filtro HTTP corta la petición.
**Requisito:** ISO27001 A.8.2 · ASVS V4.1.3.

### 🟢 `postPrestar_conRolBibliotecario_esAceptadaPorElFiltro`
**Qué prueba:** con rol `BIBLIOTECARIO`, la misma petición pasa el filtro y llega al controller (`200`, con el service mockeado devolviendo éxito).
**Requisito:** ISO27001 A.8.2 · ASVS V4.1.1.

### 🟢 `getPrestamos_conRolUsuario_esRechazadaPorElFiltro`
**Qué prueba:** `GET /api/prestamos` con rol `USUARIO` recibe `403` por el filtro (`/api/prestamos/**` exige `hasAuthority("BIBLIOTECARIO")`).
**Requisito:** ISO27001 A.8.2 · ASVS V4.1.3.

### 🔴 `deleteLibroPorIsbn_DEFECTO_reglaPermitAllPrevaleceSobreReglaDeRolEspecifica`
**Qué prueba:** un usuario con rol `USUARIO` llama `DELETE /api/libros/isbn/1234567890123` y se verifica que **debería** recibir `403`.
**Requisito:** ISO27001 A.8.2 · ASVS V4.1.1.
**Por qué está rojo:** en `SecurityConfig`, la regla `.requestMatchers("/api/libros","/api/libros/**").permitAll()` se declara *antes* que `.requestMatchers(HttpMethod.DELETE,"/api/libros/isbn/**").hasAuthority("BIBLIOTECARIO")`. Spring Security evalúa las reglas **en el orden en que se declaran** y aplica la primera que coincide; como la regla `permitAll` no filtra por método HTTP, también captura los `DELETE`, dejando la regla de rol como código inalcanzable. Resultado real: el test recibe `200`, no `403`. (Se usa un usuario autenticado con rol `USUARIO`, no un anónimo real, porque para una petición verdaderamente anónima `Authentication` llega `null` al controller y produce un `NullPointerException` antes de completar la operación — el escenario explotable en la práctica es el de un usuario logueado con el rol equivocado.)

### 🔴 `putVerificarAmonestacion_DEFECTO_rolUsuarioNoDeberiaPoderVerificar`
**Qué prueba:** un usuario con rol `USUARIO` llama `PUT /api/amonestaciones-usuario/verificar/1` y se verifica que **debería** recibir `403`.
**Requisito:** ISO27001 A.8.2, A.5.15 · ASVS V4.1.3.
**Por qué está rojo:** confirma el hallazgo **B7** (ya visto a nivel unitario por ausencia del parámetro `Authentication`) directamente contra el filtro HTTP real: como esta ruta no tiene ninguna regla `.hasAuthority` en `SecurityConfig`, solo cae en el catch-all `.anyRequest().authenticated()`, así que cualquier rol autenticado recibe `200`.

### 🔴 `getTodasAmonestaciones_DEFECTO_rolUsuarioNoDeberiaVerTodas`
**Qué prueba:** lo mismo que el anterior sobre `GET /api/amonestaciones-usuario/todas`.
**Requisito:** ISO27001 A.8.2, A.8.3 · ASVS V4.1.3.
**Por qué está rojo:** confirma el hallazgo **B8** a nivel HTTP — cualquier usuario autenticado puede listar las amonestaciones (datos financieros) de todos los demás usuarios.

### 🔴 `postLogin_conCuerpoJson_DEFECTO_nuncaInvocaAlControllerReal`
**Qué prueba:** envía un `POST /api/login` con el cuerpo JSON `{"correo":...,"contrasena":...}` que espera `Controller.loginUsuario`, y verifica que **debería** dispararse `usuarioService.autenticarYObtenerUsuario(...)`.
**Requisito:** ISO27001 A.5.17, A.8.26 · ASVS V2.2.2, V4.1.1.
**Por qué está rojo:** `SecurityConfig` configura `formLogin().loginProcessingUrl("/api/login")`. El `UsernamePasswordAuthenticationFilter` de Spring Security intercepta **cualquier** `POST` a esa URL antes de que llegue al `DispatcherServlet`, y espera parámetros de formulario `username`/`password` — no el JSON que implementa el controller. El mock `usuarioService` nunca es invocado: `Controller.loginUsuario` (y el control positivo D2 que ya se probó a nivel unitario, "no exponer la contraseña en la respuesta de login") es **código muerto** en producción. Este es uno de los hallazgos nuevos de esta ronda.

---

## 2. `CsrfProtectionIntegrationTest` — 2 ejecuciones, 1 verde, 1 roja

`@WebMvcTest(Controller.class)` + `@Import(SecurityConfig.class)`: misma justificación que la
clase anterior, aquí enfocada específicamente en la presencia/ausencia del `CsrfFilter`.

### 🔴 `postResenas_DEFECTO_sinTokenCsrfDeberiaSerRechazada`
**Qué prueba:** un `POST /api/resenas` autenticado, **deliberadamente sin** `.with(csrf())`, y verifica que **debería** recibir `403`.
**Requisito:** ISO25010 Integridad · ISO27001 A.8.26 · ASVS V4.2.2.
**Por qué está rojo:** `SecurityConfig` llama `.csrf(csrf -> csrf.disable())`, eliminando el `CsrfFilter` de la cadena por completo. La app usa autenticación por sesión/cookie (`JSESSIONID` vía `formLogin`), el escenario donde la protección CSRF sí aplica — pero la petición sin token se acepta igual (`200`), no se rechaza.

### 🟢 `postResenas_conTokenCsrfExplicito_esAceptada`
**Qué prueba:** la misma petición, esta vez **con** `.with(csrf())`, responde `200`.
**Requisito:** ASVS V4.2.2 (control de comparación, no positivo per se: al estar el filtro deshabilitado, el token es irrelevante — este test solo confirma que incluirlo no rompe nada).

---

## 3. `BeanValidationRequestBodyIntegrationTest` — 12 ejecuciones, 0 verdes, 12 rojas

`@WebMvcTest(Controller.class)` + `@Import(SecurityConfig.class)`: `@Valid` se dispara en el
binding de Spring MVC antes de invocar el controller, así que no hace falta persistencia. Las 12
ejecuciones fallan porque **ningún** DTO/entidad usado como `@RequestBody` en este backend tiene
anotaciones de Bean Validation ni el controller usa `@Valid` en ningún endpoint.

### 🔴 `postRegistroUsuario_DEFECTO_aceptaPayloadInvalido` (×4)
**Qué prueba:** envía `POST /api/usuarios/registro` con, respectivamente: nombre vacío, correo vacío, correo con formato inválido (`"esto-no-es-un-correo"`) y contraseña vacía. Verifica que **debería** responder `400`.
**Requisito:** ASVS V5.1.3 · ISO27001 A.8.26.
**Por qué está rojo (los 4 casos):** `Usuario` no tiene `@NotBlank`/`@Email`, y `registrarUsuario(@RequestBody Usuario usuario)` no usa `@Valid`. Los 4 payloads inválidos responden `200`.

### 🔴 `putLibroPorIsbn_DEFECTO_aceptaPayloadInvalido` (×2)
**Qué prueba:** `PUT /api/libros/isbn/1234567890123` (con rol `BIBLIOTECARIO`) con título vacío y con `cantidad: -5`. Verifica que **debería** responder `400`.
**Requisito:** ASVS V5.1.3 · ISO27001 A.8.26.
**Por qué está rojo:** `Libro` no tiene anotaciones de validación y `actualizarLibroPorIsbn` no usa `@Valid`. Complementa, a nivel de contrato HTTP, el hallazgo E2 ya visto a nivel unitario (la falta de validación de negocio sobre `cantidad`).

### 🔴 `postResenas_DEFECTO_aceptaPayloadInvalido` (×2)
**Qué prueba:** `POST /api/resenas` con `libroId: null` y con `texto: ""`. Verifica que **debería** responder `400`.
**Requisito:** ASVS V5.1.3.
**Por qué está rojo:** `ResenaRequest` no tiene anotaciones de validación ni `crearResena` usa `@Valid`.

### 🔴 `postComentariosResena_DEFECTO_aceptaPayloadInvalido` (×2)
**Qué prueba:** `POST /api/comentarios-resena` con `resenaId: null` y con `texto: ""`. Verifica que **debería** responder `400`.
**Requisito:** ASVS V5.1.3.
**Por qué está rojo:** mismo patrón que `ResenaRequest`, aplicado a `ComentarioResenaRequest`.

### 🔴 `postPrestar_DEFECTO_aceptaPayloadInvalido` (×2)
**Qué prueba:** `POST /api/prestar` con `correoUsuario: ""` y con `isbn: null`. Verifica que **debería** responder `400`.
**Requisito:** ASVS V5.1.3.
**Por qué está rojo:** `PrestamoRequest` no tiene anotaciones de validación ni `registrarPrestamo` usa `@Valid`.

---

## 4. `PrivilegeEscalationEndToEndIntegrationTest` — 2 ejecuciones, 1 verde, 1 roja

`@SpringBootTest(webEnvironment = MOCK)` + `@AutoConfigureMockMvc`: única clase con contexto
completo (filtros reales + `UsuarioService` real + `UsuarioRepository` real + H2 en memoria),
porque el objetivo es demostrar el impacto de principio a fin, no solo que el service lo permite
en aislamiento (eso ya lo prueba `UsuarioServiceTest`).

### 🔴 `registro_sinAutenticar_DEFECTO_persisteRolBibliotecarioEnBaseDeDatosReal`
**Qué prueba:** hace un `POST /api/usuarios/registro` real (sin autenticar) con `"rol":"BIBLIOTECARIO"` en el JSON, y después consulta el `UsuarioRepository` real para ver qué quedó efectivamente guardado en H2. Verifica que el rol persistido **debería** ser `"USUARIO"`.
**Requisito:** ISO25010 Integridad/Autenticidad · ISO27001 A.8.2, A.5.15 · ASVS V4.1.5, V4.2.1.
**Por qué está rojo:** confirma el hallazgo **A3** de extremo a extremo — el registro responde `200` y, al consultar la base de datos real, el usuario quedó guardado con `rol = "BIBLIOTECARIO"`, el mismo valor que envió un cliente no autenticado. Es la prueba más fuerte de todo el proyecto: no es una suposición sobre el comportamiento del `service`, es la fila real en la tabla `usuarios`.

### 🟢 `registro_sinRolEspecificado_persisteRolUsuarioPorDefectoEnBaseDeDatosReal`
**Qué prueba:** el mismo flujo end-to-end, pero sin enviar el campo `rol` en el JSON. Verifica que el usuario quede persistido con `rol = "USUARIO"`.
**Requisito:** ISO25010 Integridad (control positivo).
**Resultado:** confirma que, cuando el cliente no intenta escalar privilegios, el valor por defecto sí se aplica y persiste correctamente — el problema es específicamente que el servidor confía en el rol *cuando el cliente lo envía explícitamente*.

---

## Tabla resumen de los 8 defectos reales de esta ronda (por causa raíz)

| # | Defecto | Test(s) que lo documentan | Severidad | ¿Nuevo o confirma Fase 1? |
|---|---|---|---|---|
| 1 | Regla `DELETE /api/libros/isbn/**` inalcanzable (orden de reglas en `SecurityConfig`) | `SecurityConfigAccessRulesIntegrationTest` | **Alta** | Nuevo |
| 2 | `POST /api/login` con JSON nunca ejecuta el controller (interceptado por `formLogin`) | `SecurityConfigAccessRulesIntegrationTest` | **Alta** | Nuevo |
| 3 | `verificarAmonestacion` sin control de rol a nivel HTTP | `SecurityConfigAccessRulesIntegrationTest` | **Alta** | Confirma B7 |
| 4 | `getTodasAmonestaciones` sin control de rol a nivel HTTP | `SecurityConfigAccessRulesIntegrationTest` | **Alta** | Confirma B8 |
| 5 | CSRF deshabilitado acepta escrituras sin token | `CsrfProtectionIntegrationTest` | Media | Nuevo |
| 6 | Ausencia total de Bean Validation en 5 DTOs/entidades (`Usuario`, `Libro`, `ResenaRequest`, `ComentarioResenaRequest`, `PrestamoRequest`) | `BeanValidationRequestBodyIntegrationTest` ×12 | Media | Nuevo |
| 7 | Escalada de privilegios en registro, confirmada con persistencia real en BD | `PrivilegeEscalationEndToEndIntegrationTest` | **Crítica** | Confirma A3 (end-to-end) |

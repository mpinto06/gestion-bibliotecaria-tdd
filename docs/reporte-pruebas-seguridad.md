# Reporte de Pruebas Unitarias de Seguridad — Detalle por Test

> Complementa a `docs/matriz-trazabilidad-pruebas-unitarias.md` (tabla compacta). Este documento
> explica, test por test, **qué hace exactamente**, **qué requisito de seguridad verifica** y
> **por qué quedó verde o rojo**. Última ejecución: `mvn test` → **83 pruebas, 63 verdes, 20 rojas**
> (20 rojas + 1 test preexistente de contexto Spring fuera de este alcance = 21 no-verdes de 83).

Leyenda: 🟢 Verde (pasa, control funciona) · 🔴 Rojo (falla a propósito, documenta un defecto real)

---

## 1. `UsuarioServiceTest` — 21 ejecuciones (14 métodos), 10 rojas

### 🟢 `registrarUsuario_debeAlmacenarContrasenaHasheadaConBCrypt_V2_4_1`
**Qué prueba:** registra un usuario con contraseña `"ClaveSegura123!"` y captura el objeto que se
manda a guardar; confirma que el valor guardado **no es la contraseña en texto plano** y que
tiene el prefijo `$2a$`/`$2b$` propio de BCrypt.
**Requisito:** ISO 27001 A.8.28 (codificación segura) · OWASP ASVS V2.4.1 (hash aprobado).

### 🟢 `registrarUsuario_correoYaRegistrado_noPersisteYRechaza`
**Qué prueba:** si el correo ya existe en BD, el servicio devuelve `"Correo ya registrado."` y
**nunca llama a `save`**.
**Requisito:** ISO 25010 Integridad (no duplicar identidades).

### 🟢 `registrarUsuario_sinRolEspecificado_asignaRolUsuarioPorDefecto`
**Qué prueba:** si el usuario llega sin rol (`null`), el servicio le asigna `"USUARIO"` antes de
guardar.
**Requisito:** ISO 27001 A.8.2 (derechos de acceso privilegiado) · ASVS V4.1.3 (mínimo privilegio).

### 🔴 `registrarUsuario_DEFECTO_permiteEscaladaDePrivilegiosPorRolArbitrario` (×3: `BIBLIOTECARIO`, `ADMIN`, `SUPERUSER`)
**Qué prueba:** registra un usuario enviando explícitamente un rol privilegiado en el JSON de
entrada y verifica que el rol persistido **debería** forzarse a `"USUARIO"`.
**Requisito:** ISO 25010 Integridad/Autenticidad · ISO 27001 A.8.2, A.5.15 · ASVS V4.1.5, V4.2.1.
**Por qué está rojo:** el servicio persiste el rol tal cual llega del cliente. Como
`POST /api/usuarios/registro` es `permitAll` en `SecurityConfig`, **cualquier persona sin
autenticarse puede autoasignarse el rol `BIBLIOTECARIO`**. Es el hallazgo más grave de todo el
análisis (escalada de privilegios / mass assignment).

### 🔴 `registrarUsuario_DEFECTO_aceptaContrasenaDebilSinValidarFortaleza` (×4: `""`, `"1"`, `"abc"`, `"aaaaaaaaaa"`)
**Qué prueba:** intenta registrar usuarios con contraseñas vacías o triviales y verifica que
**no deberían** persistirse (`save` nunca debería llamarse).
**Requisito:** ASVS V2.1.1 (mínimo 12 caracteres).
**Por qué está rojo:** el servicio hashea y guarda cualquier contraseña sin validar longitud ni
complejidad — incluida la cadena vacía.

### 🟢 `autenticarYObtenerUsuario_correoInexistente_devuelveNull`
**Qué prueba:** autenticar con un correo que no existe devuelve `null` (no lanza excepción ni da
pistas de que el correo no existe).
**Requisito:** ASVS V2.2.2 (prevención de enumeración de usuarios).

### 🟢 `autenticarYObtenerUsuario_contrasenaIncorrecta_devuelveNullSinDistinguirCausa`
**Qué prueba:** con un correo que sí existe pero contraseña incorrecta, también devuelve `null`
— el mismo resultado que "correo inexistente", sin distinguir la causa del fallo.
**Requisito:** ASVS V2.2.2.

### 🟢 `autenticarYObtenerUsuario_credencialesCorrectas_devuelveUsuario`
**Qué prueba:** con correo y contraseña correctos (verificados contra un hash BCrypt real),
devuelve el `Usuario`.
**Requisito:** ASVS V2.4.1 (verificación correcta contra hash almacenado).

### 🟢 `cambiarContrasena_actualIncorrecta_rechazaYNoPersiste`
**Qué prueba:** si la "contraseña actual" enviada no coincide con el hash guardado, se rechaza el
cambio y no se llama a `save`.
**Requisito:** ASVS V2.1.6 (cambiar contraseña exige la contraseña actual).

### 🟢 `cambiarContrasena_actualCorrecta_actualizaHash`
**Qué prueba:** con la contraseña actual correcta, el hash guardado tras el cambio es distinto al
hash original (se generó un hash nuevo, no se reutilizó).
**Requisito:** ASVS V2.1.6 + V2.4.1.

### 🔴 `cambiarContrasena_DEFECTO_aceptaContrasenaNuevaDebil` (×3: `""`, `"1"`, `"abc"`)
**Qué prueba:** cambia la contraseña a un valor trivial (conociendo la contraseña actual) y
verifica que **no debería** persistirse.
**Requisito:** ASVS V2.1.1.
**Por qué está rojo:** igual que en el registro, no hay validación de fortaleza al cambiar de
contraseña — se puede dejar la cuenta con una contraseña vacía.

### 🟢 `eliminarUsuario_conPrestamosActivos_lanzaExcepcionYNoElimina`
**Qué prueba:** si el usuario tiene préstamos sin devolver, `eliminarUsuario` lanza excepción y
no borra al usuario.
**Requisito:** ISO 25010 Integridad (regla de negocio).

### 🟢 `eliminarUsuario_conAmonestacionesPendientes_lanzaExcepcionYNoElimina`
**Qué prueba:** igual que el anterior pero con amonestaciones no pagadas.
**Requisito:** ISO 25010 Integridad.

### 🟢 `eliminarUsuario_sinBloqueos_eliminaComentariosResenasYUsuario`
**Qué prueba:** sin bloqueos, se borran primero los comentarios y reseñas asociados y luego el
usuario (limpieza consistente de datos relacionados).
**Requisito:** ISO 25010 Integridad.

---

## 2. `CustomUserDetailsServiceTest` — 2 ejecuciones, 0 rojas

### 🟢 `loadUserByUsername_usuarioExistente_devuelveUserDetailsConHashYRolCorrectos`
**Qué prueba:** que el `UserDetails` construido a partir de un `Usuario` de BD tenga exactamente
el mismo correo, el mismo hash de contraseña y una única autoridad igual al rol guardado.
**Requisito:** ISO 27001 A.5.17 (información de autenticación) · ASVS V2.4.1.

### 🟢 `loadUserByUsername_usuarioInexistente_lanzaUsernameNotFoundException`
**Qué prueba:** si el correo no existe, lanza `UsernameNotFoundException` (comportamiento
estándar de Spring Security, no una excepción distinta que delate más información).
**Requisito:** ASVS V2.2.2.

---

## 3. `LibroServiceTest` — 19 ejecuciones (12 métodos), 3 rojas

### 🟢 `registrarLibroConImagen_conRolBibliotecario_persisteLibro`
**Qué prueba:** un usuario con rol `BIBLIOTECARIO` sí puede registrar un libro válido.
**Requisito:** ISO 27001 A.8.2 · ASVS V4.1.1/V4.1.3.

### 🟢 `registrarLibroConImagen_conRolNoBibliotecario_lanzaAccessDeniedException` (×3: `USUARIO`, `ADMIN`, `bibliotecario` en minúscula)
**Qué prueba:** cualquier rol que no sea exactamente `"BIBLIOTECARIO"` (incluida una variante en
minúsculas, para probar que la comparación es case-sensitive) es rechazado con
`AccessDeniedException` y no se persiste nada.
**Requisito:** ISO 27001 A.8.2 · ASVS V4.1.3.

### 🟢 `registrarLibroConImagen_isbnDuplicado_rechazaYNoPersiste`
**Qué prueba:** si ya existe un libro con ese ISBN, se rechaza sin guardar.
**Requisito:** ISO 25010 Integridad.

### 🟢 `registrarLibroConImagen_cantidadNoPositiva_rechazaYNoPersiste` (×3: `0`, `-1`, `-100`)
**Qué prueba:** cantidades no positivas se rechazan antes de guardar.
**Requisito:** ASVS V5.1.3 (validación de entrada positiva).

### 🔴 `registrarLibroConImagen_DEFECTO_isbnNegativoBurlaValidacionDeLongitud`
**Qué prueba:** registra un libro con ISBN `-123456789012` (12 dígitos de magnitud) y verifica
que **no debería** guardarse.
**Requisito:** ASVS V5.1.3.
**Por qué está rojo:** la validación de "13 dígitos" se hace con
`String.valueOf(isbn).length() == 13`. Con un ISBN negativo, el signo `-` ocupa el lugar de un
carácter, así que un número de 12 dígitos negativo también da longitud 13 y **pasa la
validación** aunque sea un ISBN inválido/imposible.

### 🟢 `actualizarLibroPorIsbn_conRolBibliotecario_actualizaCamposPermitidos`
**Qué prueba:** con rol `BIBLIOTECARIO`, el `PUT` actualiza correctamente título y cantidad.
**Requisito:** ISO 27001 A.8.2 · ASVS V4.1.1.

### 🟢 `actualizarLibroPorIsbn_conRolNoBibliotecario_lanzaAccessDeniedException` (×2: `USUARIO`, `""`)
**Qué prueba:** sin rol `BIBLIOTECARIO`, el `PUT` se rechaza.
**Requisito:** ISO 27001 A.8.2 · ASVS V4.1.3.

### 🔴 `actualizarLibroPorIsbn_DEFECTO_permiteCantidadNegativa` (×2: `-1`, `-50`)
**Qué prueba:** actualiza un libro fijando `cantidad` en un valor negativo y verifica que **no
debería** guardarse.
**Requisito:** ASVS V5.1.3.
**Por qué está rojo:** a diferencia del alta (`registrarLibroConImagen`), el método de
actualización **no repite** la validación `cantidad > 0`; copia el valor recibido tal cual. Se
puede dejar el inventario en negativo vía `PUT /api/libros/isbn/{isbn}`.

### 🟢 `actualizarLibroPorIsbn_libroNoEncontrado_lanzaExcepcion`
**Qué prueba:** actualizar un ISBN que no existe lanza excepción.
**Requisito:** ISO 25010 Integridad.

### 🟢 `eliminarLibroPorIsbn_conRolBibliotecario_eliminaLibro`
**Qué prueba:** con rol `BIBLIOTECARIO`, se elimina el libro correctamente.
**Requisito:** ISO 27001 A.8.2 · ASVS V4.1.1.

### 🟢 `eliminarLibroPorIsbn_conRolNoBibliotecario_lanzaAccessDeniedException` (×2: `USUARIO`, `ADMIN`)
**Qué prueba:** sin rol `BIBLIOTECARIO` se rechaza el borrado.
**Requisito:** ISO 27001 A.8.2 · ASVS V4.1.3.

### 🟢 `eliminarLibroPorIsbn_libroNoEncontrado_lanzaExcepcion`
**Qué prueba:** eliminar un ISBN inexistente lanza excepción.
**Requisito:** ISO 25010 Integridad.

---

## 4. `PrestamoServiceTest` — 18 ejecuciones (15 métodos), 1 roja

### 🟢 `crearPrestamo_conRolUsuarioYStockDisponible_registraYDecrementaStock`
**Qué prueba:** con rol `USUARIO` y stock disponible, se crea el préstamo y el stock del libro
baja de 3 a 2.
**Requisito:** ISO 27001 A.8.2 · ASVS V4.1.1/V4.1.3.

### 🟢 `crearPrestamo_conRolNoUsuario_rechazaYNoPersiste` (×3: `BIBLIOTECARIO`, `ADMIN`, `""`)
**Qué prueba:** solo el rol `USUARIO` puede pedir préstamos; cualquier otro rol se rechaza.
**Requisito:** ISO 27001 A.8.2 · ASVS V4.1.3.

### 🟢 `crearPrestamo_conDosPrestamosActivos_rechazaYNoPersiste`
**Qué prueba:** con 2 préstamos activos ya registrados, se rechaza un tercero.
**Requisito:** ASVS V11.1 (límite de negocio).

### 🟢 `crearPrestamo_conAmonestacionesPendientes_rechazaYNoPersiste`
**Qué prueba:** con amonestaciones no verificadas, se rechaza el préstamo.
**Requisito:** ASVS V11.1.

### 🟢 `crearPrestamo_libroSinStock_rechazaYNoPersiste`
**Qué prueba:** con `cantidad = 0`, se rechaza el préstamo.
**Requisito:** ASVS V11.1.

### 🟢 `crearPrestamo_usuarioNoRegistrado_rechaza`
**Qué prueba:** un correo que no existe en BD no puede pedir préstamo.
**Requisito:** ISO 25010 Integridad.

### 🟢 `devolverPrestamo_yaDevuelto_rechazaYNoDuplicaEfectos`
**Qué prueba:** devolver un préstamo que ya tiene `fechaDevolucion` se rechaza (no se vuelve a
incrementar el stock).
**Requisito:** ASVS V11.1 (no permitir doble devolución).

### 🟢 `devolverPrestamo_vencido_generaAmonestacion`
**Qué prueba:** al devolver un préstamo cuya `fechaLimite` ya pasó, se genera una `Amonestacion`
con monto 100.0 y `pagada = false`.
**Requisito:** ISO 25010 Integridad · ASVS V11.1.

### 🟢 `devolverPrestamo_aTiempo_noGeneraAmonestacion`
**Qué prueba:** al devolver a tiempo, no se guarda ninguna amonestación.
**Requisito:** ISO 25010 Integridad.

### 🟢 `renovarPrestamo_conRolNoBibliotecario_rechaza` (×2: `USUARIO`, `""`)
**Qué prueba:** solo `BIBLIOTECARIO` puede renovar préstamos; el rechazo ocurre antes de tocar el
repositorio (ni siquiera se busca el préstamo en BD).
**Requisito:** ISO 27001 A.8.2 · ASVS V4.1.1 (rol resuelto server-side).

### 🟢 `renovarPrestamo_noEncontrado_rechaza`
**Qué prueba:** renovar un ID de préstamo inexistente se rechaza.
**Requisito:** ISO 25010 Integridad.

### 🟢 `renovarPrestamo_noFinalizado_rechaza`
**Qué prueba:** solo se pueden renovar préstamos ya finalizados (`fechaDevolucion != null`).
**Requisito:** ASVS V11.1.

### 🟢 `renovarPrestamo_conAmonestacionesPendientes_rechaza`
**Qué prueba:** no se renueva si el usuario tiene amonestaciones sin verificar.
**Requisito:** ASVS V11.1.

### 🟢 `renovarPrestamo_conStockDisponible_actualizaFechaLimiteYEstado`
**Qué prueba:** con stock disponible, la renovación deja el préstamo en estado `"activo"` y sin
`fechaDevolucion`.
**Requisito:** ISO 25010 Integridad.

### 🔴 `renovarPrestamo_DEFECTO_decrementaStockSinVerificarDisponibilidad`
**Qué prueba:** renueva un préstamo cuyo libro tiene `cantidad = 0` y verifica que el stock
**no debería** quedar negativo tras la renovación.
**Requisito:** ISO 25010 Integridad · ASVS V11.1.4.
**Por qué está rojo:** a diferencia de `crearPrestamo` (que valida `cantidad >= 1` antes de
decrementar), `renovarPrestamo` decrementa el stock **sin verificar disponibilidad**. El test
captura el libro guardado y confirma que su `cantidad` quedó en **-1** — inventario corrupto.

---

## 5. `AmonestacionServiceTest` — 5 ejecuciones (3 métodos), 0 rojas

### 🟢 `eliminarAmonestacion_conRolBibliotecario_elimina`
**Qué prueba:** con rol `BIBLIOTECARIO` y una amonestación existente, se elimina correctamente.
**Requisito:** ISO 27001 A.8.2 · ASVS V4.1.1/V4.1.3.

### 🟢 `eliminarAmonestacion_conRolNoBibliotecario_rechazaYNoConsultaRepositorio` (×3: `USUARIO`, `""`, `admin`)
**Qué prueba:** sin rol `BIBLIOTECARIO` se rechaza **antes** de consultar el repositorio (ni
`existsById` ni `deleteById` se llaman).
**Requisito:** ISO 27001 A.8.2 · ASVS V4.1.3.

### 🟢 `eliminarAmonestacion_noEncontrada_rechaza`
**Qué prueba:** eliminar un ID de amonestación que no existe se rechaza sin llamar a
`deleteById`.
**Requisito:** ISO 25010 Integridad.

---

## 6. `ControllerAuthorizationTest` — 14 ejecuciones, 4 rojas

Instancia `Controller` con `new` e inyecta los mocks por reflexión pura de Java (sin Spring).

### 🟢 `editarResena_usuarioDistintoAlAutor_devuelve403`
**Qué prueba:** un usuario autenticado distinto al autor de la reseña intenta editarla; el
controller responde `403` y nunca llama a `guardar`.
**Requisito:** ASVS V4.2.1 (protección IDOR/BOLA).

### 🟢 `editarResena_usuarioAutor_permiteEdicion`
**Qué prueba:** el autor real de la reseña sí puede editarla (`200`, texto actualizado y
`guardar` invocado).
**Requisito:** ASVS V4.2.1 (control positivo).

### 🟢 `eliminarResena_usuarioDistintoAlAutor_devuelve403`
**Qué prueba:** igual que el primero pero para el borrado de reseñas.
**Requisito:** ASVS V4.2.1.

### 🟢 `editarComentarioResena_usuarioDistintoAlAutor_devuelve403`
**Qué prueba:** un usuario distinto al autor de un comentario de reseña no puede editarlo.
**Requisito:** ASVS V4.2.1.

### 🟢 `eliminarComentarioResena_usuarioDistintoAlAutor_devuelve403`
**Qué prueba:** igual, para el borrado de comentarios.
**Requisito:** ASVS V4.2.1.

### 🟢 `pagarAmonestacion_usuarioDistintoAlPropietario_devuelve403`
**Qué prueba:** un usuario intenta pagar/marcar una amonestación que pertenece a otro usuario;
se rechaza con `403` y no se guarda.
**Requisito:** ASVS V4.2.1.

### 🟢 `pagarAmonestacion_usuarioPropietario_permitePago`
**Qué prueba:** el propietario real de la amonestación sí puede pagarla (`200`, `pagada = true`).
**Requisito:** ASVS V4.2.1 (control positivo).

### 🔴 `verificarAmonestacion_DEFECTO_metodoNoValidaRolNiPropietario`
**Qué prueba (técnica distinta a las demás):** usa reflexión (`Method.getParameterTypes()`) para
comprobar si `verificarAmonestacion(Integer id)` recibe un parámetro `Authentication`. No hay
mocks porque no hay rama de código que ejercitar: el defecto es que **no existe ningún chequeo**.
**Requisito:** ISO 27001 A.8.2/A.5.15 · ASVS V4.1.3.
**Por qué está rojo:** el método no recibe `Authentication` ni ningún dato de rol. El único
control que existe hoy es el catch-all `.anyRequest().authenticated()` de `SecurityConfig`, así
que **cualquier usuario autenticado** (no solo `BIBLIOTECARIO`) puede verificar/aprobar el pago
de una amonestación ajena.

### 🔴 `getTodasAmonestaciones_DEFECTO_metodoNoValidaRol`
**Qué prueba:** misma técnica de reflexión sobre `getTodasAmonestaciones()`.
**Requisito:** ISO 27001 A.8.2/A.8.3 · ASVS V4.1.3.
**Por qué está rojo:** el método no recibe `Authentication`; expone la lista de amonestaciones
(datos financieros/personales) de **todos** los usuarios a cualquier usuario autenticado, sin
restringir a `BIBLIOTECARIO`.

### 🔴 `crearResena_DEFECTO_noCruzaUsuarioIdConAutenticado`
**Qué prueba:** reflexión sobre `crearResena(ResenaRequest)`.
**Requisito:** ISO 25010 Autenticidad · ASVS V4.2.1.
**Por qué está rojo:** el método no recibe `Authentication`; el `usuarioId` de la reseña se toma
tal cual del cuerpo JSON que envía el cliente, sin cruzarlo contra el usuario realmente
autenticado. Un atacante puede publicar una reseña **"como" otro usuario**, a diferencia de
`editarResena`/`eliminarResena`, que sí resuelven el usuario desde `Authentication`.

### 🔴 `crearComentarioResena_DEFECTO_noCruzaUsuarioIdConAutenticado`
**Qué prueba:** el mismo patrón de reflexión sobre `crearComentarioResena(ComentarioResenaRequest)`.
**Requisito:** ISO 25010 Autenticidad · ASVS V4.2.1.
**Por qué está rojo:** mismo defecto que el anterior, aplicado a comentarios de reseña.

### 🟢 `loginUsuario_credencialesValidas_noExponeContrasenaEnRespuesta`
**Qué prueba:** tras un login exitoso, el `Usuario` devuelto en el `ResponseEntity` tiene
`contrasena == null` (el controller la limpia manualmente antes de responder).
**Requisito:** ISO 25010 Confidencialidad · ISO 27001 A.8.3 · ASVS V8.3.4.

### 🟢 `loginUsuario_credencialesInvalidas_devuelve401MensajeGenerico`
**Qué prueba:** con credenciales inválidas, responde `401` con el mensaje genérico
`"Credenciales inválidas"` (no distingue si falló el correo o la contraseña).
**Requisito:** ASVS V2.2.2.

### 🟢 `getUsuarioAutenticado_devuelveUsuarioSinContrasena`
**Qué prueba:** el endpoint `/api/usuarios/me` también nulea la contraseña antes de responder.
**Requisito:** ISO 25010 Confidencialidad · ASVS V8.3.4.

---

## 7. `ModelSerializationTest` — 3 ejecuciones, 2 rojas

Usa `ObjectMapper` de Jackson puro (librería, no contexto Spring) para serializar entidades
directamente a JSON, tal como lo haría Spring MVC al responder un endpoint.

### 🔴 `usuario_DEFECTO_serializacionExponeHashDeContrasena`
**Qué prueba:** serializa un `Usuario` con contraseña y verifica que el JSON resultante **no
debería** contener el hash.
**Requisito:** ISO 25010 Confidencialidad · ISO 27001 A.8.3 · ASVS V8.3.4.
**Por qué está rojo:** la clase `Usuario` no tiene `@JsonIgnore`/`@JsonProperty(WRITE_ONLY)` en el
campo `contrasena`. El JSON generado incluye literalmente `"contrasena":"$2a$10$..."`. Hoy el
riesgo está mitigado a mano en los dos únicos lugares donde el controller limpia el campo antes
de responder (login y `/usuarios/me`), pero **cualquier endpoint nuevo** que devuelva un
`Usuario` sin recordar hacer esa limpieza manual filtraría el hash.

### 🔴 `prestamo_DEFECTO_serializacionExponeHashDeContrasenaDelUsuarioAnidado`
**Qué prueba:** serializa un `Prestamo` (con su `Usuario` anidado) y verifica que el JSON no
debería contener el hash del usuario.
**Requisito:** ISO 25010 Confidencialidad · ISO 27001 A.8.3 · ASVS V8.3.4.
**Por qué está rojo:** `Prestamo.usuario` **no** tiene `@JsonIgnoreProperties({"contrasena"})`
(a diferencia de `Amonestacion.usuario`, que sí lo tiene). Cualquier endpoint que devuelva un
`Prestamo` (`GET /api/prestamos`, `/api/prestamos/activos`, `/api/prestamos/usuario/{id}`, etc.)
filtra el hash de contraseña del usuario dueño del préstamo.

### 🟢 `amonestacion_serializacionOcultaContrasenaDelUsuarioAnidado`
**Qué prueba:** serializa una `Amonestacion` (con su `Usuario` anidado) y confirma que el JSON
**no** contiene el hash.
**Requisito:** ISO 25010 Confidencialidad · ISO 27001 A.8.3.
**Resultado:** control positivo — aquí sí funciona porque `Amonestacion.usuario` está anotado con
`@JsonIgnoreProperties({"contrasena"})`. Sirve de contraste: la misma clase `Usuario` anidada en
`Prestamo` (sin esa anotación) sí filtra el dato, mostrando que la protección actual es
inconsistente entre entidades en vez de estar centralizada en el modelo `Usuario`.

---

## Tabla resumen de los 9 defectos reales (por causa raíz)

| # | Defecto | Test(s) que lo documentan | Severidad |
|---|---|---|---|
| 1 | Escalada de privilegios en registro público (rol arbitrario) | `UsuarioServiceTest` ×3 | **Crítica** |
| 2 | IDOR en creación de reseñas/comentarios (usuarioId sin validar) | `ControllerAuthorizationTest` ×2 | **Alta** |
| 3 | `verificarAmonestacion` sin control de rol | `ControllerAuthorizationTest` | **Alta** |
| 4 | `getTodasAmonestaciones` sin control de rol | `ControllerAuthorizationTest` | **Alta** |
| 5 | `Usuario`/`Prestamo` exponen hash de contraseña en JSON | `ModelSerializationTest` ×2 | Media |
| 6 | Sin validación de fortaleza de contraseña (registro) | `UsuarioServiceTest` ×4 | Media |
| 7 | Sin validación de fortaleza de contraseña (cambio) | `UsuarioServiceTest` ×3 | Media |
| 8 | `renovarPrestamo` decrementa stock sin verificar disponibilidad | `PrestamoServiceTest` | Media |
| 9 | ISBN negativo / `cantidad` negativa vía `PUT` | `LibroServiceTest` ×3 | Baja/Media |

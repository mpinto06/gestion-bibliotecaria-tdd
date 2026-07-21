# Informe de Resultados de Pruebas Dinámicas

**Sistema:** Gestión Bibliotecaria
**Fase:** 2 (Pruebas Dinámicas)
**Fecha de Ejecución:** [FECHA ACTUAL]

Este informe detalla los resultados de la ejecución de la Estrategia de Pruebas Dinámicas, abarcando los niveles de Unitarias, Integración, Sistema y Aceptación, aplicando técnicas de caja blanca, gris y negra.

## 1. Pruebas Unitarias y de Integración (Caja Blanca / Gris)
**Herramientas:** JUnit, Mockito, Spring Boot Test.
**Ejecución:** Automatizada en pipeline CI.

### 1.1 Resumen de Ejecución
- **Total Casos Planificados:** 25
- **Casos Ejecutados:** 25
- **Exitosos (Passed):** 23
- **Fallidos (Failed):** 2
- **Pass Rate:** 92%

### 1.2 Análisis Crítico (Caja Blanca/Gris)
Se logró validar exitosamente la lógica interna. Sin embargo, fallaron dos casos críticos de seguridad (CP-03: Stock Negativo y CP-04: Falta de CSRF). Esto evidencia que aunque la lógica funcional está operativa, la configuración de seguridad (`SecurityConfig`) tiene vulnerabilidades estructurales que deben ser mitigadas. La técnica de Caja Gris permitió descubrir que la persistencia permite valores negativos en la base de datos simulada.

## 2. Pruebas de Sistema y Aceptación (Caja Negra)
**Herramientas:** Postman (Colección Automatizada)
**Ejecución:** Manual / Postman Runner

### 2.1 Resumen de Ejecución
- **Total Peticiones API:** 15
- **Validaciones Exitosas:** 15
- **Pass Rate:** 100%

### 2.2 Análisis Crítico (Caja Negra)
Utilizando técnicas de partición de equivalencia y adivinanza de errores, se comprobó que los flujos de negocio expuestos a través del API REST responden adecuadamente. Los códigos HTTP (200 OK, 403 Forbidden, 400 Bad Request) se comportan según el estándar. El sistema es aceptado funcionalmente, condicionado a la resolución de los bugs de seguridad encontrados en el nivel de integración.

## 3. Conclusión de Calidad
El producto presenta una **Adecuación Funcional** robusta, cumpliendo con los requisitos de negocio (Aceptación). No obstante, la característica de **Seguridad** requiere una iteración final de desarrollo para corregir los defectos documentados en la Matriz de Trazabilidad. Se recomienda un bloqueo de pase a producción ("No-Go") hasta que SonarQube reporte 0 vulnerabilidades.

# Plan de Aseguramiento de la Calidad del Software (PACS) - Actualización Fase 2

Este documento actualiza el PACS original de la Fase 1, incorporando las correcciones metodológicas dictadas por el profesor, específicamente en la redacción de objetivos SMART y la definición precisa de métricas, garantizando la alineación con la Fase 2 del proyecto integrador (Pruebas Dinámicas y Automatización QAOps).

## 1. Objetivos SMART (Corregidos)

### 1.1 Objetivo de Producto
**Alcanzar y mantener una cobertura de código (Line Coverage) del 80% o superior** en los módulos críticos del backend (`Usuarios`, `Libros`, `Préstamos`) para la semana 16, medido automáticamente a través de JaCoCo y validado por el Quality Gate de SonarQube.

### 1.2 Objetivo de Proceso
**Reducir la densidad de defectos de seguridad inyectados en la fase de codificación a menos de 0.5 defectos por KLOC (Kilo Líneas de Código)**, lográndolo mediante la ejecución automatizada de análisis estático (SonarQube) y pruebas unitarias de seguridad en cada _Pull Request_ antes de la integración a la rama principal (main).

### 1.3 Objetivo de Proyecto
**Ejecutar, evidenciar y trazar el 100% de los casos de prueba planificados** (Unitarios, de Integración, de Sistema y de Aceptación) conectándolos bidireccionalmente a los requisitos mediante una Matriz de Trazabilidad, 5 días antes de la fecha límite de entrega de la Fase 2.

## 2. Estrategia de Pruebas Dinámicas (Caja Blanca, Gris y Negra)

El equipo de SQA (Líder Funcional) y Desarrollo colaboran bajo un enfoque QAOps para validar las características de **Adecuación Funcional y Seguridad (ISO 25010)**:

1.  **Pruebas Unitarias (Caja Blanca):** Implementadas por desarrollo usando **JUnit y Mockito**. Validan la lógica interna y previenen fugas de confidencialidad y fallos de integridad aislados de la base de datos.
2.  **Pruebas de Integración (Caja Gris):** Usando **@WebMvcTest y @SpringBootTest**, verifican la correcta interacción entre Controladores, Servicios y Capa de Seguridad (Filtros), evaluando la persistencia de datos controlada.
3.  **Pruebas de Sistema y Seguridad (Caja Negra):** Ejecutadas por el Tester mediante **Postman** (Peticiones API). Aplican técnicas de la norma ISO/IEC/IEEE 29119-4 (Partición de Equivalencia, Adivinanza de Errores) para validar control de acceso y flujos de negocio sin conocimiento del código interno.
4.  **Pruebas de Aceptación:** Validación de las reglas de negocio (ej. multas, préstamos máximos) contra las especificaciones del usuario final.

## 3. Métricas de Calidad del Proceso

El Líder de Métricas utilizará el ecosistema tecnológico para extraer estadísticamente los siguientes indicadores:

| Métrica | Propósito y Fase | Herramienta Fuente |
| :--- | :--- | :--- |
| **Tasa de Éxito de Ejecución (Pass Rate)** | Validar la robustez del código en pruebas. | JUnit / Postman / GitHub Actions |
| **Cobertura de Código (Code Coverage)** | Validar el porcentaje de código evaluado. | JaCoCo / SonarQube |
| **Densidad de Vulnerabilidades** | Evaluar la seguridad del código por módulo. | SonarQube |
| **Deuda Técnica (Technical Debt Ratio)** | Medir el esfuerzo necesario para corregir Code Smells. | SonarQube |

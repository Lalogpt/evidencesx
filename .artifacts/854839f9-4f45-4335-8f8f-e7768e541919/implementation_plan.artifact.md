# Plan de Implementación: Módulos de Nivel Empresarial para EVIDENCE X

Este plan detalla la implementación de las 5 características avanzadas solicitadas para elevar **EVIDENCE X** a un sistema pericial de grado forense y militar.

## User Review Required

> [!IMPORTANT]
> - Se integrará la librería `androidx.biometric:biometric` para autenticación por huella dactilar / rostro.
> - Se implementará generación de códigos QR mediante matriz nativa/liviana para etiquetado de evidencias.
> - Se añadirán pruebas unitarias con JUnit para validar la infalibilidad matemática de los hashes SHA-256 y la cadena de custodia.

## Proposed Changes

### 1. Seguridad Biométrica y PIN (`security/`)
- **[NEW]** [BiometricAuthManager.kt](file:///C:/Users/USUARIO/OneDrive/Desktop/EVIDENCEX/app/src/main/java/com/codemaster/evidencex/security/BiometricAuthManager.kt)
- **[MODIFY]** [MainActivity.kt](file:///C:/Users/USUARIO/OneDrive/Desktop/EVIDENCEX/app/src/main/java/com/codemaster/evidencex/MainActivity.kt) (Agregar pantalla de bloqueo/autenticación inicial).

### 2. Verificador de Integridad Criptográfica en Tiempo Real (`domain/crypto/`)
- **[NEW]** [IntegrityVerifier.kt](file:///C:/Users/USUARIO/OneDrive/Desktop/EVIDENCEX/app/src/main/java/com/codemaster/evidencex/domain/crypto/IntegrityVerifier.kt)
- **[NEW]** [IntegrityScreen.kt](file:///C:/Users/USUARIO/OneDrive/Desktop/EVIDENCEX/app/src/main/java/com/codemaster/evidencex/ui/screens/IntegrityScreen.kt)

### 3. Generación de Códigos QR para Evidencias (`util/`)
- **[NEW]** [QrCodeGenerator.kt](file:///C:/Users/USUARIO/OneDrive/Desktop/EVIDENCEX/app/src/main/java/com/codemaster/evidencex/util/QrCodeGenerator.kt)

### 4. Paquetes Forenses Seguros `.evx` (`data/export/`)
- **[NEW]** [ForensicPackageExporter.kt](file:///C:/Users/USUARIO/OneDrive/Desktop/EVIDENCEX/app/src/main/java/com/codemaster/evidencex/data/export/ForensicPackageExporter.kt)

### 5. Suite de Pruebas Automatizadas (`src/test/`)
- **[NEW]** [CryptoUtilsTest.kt](file:///C:/Users/USUARIO/OneDrive/Desktop/EVIDENCEX/app/src/test/java/com/codemaster/evidencex/CryptoUtilsTest.kt)

## Verification Plan

### Automated Tests
- Ejecutar `./gradlew testDebugUnitTest` para verificar las pruebas unitarias criptográficas.

### Manual Verification
- Desplegar en emulador o dispositivo y verificar el bloqueo biométrico, la verificación de integridad de hashes, la generación de QR y la exportación del paquete `.evx`.

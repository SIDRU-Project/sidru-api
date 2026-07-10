# Guion de demo y validación E2E — SIDRU (Capstone)

> Cubre **US-37** (guion de demo documentado) y **US-26** (validación del flujo completo del MVP).
> Objetivo: una sustentación sin sorpresas. Sigue los pasos en orden; cada bloque indica la(s) US que
> demuestra. Al final hay un **checklist E2E** para firmar antes de presentar y un **plan de contingencia**.

---

## 0. Preparación (T-15 min antes de presentar)

| # | Acción | Verificación |
|---|---|---|
| 1 | Prender el backend en Azure: `az vm start -g sidru-rg -n sidru-vm` | Espera ~30–45 s |
| 2 | Comprobar API arriba | `curl -s -o /dev/null -w "%{http_code}" http://sidru-209313030.eastus2.cloudapp.azure.com:8080/api/v1/v3/api-docs` → **200** |
| 3 | Abrir Swagger (pantalla de respaldo) | `…:8080/api/v1/swagger-ui/index.html` carga |
| 4 | Encender el Smart Bin (ESP32) y esperar WiFi + MQTT | Pantalla TFT muestra "Comenzar"; LED/serial: WiFi OK, MQTT conectado |
| 5 | App instalada (APK release) y apuntando a Azure | `.env` → `API_BASE_URL=…azure…:8080/api/v1` |
| 6 | Cuenta de prueba lista (o registrar en vivo) | Email/clave a mano |
| 7 | Wallet MetaMask de prueba en **Polygon Amoy** (para el retiro) | Dirección `0x…` copiada |
| 8 | Abrir Polygonscan del contrato en una pestaña | `https://amoy.polygonscan.com/address/0x734abA5606F56F7a6313132c6d608B43e594C684` |

> **Regla de oro:** prender Azure y el bin **antes** de empezar. La VM tarda y el ESP necesita asociar WiFi.

---

## 1. Registro e inicio de sesión · *US-07, US-13, US-06*
1. Abrir la app → **Onboarding** → **Registrarse**.
2. Completar nombre, email, clave, teléfono, distrito → **Crear cuenta**.
3. (O bien) **Iniciar sesión** con la cuenta de prueba.

✅ *Esperado:* entra al Home; el JWT queda en SecureStorage (no se ve). Si las credenciales son malas,
muestra error claro.

---

## 2. Depósito físico en el Smart Bin · *US-08, US-09, US-10, US-11, US-12*
1. En la TFT, pulsar **Comenzar** (el servo va a posición de recolección).
2. Depositar **chapas plásticas válidas** una a una → la pantalla cuenta y muestra el **peso en vivo**.
3. **Demostrar el rechazo de metal:** depositar una tapa metálica → pantalla "METAL" y el servo la
   expulsa (no suma).
4. Pulsar **TERMINÉ**.

✅ *Esperado:* contador correcto, peso > 0, metal rechazado sin contarse.

---

## 3. Generación de sesión y QR · *US-15, US-17, US-18*
1. Tras "TERMINÉ", el bin pesa y llama a `POST /sessions` con `capCount` + `weightGrams`.
2. La TFT muestra el **QR** y los **puntos** calculados.

✅ *Esperado:* QR visible; puntos coherentes con el peso (`peso_kg × 4.00 S/ × 100 pts/sol`).

---

## 4. Escaneo y confirmación en la app · *US-19, US-20*
1. En la app → **Escanear** → apuntar al QR de la TFT.
2. Ver el **resumen** de la sesión (chapas, peso, puntos) → **Confirmar entrega**.
3. *Plan B cámara:* usar **Ingresar código manual** y teclear el token.

✅ *Esperado:* pantalla de resultado con éxito; la compuerta del bin **se abre** (comando OPEN por MQTT);
llega una **notificación push** (US-39).

---

## 5. Recompensas, saldo y blockchain · *US-21, US-24, US-25, US-38, US-35, US-39*
1. **Notificación push:** mostrar la notificación "Se acreditaron N CTC" (US-39).
2. **Wallet** (US-25, US-38): mostrar dirección custodial, red **Amoy**, **balance CTC** y la
   **transacción con su hash** → tocar el link → abre **Polygonscan** (RF-19).
3. **Polygonscan** (US-35): mostrar el contrato `0x734abA…` y la transacción de mint reciente.
4. **Historial** (US-22): la sesión aparece como **CONFIRMADA** con su hash.

✅ *Esperado:* el balance subió; la tx es real y verificable en el explorador público.

---

## 6. Canje de recompensa · *rewards (EP05)*
1. Ir a **Recompensas** → elegir una → **Canjear**.
2. Ver el descuento de puntos/CTC y el resultado.

✅ *Esperado:* canje exitoso; el saldo baja (burn/transfer registrado on-chain).

---

## 7. Retiro a wallet propia · *US-38 (self-custody)*
1. En **Wallet** → **Retirar a mi wallet**.
2. Pegar la dirección MetaMask de Amoy → el sistema valida **formato + checksum EIP-55**.
3. Confirmar → el backend transfiere los CTC (paga el gas) → estado pasa a **completado**.
4. *(Opcional)* abrir MetaMask en Amoy y mostrar los CTC recibidos.

✅ *Esperado:* dirección inválida ⇒ error de validación; dirección válida ⇒ retiro idempotente OK.

---

## 8. Cierre — métricas de impacto (narrativa)
Resumir el impacto: chapas recicladas, peso total, CTC emitidos. *(US-36 aún pendiente como endpoint;
de momento se narra con los datos de la demo / historial.)*

---

## Casos de borde a mencionar o probar · *US-23, US-09, sesiones*
- **Doble confirmación (anti-fraude, US-23):** confirmar dos veces el mismo QR ⇒ la segunda devuelve
  **409 Conflicto** y **no** vuelve a acreditar ni mintear. *(Implementado con lock pesimista.)*
- **Metal rechazado (US-09):** ya demostrado en el paso 2.
- **Sesión expirada:** una sesión sin confirmar > 15 min ⇒ estado **EXPIRED**; el QR deja de servir.
- **Retiro sin saldo / sin wallet:** mensaje de error controlado.
- **Sin WiFi en el bin:** la TFT muestra "Sin WiFi" y no rompe el ciclo (vuelve a "Comenzar").

---

## Plan de contingencia (si algo falla en vivo)
| Falla | Mitigación |
|---|---|
| Azure tarda/no responde | Prenderla en T-15; tener Swagger abierto; reintentar `az vm start` |
| ESP32 no asocia WiFi | Hotspot del teléfono con el SSID/clave de `secrets.h`; reiniciar el bin |
| Cámara no lee el QR | **Ingresar código manual** en la app (ManualQrScreen) |
| RPC de Amoy lento/caído | Los **puntos off-chain se acreditan igual**; el mint lo reintenta el **job de reconciliación**. Mostrar el hash más tarde |
| Push no llega | Mostrar el saldo/tx directamente en Wallet (no depende del push) |
| Bin sin energía | Usar una **sesión pre-creada** (token de respaldo) y entrar por "Ingresar código manual" |

> Ten a mano: 1 QR/token de **sesión de respaldo** ya creada, el teléfono con **hotspot** listo, y la
> pestaña de **Polygonscan** abierta.

---

## Checklist E2E de validación (US-26) — firmar antes de presentar

Marca cada ítem tras probarlo de extremo a extremo el día previo:

- [ ] Registro de usuario nuevo (US-07/US-13)
- [ ] Login con credenciales válidas e inválidas (US-06)
- [ ] Depósito de chapa plástica: cuenta + pesa (US-08/US-11/US-12)
- [ ] Rechazo de metal por el servo (US-09/US-10)
- [ ] Generación de sesión + QR con puntos correctos (US-15/US-17/US-18)
- [ ] Escaneo de QR (cámara) y por código manual (US-19)
- [ ] Confirmación de entrega → apertura de compuerta por MQTT (US-20)
- [ ] Notificación push de confirmación (US-39)
- [ ] Mint de CTC a la dirección custodial, visible en Polygonscan (US-21/US-24/US-35)
- [ ] Saldo y transacción con hash en Wallet + link al explorador (US-25/US-38)
- [ ] Historial muestra la sesión como CONFIRMADA (US-22)
- [ ] Canje de recompensa descuenta saldo (EP05)
- [ ] Retiro a wallet propia con validación EIP-55 (US-38)
- [ ] **Doble confirmación rechazada con 409, sin doble acreditación (US-23)**
- [ ] Sesión expirada deja de ser canjeable
- [ ] APK release **firmado** instala en Android físico (US-34)

**Probado por:** ____________________  **Fecha:** __________  **Versión APK:** __________

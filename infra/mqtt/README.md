# Broker MQTT (Mosquitto) — SIDRU

Broker para la comunicación **backend → Smart Bin** (comando `OPEN` al confirmar una sesión) y,
opcionalmente, telemetría **bin → backend**. Parte del módulo IoT (`docs/specs/sidru-iot/`).

## Topics
| Topic | Dirección | Payload |
|-------|-----------|---------|
| `sidru/bin/{deviceCode}/commands` | backend → bin | `{"command":"OPEN"\|"RESET","payload":{}}` |
| `sidru/bin/{deviceCode}/events` | bin → backend (opcional) | telemetría/heartbeat |

`{deviceCode}` es el código del bin (ej. `BIN-001`).

## Requisitos
- Docker + Docker Compose.
- No se versiona ningún secreto: el archivo de contraseñas (`mosquitto/config/passwd`) y `.env`
  están en `.gitignore`.

## Puesta en marcha (primera vez)

1. **Define la contraseña** (debe coincidir con `MQTT_PASSWORD` del backend):
   ```bash
   cp .env.example .env
   # edita .env y pon un valor real en MQTT_PASSWORD
   ```

2. **Crea el usuario `sidru`** en el archivo de passwords (usando la imagen de mosquitto, sin
   instalar nada):
   ```bash
   # Windows PowerShell / Git Bash — desde infra/mqtt/
   docker run --rm -v "${PWD}/mosquitto/config:/mosquitto/config" eclipse-mosquitto:2 \
     mosquitto_passwd -b -c /mosquitto/config/passwd sidru "TU_PASSWORD"
   ```
   > Usa el **mismo** valor que pusiste en `.env`. El archivo `passwd` queda gitignored.

3. **Levanta el broker:**
   ```bash
   docker compose up -d
   docker compose logs -f mosquitto      # ver que arranca sin errores
   ```

## Verificación

Suscríbete al topic de comandos del bin (deja esta terminal abierta):
```bash
docker exec -it mosquitto mosquitto_sub -u sidru -P "TU_PASSWORD" -t 'sidru/bin/BIN-001/commands' -v
```
En otra terminal, publica un OPEN de prueba:
```bash
docker exec -it mosquitto mosquitto_pub -u sidru -P "TU_PASSWORD" \
  -t 'sidru/bin/BIN-001/commands' -m '{"command":"OPEN","payload":{}}'
```
Debe aparecer el mensaje en la primera terminal. Sin credenciales, la conexión se rechaza
(`allow_anonymous false`).

## Conexión del backend
El backend ya apunta aquí (`application.properties`):
```
sidru.mqtt.broker-url=tcp://localhost:1883
sidru.mqtt.username=sidru
sidru.mqtt.password=${MQTT_PASSWORD:sidru_mqtt_pass}
```
Arranca el backend con `MQTT_ENABLED=true` y `MQTT_PASSWORD=<el de .env>` para que publique el `OPEN`
al confirmar una sesión.

## Conexión del ESP32
El firmware (`sidru-firmware/`) se conecta a `tcp://<IP-LAN-DEL-PC>:1883` con usuario `sidru` y se
suscribe a `sidru/bin/BIN-001/commands`. Abre el puerto **1883** en el firewall de Windows:
```
netsh advfirewall firewall add rule name="MQTT 1883" dir=in action=allow protocol=TCP localport=1883
```

## Detener / limpiar
```bash
docker compose down            # detiene
docker compose down -v         # detiene y borra volúmenes (datos/log)
```

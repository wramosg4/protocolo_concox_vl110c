# GPS Protocol Parser (GT06)

Este proyecto permite interpretar paquetes binarios recibidos de dispositivos GPS que usan el protocolo GT06 (como el VL110C) y mostrar su contenido de forma legible.

## Requisitos
- Java 8+
- Maven (para construir el .jar)

## Compilar

```bash
mvn clean package
Esto generará el archivo target/gps-protocol-parser-1.0.0.jar
Ejecutar el Servidor
java -jar target/gps-protocol-parser-1.0.0.jar
El servidor escuchará conexiones TCP en el puerto 5000 y procesará paquetes GPS según el protocolo GT06. Cada paquete recibido será interpretado e impreso en consola.
Estructura
•	PacketParser.java: Clase principal para interpretar los paquetes
•	GpsServer.java: Servidor TCP que recibe los paquetes
Ejemplo de salida
Paquete recibido: 78 78 11 01 08 63 76 ...
Login Packet recibido. IMEI: 0863767070197900
ACK enviado: 78 78 05 01 00 17 58 0C 0D 0A

package com.vl110c;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

public class GpsServer {

    private static final File LOG_FILE = new File("gps_data.log");

    public static void main(String[] args) throws Exception {
        int port = 5000;
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("Servidor escuchando en el puerto " + port);
            do {
                Socket sock = server.accept();
                System.out.println("Conexión recibida desde " + sock.getRemoteSocketAddress());
                new Thread(() -> handleConnection(sock)).start();
            } while (true);
        }
    }

    private static void handleConnection(Socket socket) {
        // Buffer receptor
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            byte[] chunk = new byte[1024];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
                byte[] data = buffer.toByteArray();
                int offset = 0;

                // Extrae todas las tramas completas en 'data'
                while (true) {
                    // Como mínimo necesitamos header(2) + len(1) + CRC(2) + protocolo(1)
                    if (data.length - offset < 6) break;

                    // Busca 0x7878
                    if ((data[offset] & 0xFF) != 0x78 || (data[offset + 1] & 0xFF) != 0x78) {
                        offset++;
                        continue;
                    }

                    int len = data[offset + 2] & 0xFF;
                    int frameLen = 2 /*hdr*/ + 1 /*len*/ + len + 2 /*CRC*/;
                    if (data.length - offset < frameLen) {
                        break;
                    }

                    // Extrae paquete completo
                    byte[] packet = Arrays.copyOfRange(data, offset, offset + frameLen);
                    offset += frameLen;

                    // --- Procesa paquete ---
                    System.out.println("Paquete completo: " + bytesToHex(packet));
                    log("PAQUETE: " + bytesToHex(packet));
                    System.out.println("Validación CRC saltada.");

                    // 1) Decodificación segura
                    try {
                        String decoded = PacketParser.parsePacket(packet);
                        System.out.println(decoded);
                        log(decoded);
                    } catch (Exception e) {
                        System.err.println("Error decodificando paquete: " + e.getMessage());
                        log("ERROR parse: " + e.getMessage());
                    }

                    // 2) Extraer protocolo y serial
                    int protocol = packet[3] & 0xFF;
                    // Los dos bytes antes del CRC:
                    int serialPos = packet.length - 4;
                    byte[] serial = new byte[] {
                            packet[serialPos],
                            packet[serialPos + 1]
                    };

                    // 3) Enviar ACK o TimeResponse
                    if (protocol == 0x8A) {
                        byte[] resp = buildTimeResponse(serial);
                        out.write(resp);
                        out.flush();
                        System.out.println("ACK 0x8A enviado: " + bytesToHex(resp));
                        log("ACK 0x8A enviado: " + bytesToHex(resp));
                    } else {
                        byte[] ack = generateAck(protocol, serial);
                        if (ack != null) {
                            out.write(ack);
                            out.flush();
                            System.out.println("ACK enviado: " + bytesToHex(ack));
                            log("ACK enviado: " + bytesToHex(ack));
                        }
                    }
                }

                // Deja en buffer los bytes que quedaron tras offset
                if (offset > 0) {
                    buffer.reset();
                    buffer.write(data, offset, data.length - offset);
                }
            }

        } catch (IOException e) {
            System.err.println("Conexión finalizada: " + e.getMessage());
        }
    }

    private static byte[] generateAck(int protocol, byte[] serial) {
        switch (protocol) {
            case 0x01: case 0x13: case 0x22:
            case 0x26: case 0x27: case 0x28:
            case 0x2A: case 0xA0: case 0xA1:
            case 0xA4:
                return buildAckPacket(protocol, serial);
            default:
                return null;
        }
    }

    private static byte[] buildAckPacket(int protocol, byte[] serial) {
        ByteBuffer buf = ByteBuffer.allocate(10);
        buf.put((byte) 0x78).put((byte) 0x78);
        buf.put((byte) 0x05).put((byte) protocol);
        buf.put(serial);
        int crc = crc16X25(buf.array(), 2, 4);
        buf.put((byte) (crc >> 8)).put((byte) crc);
        buf.put((byte) 0x0D).put((byte) 0x0A);
        return buf.array();
    }

    private static byte[] buildTimeResponse(byte[] serial) {
        ByteBuffer buf = ByteBuffer.allocate(16);
        buf.put((byte) 0x78).put((byte) 0x78);
        buf.put((byte) 0x0B).put((byte) 0x8A);
        String ts = new SimpleDateFormat("yyMMddHHmmss").format(new Date());
        for (int i = 0; i < 6; i++) {
            buf.put((byte) Integer.parseInt(ts.substring(i*2, i*2+2)));
        }
        buf.put(serial);
        int crc = crc16X25(buf.array(), 2, 10);
        buf.put((byte) (crc >> 8)).put((byte) crc);
        buf.put((byte) 0x0D).put((byte) 0x0A);
        return buf.array();
    }

    private static int crc16X25(byte[] data, int start, int len) {
        int crc = 0xFFFF;
        for (int i = start; i < start + len; i++) {
            crc ^= data[i] & 0xFF;
            for (int j = 0; j < 8; j++) {
                if ((crc & 1) != 0) {
                    crc = (crc >>> 1) ^ 0x8408;
                } else {
                    crc >>>= 1;
                }
            }
        }
        return (~crc) & 0xFFFF;
    }

    private static String bytesToHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02X ", x));
        return sb.toString().trim();
    }

    private static void log(String msg) {
        try (FileWriter fw = new FileWriter(LOG_FILE, true)) {
            String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new Date());
            fw.write("[" + ts + "] " + msg + "\n");
        } catch (IOException ignored) { }
    }

}

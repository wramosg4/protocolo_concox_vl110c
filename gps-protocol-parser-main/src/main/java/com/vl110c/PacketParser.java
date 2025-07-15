package com.vl110c;

//import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PacketParser {

    private static final List<String> logMessages = new ArrayList<>();

    public static String parsePacket(byte[] data) {
        if (data.length < 5) {
            return log("Paquete demasiado corto.");
        }

        // Solo maneja tramas GT06 estándar (0x78 0x78)
        if ((data[0] & 0xFF) != 0x78 || (data[1] & 0xFF) != 0x78) {
            return log("Protocolo no GT06 o encabezado inválido.");
        }

        int length = Byte.toUnsignedInt(data[2]);
        int protocol = Byte.toUnsignedInt(data[3]);

        // Contenido real: desde 4 hasta 4 + (length-5)
        int contentLen = length - 5;
        if (data.length < 4 + contentLen + 2) {
            return log("Paquete mal formado o incompleto.");
        }
        byte[] content = Arrays.copyOfRange(data, 4, 4 + contentLen);

        String result;
        try {
            switch (protocol) {
                case 0x01:
                    result = parseLogin(content);
                    break;
                case 0x13:
                    result = parseHeartbeat(content);
                    break;
                case 0x22:
                    result = parseGps(content);
                    break;
                case 0x21:
                    result = parseResponseOnline(content);
                    break;
                case 0x26:
                    result = parseAlarm(content);
                    break;
                case 0x28:
                    result = parseLbsMulti(content);
                    break;
                case 0x2A:
                    result = parseAddressRequest(content);
                    break;
                case 0x80:
                    result = parseOnlineCommand(content);
                    break;
                case 0x8A:
                    result = parseTimeCalibration(content);
                    break;
                case 0x94:
                    result = parseInfoTransfer(content);
                    break;
                case 0x17:
                    result = parseChineseAddress(content);
                    break;
                case 0x97:
                    result = parseEnglishAddress(content);
                    break;
                case 0xA0:
                    result = parseGps(content);
                    break;
                case 0xA1:
                    result = parseLbs4G(content);
                    break;
                case 0xA4:
                    result = parseMultiFence(content);
                    break;
                default:
                    result = String.format(
                            "Protocolo desconocido: 0x%02X (contenido: %s)",
                            protocol, bytesToHex(content)
                    );
            }
        } catch (Exception e) {
            result = String.format(
                    "ERROR parseando 0x%02X: %s",
                    protocol, e.getMessage()
            );
        }

        return log(String.format("Protocolo 0x%02X -> %s", protocol, result));
    }

    private static String parseLogin(byte[] content) {
        if (content.length < 8) {
            return "LOGIN packet muy corto.";
        }
        StringBuilder imei = new StringBuilder();
        for (byte b : content) {
            imei.append((b >> 4) & 0x0F).append(b & 0x0F);
        }
        while (imei.length() > 0 && imei.charAt(0) == '0') {
            imei.deleteCharAt(0);
        }
        return "LOGIN IMEI=" + imei;
    }

    private static String parseHeartbeat(byte[] content) {
        if (content.length < 2) {
            return "HEARTBEAT packet muy corto.";
        }
        int status = Byte.toUnsignedInt(content[0]);
        boolean ign     = (status & 0x02) != 0;
        boolean charge  = (status & 0x04) != 0;
        boolean blocked = (status & 0x80) != 0;
        return String.format(
                "STATUS Ign=%b Charge=%b Blocked=%b Code=0x%02X",
                ign, charge, blocked, status
        );
    }

    private static String parseGps(byte[] buf) {
        // Fecha / hora UTC
        int day    = Byte.toUnsignedInt(buf[0]);
        int month  = Byte.toUnsignedInt(buf[1]);
        int year   = Byte.toUnsignedInt(buf[2]) + 2000;
        int hour   = Byte.toUnsignedInt(buf[3]);
        int minute = Byte.toUnsignedInt(buf[4]);
        int second = Byte.toUnsignedInt(buf[5]);
        // Satélites
        int sats = Byte.toUnsignedInt(buf[6]) & 0x0F;
        // Lat / Lon bruto
        long latRaw = byte4ToLong(buf, 7);
        long lonRaw = byte4ToLong(buf, 11);
        double lat = latRaw / 60.0 / 30000.0;
        double lon = lonRaw / 60.0 / 30000.0;
        // Velocidad
        int speed = Byte.toUnsignedInt(buf[15]);
        // Curso / flags
        int flags = ((buf[16] & 0xFF) << 8) | (buf[17] & 0xFF);
        int course = flags & 0x03FF;
        boolean valid = (flags & 0x1000) != 0;

        return String.format(
                "GPS %04d-%02d-%02d %02d:%02d:%02d | Sat:%d | Lat:%.6f | Lon:%.6f | Vel:%dkm/h | Curso:%d° | Válido:%b",
                year, month, day, hour, minute, second,
                sats, lat, lon, speed, course, valid
        );
    }

    private static String parseResponseOnline(byte[] content) {
        String text = new String(content, StandardCharsets.US_ASCII).trim();
        return "RESPONSE ONLINE: " + text;
    }

    private static String parseAlarm(byte[] content) {
        // Simple -  UTC + resto en hex
        if (content.length >= 6) {
            String time = String.format("%02d-%02d-%02d %02d:%02d:%02d",
                    Byte.toUnsignedInt(content[0]),  // día
                    Byte.toUnsignedInt(content[1]),  // mes
                    Byte.toUnsignedInt(content[2])+2000-2000, // año-2000
                    Byte.toUnsignedInt(content[3]),  // hora
                    Byte.toUnsignedInt(content[4]),  // min
                    Byte.toUnsignedInt(content[5])   // seg
            );
            return "ALARM UTC=" + time + " DATA=" + bytesToHex(Arrays.copyOfRange(content, 6, content.length));
        } else {
            return "ALARM RAW=" + bytesToHex(content);
        }
    }

    private static String parseLbsMulti(byte[] content) {
        return "LBS MULTI(BASE) RAW=" + bytesToHex(content);
    }

    private static String parseAddressRequest(byte[] content) {
        return "ADDRESS REQUEST RAW=" + bytesToHex(content);
    }

    private static String parseOnlineCommand(byte[] content) {
        return "ONLINE COMMAND RAW=" + bytesToHex(content);
    }

    private static String parseTimeCalibration(byte[] content) {
        // 6 bytes BCD
        if (content.length >= 6) {
            int year  = bcd(content[0]) + 2000;
            int month = bcd(content[1]);
            int day   = bcd(content[2]);
            int hour  = bcd(content[3]);
            int min   = bcd(content[4]);
            int sec   = bcd(content[5]);
            return String.format("TIME CALIBRATION %04d-%02d-%02d %02d:%02d:%02d",
                    year, month, day, hour, min, sec);
        }
        return "TIME CALIB RAW=" + bytesToHex(content);
    }

    private static String parseInfoTransfer(byte[] content) {
        String text = new String(content, StandardCharsets.US_ASCII).trim();
        return "INFO TRANSFER: " + text;
    }

    private static String parseChineseAddress(byte[] content) {
        String text = new String(content, StandardCharsets.US_ASCII).trim();
        return "CHINESE ADDRESS: " + text;
    }

    private static String parseEnglishAddress(byte[] content) {
        String text = new String(content, StandardCharsets.US_ASCII).trim();
        return "ENGLISH ADDRESS: " + text;
    }

    private static String parseLbs4G(byte[] content) {
        return "LBS 4G RAW=" + bytesToHex(content);
    }

    private static String parseMultiFence(byte[] content) {
        if (content.length >= 1) {
            int fence = Byte.toUnsignedInt(content[0]);
            return "MULTI-FENCE ALARM ID=" + fence;
        }
        return "MULTI-FENCE RAW=" + bytesToHex(content);
    }

    // Helpers

    private static long byte4ToLong(byte[] b, int i) {
        return ((long)(b[i]   & 0xFF) << 24)
                | ((long)(b[i+1] & 0xFF) << 16)
                | ((long)(b[i+2] & 0xFF) <<  8)
                |  (long)(b[i+3] & 0xFF);
    }

    private static int bcd(byte b) {
        return ((b >> 4) & 0x0F) * 10 + (b & 0x0F);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte x : bytes) {
            sb.append(String.format("%02X ", x));
        }
        return sb.toString().trim();
    }

    private static String log(String message) {
        System.out.println(message);
        logMessages.add(message);
        return message;
    }

    public static List<String> getLogMessages() {
        return new ArrayList<>(logMessages);
    }

    public static void clearLog() {
        logMessages.clear();
    }
}

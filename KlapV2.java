package com.bbbun.tapol530;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class KlapV2 {
    private final String host;
    private final String username;
    private final String password;

    KlapV2(String host, String username, String password) {
        this.host = host;
        this.username = username;
        this.password = password;
    }

    boolean getPower() throws Exception {
        JSONObject result = request(new JSONObject()
                .put("method", "get_device_info"));
        return result.getJSONObject("result").optBoolean("device_on", false);
    }

    boolean setPower(boolean on) throws Exception {
        JSONObject result = request(new JSONObject()
                .put("method", "set_device_info")
                .put("params", new JSONObject().put("device_on", on)));
        JSONObject r = result.optJSONObject("result");
        return r == null || !r.has("device_on") || r.optBoolean("device_on", on);
    }

    private JSONObject request(JSONObject body) throws Exception {
        byte[] auth = sha256(concat(
                sha1(username.getBytes(StandardCharsets.UTF_8)),
                sha1(password.getBytes(StandardCharsets.UTF_8))
        ));
        byte[] localSeed = new byte[16];
        new SecureRandom().nextBytes(localSeed);

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, 80), 5000);
            socket.setSoTimeout(7000);
            socket.setKeepAlive(true);

            HttpResponse h1 = post(socket, "/app/handshake1", localSeed, null);
            if (h1.code != 200 || h1.body.length < 48)
                throw new IOException("KLAP handshake1 failed: HTTP " + h1.code);

            byte[] remoteSeed = slice(h1.body, 0, 16);
            byte[] serverHash = slice(h1.body, 16, 32);
            byte[] expected = sha256(concat(localSeed, remoteSeed, auth));
            if (!MessageDigest.isEqual(serverHash, expected))
                throw new IOException("Authentication failed");

            String cookie = h1.cookie;
            if (cookie == null || cookie.isEmpty())
                throw new IOException("L530 did not return TP_SESSIONID");

            byte[] h2 = sha256(concat(remoteSeed, localSeed, auth));
            HttpResponse r2 = post(socket, "/app/handshake2", h2, cookie);
            if (r2.code != 200)
                throw new IOException("KLAP handshake2 failed: HTTP " + r2.code);

            Session s = new Session(localSeed, remoteSeed, auth);
            Encrypted enc = s.encrypt(body.toString().getBytes(StandardCharsets.UTF_8));
            HttpResponse rr = post(socket, "/app/request?seq=" + enc.seq, enc.payload, cookie);
            if (rr.code == 403) throw new IOException("L530 rejected the session");
            if (rr.code != 200) throw new IOException("L530 request failed: HTTP " + rr.code);

            return new JSONObject(s.decrypt(rr.body));
        }
    }

    private static HttpResponse post(Socket socket, String path, byte[] data, String cookie)
            throws IOException {
        OutputStream out = socket.getOutputStream();
        StringBuilder h = new StringBuilder();
        h.append("POST ").append(path).append(" HTTP/1.1\r\n");
        h.append("Host: ").append("tapo-l530").append("\r\n");
        h.append("Content-Type: application/octet-stream\r\n");
        h.append("Content-Length: ").append(data.length).append("\r\n");
        h.append("Connection: keep-alive\r\n");
        if (cookie != null) h.append("Cookie: TP_SESSIONID=").append(cookie).append("\r\n");
        h.append("\r\n");
        out.write(h.toString().getBytes(StandardCharsets.US_ASCII));
        out.write(data);
        out.flush();

        InputStream in = socket.getInputStream();
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int state = 0;
        while (true) {
            int b = in.read();
            if (b < 0) throw new IOException("Connection closed");
            head.write(b);
            if (state == 0 && b == '\r') state = 1;
            else if (state == 1 && b == '\n') state = 2;
            else if (state == 2 && b == '\r') state = 3;
            else if (state == 3 && b == '\n') break;
            else state = (b == '\r') ? 1 : 0;
            if (head.size() > 16384) throw new IOException("HTTP headers too large");
        }

        String hs = head.toString(StandardCharsets.ISO_8859_1.name());
        String[] lines = hs.split("\r\n");
        int code = Integer.parseInt(lines[0].split(" ")[1]);
        int len = -1;
        String cookieOut = null;
        boolean chunked = false;
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.US);
            if (lower.startsWith("content-length:"))
                len = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
            if (lower.startsWith("transfer-encoding:") && lower.toLowerCase(Locale.US).contains("chunked"))
                chunked = true;
            if (lower.startsWith("set-cookie:")) {
                String v = line.substring(line.indexOf(':') + 1).trim();
                int p = v.indexOf("TP_SESSIONID=");
                if (p >= 0) {
                    String x = v.substring(p + "TP_SESSIONID=".length());
                    int semi = x.indexOf(';');
                    cookieOut = semi >= 0 ? x.substring(0, semi) : x;
                }
            }
        }

        byte[] body;
        if (chunked) {
            body = readChunked(in);
        } else if (len >= 0) {
            body = readExactly(in, len);
        } else {
            body = new byte[0];
        }
        return new HttpResponse(code, body, cookieOut);
    }

    private static byte[] readExactly(InputStream in, int n) throws IOException {
        byte[] b = new byte[n];
        int off = 0;
        while (off < n) {
            int r = in.read(b, off, n - off);
            if (r < 0) throw new IOException("Unexpected EOF");
            off += r;
        }
        return b;
    }

    private static byte[] readChunked(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (true) {
            String line = readLine(in);
            int semi = line.indexOf(';');
            int size = Integer.parseInt((semi >= 0 ? line.substring(0, semi) : line).trim(), 16);
            if (size == 0) {
                readLine(in);
                return out.toByteArray();
            }
            out.write(readExactly(in, size));
            readExactly(in, 2);
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        while (true) {
            int x = in.read();
            if (x < 0) throw new IOException("Unexpected EOF");
            if (x == '\n') return b.toString(StandardCharsets.ISO_8859_1.name()).replace("\r", "");
            b.write(x);
        }
    }

    private static final class Session {
        final byte[] key, iv12, sig;
        int seq;

        Session(byte[] local, byte[] remote, byte[] auth) throws Exception {
            key = slice(sha256(concat("lsk".getBytes(StandardCharsets.US_ASCII), local, remote, auth)), 0, 16);
            byte[] fullIv = sha256(concat("iv".getBytes(StandardCharsets.US_ASCII), local, remote, auth));
            iv12 = slice(fullIv, 0, 12);
            seq = ByteBuffer.wrap(fullIv, 28, 4).getInt();
            sig = slice(sha256(concat("ldk".getBytes(StandardCharsets.US_ASCII), local, remote, auth)), 0, 28);
        }

        Encrypted encrypt(byte[] plain) throws Exception {
            seq++;
            byte[] iv = new byte[16];
            System.arraycopy(iv12, 0, iv, 0, 12);
            ByteBuffer.wrap(iv, 12, 4).putInt(seq);

            Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            byte[] ciphertext = c.doFinal(plain);

            ByteBuffer sb = ByteBuffer.allocate(sig.length + 4 + ciphertext.length);
            sb.put(sig).putInt(seq).put(ciphertext);
            byte[] signature = sha256(sb.array());

            return new Encrypted(concat(signature, ciphertext), seq);
        }

        String decrypt(byte[] payload) throws Exception {
            if (payload.length < 32) throw new IOException("Encrypted response too short");
            byte[] iv = new byte[16];
            System.arraycopy(iv12, 0, iv, 0, 12);
            ByteBuffer.wrap(iv, 12, 4).putInt(seq);

            Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            return new String(c.doFinal(slice(payload, 32, payload.length - 32)),
                    StandardCharsets.UTF_8);
        }
    }

    private record Encrypted(byte[] payload, int seq) {}
    private record HttpResponse(int code, byte[] body, String cookie) {}

    static byte[] sha1(byte[] x) throws Exception { return digest("SHA-1", x); }
    static byte[] sha256(byte[] x) throws Exception { return digest("SHA-256", x); }

    private static byte[] digest(String alg, byte[] x) throws Exception {
        return MessageDigest.getInstance(alg).digest(x);
    }

    static byte[] concat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) n += p.length;
        byte[] out = new byte[n];
        int at = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, at, p.length);
            at += p.length;
        }
        return out;
    }

    static byte[] slice(byte[] x, int start, int len) {
        byte[] out = new byte[len];
        System.arraycopy(x, start, out, 0, len);
        return out;
    }
}

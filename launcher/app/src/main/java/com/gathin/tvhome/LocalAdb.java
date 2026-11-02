package com.gathin.tvhome;

import android.content.Context;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * A tiny ADB client that runs one shell command through the TV's own adbd on 127.0.0.1:5555 (Network debugging
 * must be on). That is the only way an ordinary app can run pm enable / pm disable-user: the permission for it is
 * signature-only. The first call shows the TV's "Allow USB debugging?" prompt for this app's key; tick
 * "Always allow". The key lives in the app's private files. Blocking: call it off the main thread.
 */
final class LocalAdb {
    private static final int CNXN = 0x4e584e43, AUTH = 0x48545541, OPEN = 0x4e45504f, OKAY = 0x59414b4f,
            CLSE = 0x45534c43, WRTE = 0x45545257;
    private static final int VERSION = 0x01000000, MAX_DATA = 4096;
    private static final int AUTH_TOKEN = 1, AUTH_SIGNATURE = 2, AUTH_RSAPUBLICKEY = 3;
    /** DER prefix of a SHA-1 DigestInfo: adbd checks the token with RSA_verify(NID_sha1, token, 20, sig). */
    private static final byte[] SHA1_PREFIX = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a,
            0x05, 0x00, 0x04, 0x14};

    /** Thrown when adbd is not listening (Network debugging off) or the key was not allowed on the TV. */
    static final class AdbException extends IOException {
        final boolean notAllowed;
        AdbException(String message, boolean notAllowed) {
            super(message);
            this.notAllowed = notAllowed;
        }
    }

    private LocalAdb() {}

    /** Runs {@code command} in an adb shell and returns its output. */
    static String shell(Context context, String command) throws IOException {
        KeyPair keys = loadOrCreateKeys(context);
        Socket socket = connect(keys);
        if (socket == null) {
            // Key not known yet: offer it on one connection, which makes the TV show its prompt, and keep that
            // connection open (closing it cancels the prompt). Android 9's adbd may answer the prompt on an older
            // connection instead of this one, so try fresh ones until the key, now saved, is accepted.
            Socket prompt = new Socket();
            try {
                prompt.connect(new InetSocketAddress("127.0.0.1", 5555), 3000);
                prompt.setSoTimeout(10_000);
                DataInputStream in = new DataInputStream(prompt.getInputStream());
                OutputStream out = prompt.getOutputStream();
                send(out, CNXN, VERSION, MAX_DATA, "host::\0".getBytes(StandardCharsets.UTF_8));
                Message m = read(in);
                if (m.command == AUTH && m.arg0 == AUTH_TOKEN) {
                    send(out, AUTH, AUTH_SIGNATURE, 0, sign(keys.getPrivate(), m.data));
                    m = read(in);
                    if (m.command == AUTH) send(out, AUTH, AUTH_RSAPUBLICKEY, 0, publicKeyBlob((RSAPublicKey) keys.getPublic()));
                }
                long deadline = System.currentTimeMillis() + 60_000;
                while (socket == null && System.currentTimeMillis() < deadline) {
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    socket = connect(keys);
                }
            } finally {
                try {
                    prompt.close();
                } catch (IOException ignored) {
                }
            }
            if (socket == null) throw new AdbException("Debugging prompt not allowed", true);
        }
        try {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();
            int localId = 1, remoteId = 0;
            send(out, OPEN, localId, 0, ("shell:" + command + "\0").getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            socket.setSoTimeout(30_000);
            while (true) {
                Message m = read(in);
                if (m.command == OKAY) {
                    remoteId = m.arg0;
                } else if (m.command == WRTE) {
                    output.write(m.data);
                    send(out, OKAY, localId, m.arg0, new byte[0]);
                } else if (m.command == CLSE) {
                    send(out, CLSE, localId, m.arg0 != 0 ? m.arg0 : remoteId, new byte[0]);
                    break;
                }
            }
            return output.toString("UTF-8").trim();
        } finally {
            socket.close();
        }
    }

    /**
     * Opens a connection and signs in with the saved key. Returns the connected socket, or null when adbd does
     * not know the key yet. Throws when nothing listens on 5555.
     */
    private static Socket connect(KeyPair keys) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress("127.0.0.1", 5555), 3000);
        } catch (IOException e) {
            socket.close();
            throw new AdbException("Network debugging is off", false);
        }
        try {
            socket.setSoTimeout(10_000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();
            send(out, CNXN, VERSION, MAX_DATA, "host::\0".getBytes(StandardCharsets.UTF_8));
            boolean signed = false;
            while (true) {
                Message m = read(in);
                if (m.command == CNXN) return socket;
                if (m.command != AUTH || m.arg0 != AUTH_TOKEN) continue;
                if (signed) {
                    socket.close();
                    return null;
                }
                send(out, AUTH, AUTH_SIGNATURE, 0, sign(keys.getPrivate(), m.data));
                signed = true;
            }
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    private static final class Message {
        int command, arg0, arg1;
        byte[] data;
    }

    private static void send(OutputStream out, int command, int arg0, int arg1, byte[] data) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        int sum = 0;
        for (byte b : data) sum += b & 0xff;
        header.putInt(command).putInt(arg0).putInt(arg1).putInt(data.length).putInt(sum).putInt(~command);
        out.write(header.array());
        out.write(data);
        out.flush();
    }

    private static Message read(DataInputStream in) throws IOException {
        byte[] raw = new byte[24];
        in.readFully(raw);
        ByteBuffer header = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        Message m = new Message();
        m.command = header.getInt();
        m.arg0 = header.getInt();
        m.arg1 = header.getInt();
        int length = header.getInt();
        if (length < 0 || length > 1 << 20) throw new IOException("Bad adb message");
        m.data = new byte[length];
        in.readFully(m.data);
        return m;
    }

    private static byte[] sign(PrivateKey key, byte[] token) throws IOException {
        try {
            Signature s = Signature.getInstance("NONEwithRSA");
            s.initSign(key);
            s.update(SHA1_PREFIX);
            s.update(token);
            return s.sign();
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    /** The key in adbd's own format: android_pubkey_encode, base64, then a name. */
    static byte[] publicKeyBlob(RSAPublicKey key) {
        BigInteger n = key.getModulus();
        int words = 2048 / 32;
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        BigInteger n0inv = n.mod(r32).modInverse(r32).negate().mod(r32);
        BigInteger rr = BigInteger.ONE.shiftLeft(4096).mod(n);
        ByteBuffer b = ByteBuffer.allocate(4 + 4 + 256 + 256 + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(words).putInt(n0inv.intValue());
        b.put(littleEndian(n, 256)).put(littleEndian(rr, 256));
        b.putInt(key.getPublicExponent().intValue());
        String text = Base64.encodeToString(b.array(), Base64.NO_WRAP) + " tvhome@launcher\0";
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] littleEndian(BigInteger value, int size) {
        byte[] big = value.toByteArray();
        byte[] out = new byte[size];
        for (int i = 0; i < size && i < big.length; i++) out[i] = big[big.length - 1 - i];
        return out;
    }

    private static synchronized KeyPair loadOrCreateKeys(Context context) throws IOException {
        File priv = new File(context.getFilesDir(), "adbkey");
        File pub = new File(context.getFilesDir(), "adbkey.pub.der");
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            if (priv.exists() && pub.exists()) {
                return new KeyPair(
                        kf.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(pub.toPath()))),
                        kf.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(priv.toPath()))));
            }
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair pair = gen.generateKeyPair();
            try (FileOutputStream o = new FileOutputStream(priv)) {
                o.write(pair.getPrivate().getEncoded());
            }
            try (FileOutputStream o = new FileOutputStream(pub)) {
                o.write(pair.getPublic().getEncoded());
            }
            return pair;
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }
}

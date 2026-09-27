package com.bbbun.tapol530;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class Prefs {
    private static final String PREFS = "config";
    private static final String KEY_ALIAS = "tapo_l530_key";

    private Prefs() {}

    static void save(Context c, String ip, String email, String password) throws Exception {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        p.edit()
            .putString("ip", ip.trim())
            .putString("email", encrypt(email))
            .putString("password", encrypt(password))
            .apply();
    }

    static String ip(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("ip", "");
    }

    static String email(Context c) throws Exception {
        return decrypt(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("email", ""));
    }

    static String password(Context c) throws Exception {
        return decrypt(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("password", ""));
    }

    static boolean configured(Context c) {
        return !ip(c).isEmpty() &&
            !c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("email", "").isEmpty();
    }

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) ks.getEntry(KEY_ALIAS, null)).getSecretKey();
        }
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return kg.generateKey();
    }

    private static String encrypt(String plain) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        byte[] out = new byte[12 + cipherText.length];
        System.arraycopy(cipher.getIV(), 0, out, 0, 12);
        System.arraycopy(cipherText, 0, out, 12, cipherText.length);
        return Base64.encodeToString(out, Base64.NO_WRAP);
    }

    private static String decrypt(String encoded) throws Exception {
        if (encoded == null || encoded.isEmpty()) return "";
        byte[] all = Base64.decode(encoded, Base64.NO_WRAP);
        byte[] iv = new byte[12];
        System.arraycopy(all, 0, iv, 0, 12);
        byte[] ct = new byte[all.length - 12];
        System.arraycopy(all, 12, ct, 0, ct.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
    }
}

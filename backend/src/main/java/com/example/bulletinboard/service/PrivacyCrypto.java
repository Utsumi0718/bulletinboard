package com.example.bulletinboard.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 鍵はDB外。認証付き暗号と対象IDのAADで、暗号文の差し替えも拒否する。 */
@Component
public class PrivacyCrypto {
    private final String encodedKey;
    private final String keyId;
    private final SecureRandom random=new SecureRandom();
    public PrivacyCrypto(@Value("${privacy.encryption.key:}") String key,
            @Value("${privacy.encryption.key-id:primary}") String keyId) {
        this.encodedKey=key; this.keyId=keyId;
    }
    public String keyId() { return keyId; }
    public void requireKey(String expectedId) {
        key();
        if (!keyId.equals(expectedId)) throw new IllegalStateException("Privacy key unavailable");
    }
    private SecretKeySpec key() {
        try {
            byte[] bytes=Base64.getDecoder().decode(encodedKey);
            if(bytes.length!=32 || keyId.isBlank() || keyId.length()>80) throw new IllegalArgumentException();
            return new SecretKeySpec(bytes,"AES");
        } catch(IllegalArgumentException ex) { throw new IllegalStateException("Privacy key unavailable"); }
    }
    public byte[] encrypt(byte[] value,String aad) {
        try {
            byte[] iv=new byte[12]; random.nextBytes(iv);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,key(),new GCMParameterSpec(128,iv));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted=cipher.doFinal(value);
            return ByteBuffer.allocate(iv.length+encrypted.length).put(iv).put(encrypted).array();
        } catch(Exception ex) { throw new IllegalStateException("Privacy encryption failed"); }
    }
    public byte[] decrypt(byte[] value,String aad,String expectedId) {
        requireKey(expectedId);
        try {
            if(value==null || value.length<28) throw new IllegalArgumentException();
            ByteBuffer input=ByteBuffer.wrap(value); byte[] iv=new byte[12]; input.get(iv);
            byte[] encrypted=new byte[input.remaining()]; input.get(encrypted);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(encrypted);
        } catch(Exception ex) { throw new IllegalStateException("Privacy decryption failed"); }
    }
}

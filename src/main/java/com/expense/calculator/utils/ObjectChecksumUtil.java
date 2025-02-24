package com.expense.calculator.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class ObjectChecksumUtil {

    public static String generateChecksum(Object obj) throws IOException, NoSuchAlgorithmException {
        // Convert object to byte array
        byte[] objectBytes = toByteArray(obj);

        // Compute SHA-256 checksum
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(objectBytes);

        // Convert to Base64 for readability
        return Base64.getEncoder().encodeToString(hashBytes);
    }

    private static byte[] toByteArray(Object obj) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(obj);
        oos.flush();
        return bos.toByteArray();
    }
}

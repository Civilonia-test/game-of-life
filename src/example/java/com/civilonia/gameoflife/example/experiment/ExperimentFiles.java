package com.civilonia.gameoflife.example.experiment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Shared bounded file/resource operations for the example applications. */
final class ExperimentFiles {
    private ExperimentFiles() { }

    static byte[] resource(String name) throws IOException {
        try (InputStream input = ExperimentFiles.class.getResourceAsStream("/experiments/" + name)) {
            if (input == null) {
                throw new IOException("missing bundled experiment resource: " + name);
            }
            byte[] bytes = input.readNBytes(65_537);
            if (bytes.length > 65_536) {
                throw new IOException("experiment resource exceeds 64 KiB: " + name);
            }
            return bytes;
        }
    }

    static byte[] readBounded(Path path, int limit) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(limit + 1);
            if (bytes.length > limit) {
                throw new IOException("file exceeds " + limit + " bytes: " + path);
            }
            return bytes;
        }
    }

    static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(digest().digest(bytes));
    }

    static void create(Path path, byte[] bytes) throws IOException {
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    static void distinct(Path first, Path second) throws IOException {
        if (first.toAbsolutePath().normalize().equals(second.toAbsolutePath().normalize())
                || Files.exists(first) && Files.exists(second) && Files.isSameFile(first, second)) {
            throw new IOException("paths must be distinct: " + first + " and " + second);
        }
    }
}

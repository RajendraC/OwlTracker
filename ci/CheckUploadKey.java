import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Checks the upload key before Gradle signs with it, and passes the values that actually work on to
 * the build as OWL_TRACKER_KEYSTORE_PASSWORD, OWL_TRACKER_KEY_ALIAS and OWL_TRACKER_KEY_PASSWORD.
 *
 * Secrets are often pasted with a stray space, a line break or quotes, and a key made in Android
 * Studio is called "key0" rather than the name in the guide. This copes with those, says what it
 * did, and otherwise stops with a message that names the secret to fix.
 *
 * Run with: java ci/CheckUploadKey.java  (JDK 17+)
 * Reads: KEYSTORE_FILE, UPLOAD_KEYSTORE_PASSWORD, UPLOAD_KEY_ALIAS, UPLOAD_KEY_PASSWORD
 * Writes: GITHUB_ENV and GITHUB_STEP_SUMMARY when they are set (on GitHub Actions)
 */
public class CheckUploadKey {

    static final PrintStream out = System.out;
    static final List<String> notes = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        String file = env("KEYSTORE_FILE");
        String storePassword = env("UPLOAD_KEYSTORE_PASSWORD");
        String alias = env("UPLOAD_KEY_ALIAS");
        String keyPassword = env("UPLOAD_KEY_PASSWORD");

        if (file.isEmpty() || !new File(file).isFile() || new File(file).length() == 0) {
            fail("The key file wasn't restored. Repeat the PowerShell command in step A5 and paste UPLOAD_KEYSTORE_BASE64 again.");
        }

        // 1. Open the key file with the store password.
        KeyStore ks = null;
        String usedStorePassword = null;
        boolean formatProblem = false;
        for (String p : variants(storePassword)) {
            try {
                ks = KeyStore.getInstance(new File(file), p.toCharArray());
                usedStorePassword = p;
                break;
            } catch (KeyStoreException e) {
                formatProblem = true; // not a keystore at all
                break;
            } catch (IOException e) {
                // wrong password (or damaged file); try the next spelling
            }
        }
        if (formatProblem) {
            fail("UPLOAD_KEYSTORE_BASE64 doesn't hold a key file (.jks). Repeat the PowerShell command in step A5 and paste that secret again.");
        }
        if (ks == null) {
            fail("UPLOAD_KEYSTORE_PASSWORD doesn't open the key file. Set that secret to the password you chose when you created the key in step A4.");
        }
        if (!usedStorePassword.equals(storePassword)) {
            notes.add("UPLOAD_KEYSTORE_PASSWORD had extra spaces, line breaks or quotes around it; they were ignored.");
        }

        // 2. Find the key.
        List<String> keys = new ArrayList<>();
        for (String a : Collections.list(ks.aliases())) {
            if (ks.isKeyEntry(a)) keys.add(a);
        }
        if (keys.isEmpty()) {
            fail("The key file opens but has no signing key in it. Create the key again with the keytool command in step A4.");
        }
        String usedAlias = null;
        for (String a : variants(alias)) {
            for (String k : keys) {
                if (k.equalsIgnoreCase(a)) { usedAlias = k; break; }
            }
            if (usedAlias != null) {
                if (!a.equals(alias)) {
                    notes.add("UPLOAD_KEY_ALIAS had extra spaces, line breaks or quotes around it; they were ignored.");
                }
                break;
            }
        }
        if (usedAlias == null) {
            // Same name apart from spaces, dashes or capitals, e.g. "owl upload" for "owl-upload"
            List<String> close = new ArrayList<>();
            for (String k : keys) if (loose(k).equals(loose(alias))) close.add(k);
            if (close.size() == 1) {
                usedAlias = close.get(0);
            } else if (keys.size() == 1) {
                usedAlias = keys.get(0);
            }
            if (usedAlias != null) {
                notes.add("UPLOAD_KEY_ALIAS doesn't match the key's name. The key file " + (keys.size() == 1 ? "has one key" : "has a key")
                        + ", named '" + usedAlias + "', and the bundle was signed with it. To remove this note, set UPLOAD_KEY_ALIAS to " + usedAlias + ".");
            } else {
                fail("UPLOAD_KEY_ALIAS doesn't match any key in the key file. Its keys are named: " + String.join(", ", keys)
                        + ". Set UPLOAD_KEY_ALIAS to the one you made for Owl Tracker.");
            }
        }

        // 3. Unlock the key. Keys made by keytool since Java 9 (PKCS12) use the store password,
        //    so that is tried too if the key password doesn't work.
        String usedKeyPassword = null;
        LinkedHashSet<String> tries = new LinkedHashSet<>(variants(keyPassword));
        tries.add(usedStorePassword);
        for (String p : tries) {
            try {
                if (ks.getKey(usedAlias, p.toCharArray()) != null) { usedKeyPassword = p; break; }
            } catch (java.security.UnrecoverableKeyException e) {
                // wrong password; try the next one
            }
        }
        if (usedKeyPassword == null) {
            fail("UPLOAD_KEY_PASSWORD doesn't unlock the key '" + usedAlias + "', and neither does the key file password. Set UPLOAD_KEY_PASSWORD to the key's own password (for a key made with the command in step A4, that's the same password as UPLOAD_KEYSTORE_PASSWORD).");
        }
        if (!variants(keyPassword).contains(usedKeyPassword)) {
            notes.add("UPLOAD_KEY_PASSWORD didn't unlock the key, but the key file password did, so that was used. To remove this note, set UPLOAD_KEY_PASSWORD to the same password as UPLOAD_KEYSTORE_PASSWORD.");
        } else if (!usedKeyPassword.equals(keyPassword)) {
            notes.add("UPLOAD_KEY_PASSWORD had extra spaces, line breaks or quotes around it; they were ignored.");
        }

        // 4. Report and hand the working values to the build step.
        Certificate cert = ks.getCertificate(usedAlias);
        String sha256 = cert == null ? "?" : HexFormat.ofDelimiter(":").withUpperCase()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));

        mask(usedStorePassword);
        mask(usedKeyPassword);
        out.println("Key file opens, key '" + usedAlias + "' found and unlocked.");
        out.println("Upload key SHA-256: " + sha256);
        // CI's self-test uses a misnamed key on purpose; its notes are plain text, not run warnings.
        boolean selfTest = !env("UPLOAD_KEY_SELF_TEST").isEmpty();
        for (String n : notes) out.println((selfTest ? "Note: " : "::warning title=Upload key::") + n);

        String ghEnv = System.getenv("GITHUB_ENV");
        if (ghEnv != null && !ghEnv.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            put(sb, "OWL_TRACKER_KEYSTORE_PASSWORD", usedStorePassword);
            put(sb, "OWL_TRACKER_KEY_ALIAS", usedAlias);
            put(sb, "OWL_TRACKER_KEY_PASSWORD", usedKeyPassword);
            Files.writeString(Path.of(ghEnv), sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } else {
            out.println("Would use alias: " + usedAlias);
        }
        String summary = System.getenv("GITHUB_STEP_SUMMARY");
        if (summary != null && !summary.isEmpty() && !notes.isEmpty() && !selfTest) {
            StringBuilder sb = new StringBuilder("### Upload key notes\n\n");
            for (String n : notes) sb.append("- ").append(n).append('\n');
            sb.append('\n');
            Files.writeString(Path.of(summary), sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }

    static String env(String name) {
        String v = System.getenv(name);
        return v == null ? "" : v;
    }

    /** The secret as stored first, then without line breaks, surrounding spaces or quotes. */
    static List<String> variants(String s) {
        LinkedHashSet<String> v = new LinkedHashSet<>();
        v.add(s);
        v.add(s.replaceAll("[\\r\\n]+", ""));
        String t = s.strip();
        v.add(t);
        if (t.length() >= 2 && (t.startsWith("\"") && t.endsWith("\"") || t.startsWith("'") && t.endsWith("'"))) {
            v.add(t.substring(1, t.length() - 1).strip());
        }
        v.remove("");
        return new ArrayList<>(v);
    }

    static String loose(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Hides a value (each of its lines) from the rest of the job log. */
    static void mask(String value) {
        for (String line : value.split("\\R")) {
            if (!line.isBlank()) out.println("::add-mask::" + line);
        }
    }

    static void put(StringBuilder sb, String name, String value) {
        String end = "EOF_" + UUID.randomUUID().toString().replace("-", "");
        sb.append(name).append("<<").append(end).append('\n').append(value).append('\n').append(end).append('\n');
    }

    static void fail(String message) {
        out.println("::error title=Upload key::" + message);
        System.exit(1);
    }
}

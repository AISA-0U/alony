import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class LocalPasswordEncoder {
    private LocalPasswordEncoder() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: LocalPasswordEncoder <public-key> <password>");
        }

        byte[] keyBytes = Base64.getDecoder().decode(args[0]);
        PublicKey publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(keyBytes));
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encrypted = cipher.doFinal(args[1].getBytes(StandardCharsets.UTF_8));
        System.out.print(Base64.getEncoder().encodeToString(encrypted));
    }
}

package com.addoc.arca.arca.crypto;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Enumeration;

public class WsaaCrypto {

    static {
        // ✅ Registrar BouncyCastle solo una vez
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
            System.out.println("✅ [WsaaCrypto] BouncyCastle provider registrado correctamente");
        }
    }

    /** 🧩 Construye el XML del LoginTicketRequest (TRA) */
    public static String buildTRA(String service, int ttlMinutes) {
        Instant now = Instant.now();
        Instant gen = now.minus(10, ChronoUnit.MINUTES); // margen recomendado AFIP
        Instant exp = now.plus(ttlMinutes, ChronoUnit.MINUTES);

        long uniqueId = now.getEpochSecond();

        return ""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<loginTicketRequest version=\"1.0\">"
                + "  <header>"
                + "    <uniqueId>" + uniqueId + "</uniqueId>"
                + "    <generationTime>" + gen + "</generationTime>"
                + "    <expirationTime>" + exp + "</expirationTime>"
                + "  </header>"
                + "  <service>" + service + "</service>"
                + "</loginTicketRequest>";
    }

    /**
     * 🧩 Firma el TRA con PKCS#7 (CMS) y devuelve Base64 del CMS (modo detached)
     */
    public static String signCmsDetachedBase64(
            String traXml,
            String p12Path,
            String p12Password,
            String aliasOpt) throws Exception {

        // --- Cargar keystore PKCS#12 ---
        KeyStore ks = KeyStore.getInstance("PKCS12");

        try (InputStream is = resolveP12Stream(p12Path)) {
            if (is == null) {
                throw new FileNotFoundException("❌ No se encontró el archivo .p12 en: " + p12Path);
            }
            ks.load(is, p12Password.toCharArray());
        }

        // --- Obtener alias ---
        String alias = aliasOpt;
        if (alias == null || alias.isBlank()) {
            Enumeration<String> e = ks.aliases();
            alias = e.hasMoreElements() ? e.nextElement() : null;
        }
        if (alias == null)
            throw new Exception("❌ No se encontró alias en el archivo PKCS12 (" + p12Path + ")");

        PrivateKey privateKey = (PrivateKey) ks.getKey(alias, p12Password.toCharArray());
        Certificate cert = ks.getCertificate(alias);
        X509Certificate x509 = (X509Certificate) cert;

        if (privateKey == null || cert == null)
            throw new Exception("❌ No se pudo extraer clave privada o certificado del keystore");

        // --- Construir CMS PKCS#7 ---
        byte[] data = traXml.getBytes(StandardCharsets.UTF_8);
        CMSTypedData msg = new CMSProcessableByteArray(data);

        // 🔸 AFIP requiere SHA1withRSA
        ContentSigner signer = new JcaContentSignerBuilder("sha512RSA")
                .setProvider("BC")
                .build(privateKey);

        var calcProvBuilder = new JcaDigestCalculatorProviderBuilder().setProvider("BC");
        var sigInfoGenBuilder = new JcaSignerInfoGeneratorBuilder(calcProvBuilder.build());

        CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
        gen.addSignerInfoGenerator(sigInfoGenBuilder.build(signer, new X509CertificateHolder(x509.getEncoded())));
        gen.addCertificate(new X509CertificateHolder(x509.getEncoded()));

        // Modo detached (el XML no se incluye dentro del CMS)
        CMSSignedData signed = gen.generate(msg, false);
        byte[] encoded = signed.getEncoded();

        String base64 = Base64.getEncoder().encodeToString(encoded);
        System.out.println("✅ [WsaaCrypto] CMS firmado correctamente (" + base64.length() + " bytes Base64)");

        return base64;
    }

    /** 🔍 Resuelve InputStream tanto para classpath: como para rutas absolutas */
    private static InputStream resolveP12Stream(String path) throws Exception {
        if (path == null) return null;

        if (path.startsWith("classpath:")) {
            String cp = path.replace("classpath:", "").replace("\\", "/");
            return Thread.currentThread().getContextClassLoader().getResourceAsStream(cp);
        }

        File file = new File(path);
        if (file.exists()) {
            return new FileInputStream(file);
        }

        return null;
    }
}

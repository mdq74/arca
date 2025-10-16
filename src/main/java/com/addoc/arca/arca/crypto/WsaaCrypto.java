package com.addoc.arca.arca.crypto;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.*;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Enumeration;

public class WsaaCrypto {

  /** Construye el XML del LoginTicketRequest (TRA) */
  public static String buildTRA(String service, int ttlMinutes) {
    Instant now = Instant.now();
    Instant gen = now.minus(10, ChronoUnit.MINUTES);   // margen recomendado
    Instant exp = now.plus(ttlMinutes, ChronoUnit.MINUTES);

    long uniqueId = now.getEpochSecond();

    return ""
      + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
      + "<loginTicketRequest version=\"1.0\">"
      + "  <header>"
      + "    <uniqueId>" + uniqueId + "</uniqueId>"
      + "    <generationTime>" + gen.toString() + "</generationTime>"
      + "    <expirationTime>" + exp.toString() + "</expirationTime>"
      + "  </header>"
      + "  <service>" + service + "</service>"
      + "</loginTicketRequest>";
  }

  /** Firma el TRA con PKCS#7 (CMS) y devuelve Base64 del CMS (sin contenido adjunto) */
  public static String signCmsDetachedBase64(
      String traXml,
      String p12Path,
      String p12Password,
      String aliasOpt) throws Exception {

    KeyStore ks = KeyStore.getInstance("PKCS12");
    try (var is = new java.io.FileInputStream(p12Path)) {
      ks.load(is, p12Password.toCharArray());
    }

    String alias = aliasOpt;
    if (alias == null || alias.isBlank()) {
      Enumeration<String> e = ks.aliases();
      alias = e.hasMoreElements() ? e.nextElement() : null;
    }
    if (alias == null) throw new Exception("No se encontró alias en el .p12");

    PrivateKey privateKey = (PrivateKey) ks.getKey(alias, p12Password.toCharArray());
    Certificate cert = ks.getCertificate(alias);
    X509Certificate x509 = (X509Certificate) cert;

    byte[] data = traXml.getBytes(StandardCharsets.UTF_8);
    CMSTypedData msg = new CMSProcessableByteArray(data);

    ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(privateKey);

    JcaDigestCalculatorProviderBuilder calcProvBuilder = new JcaDigestCalculatorProviderBuilder().setProvider("BC");
    JcaSignerInfoGeneratorBuilder sigInfoGenBuilder = new JcaSignerInfoGeneratorBuilder(calcProvBuilder.build());

    CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
    gen.addSignerInfoGenerator(sigInfoGenBuilder.build(signer, new X509CertificateHolder(x509.getEncoded())));
    gen.addCertificate(new X509CertificateHolder(x509.getEncoded()));

    // "detached": contenido no incluido
    CMSSignedData signed = gen.generate(msg, false);
    byte[] encoded = signed.getEncoded();

    return Base64.getEncoder().encodeToString(encoded);
  }
}

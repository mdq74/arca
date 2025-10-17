package com.addoc.arca.arca;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPConnection;
import javax.xml.soap.SOAPConnectionFactory;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPMessage;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;

import com.addoc.arca.arca.crypto.WsaaCrypto;

@Service
public class WsaaClient {

    private static final Logger log = LoggerFactory.getLogger(WsaaClient.class);

    @Value("${wsaa.endpoint}")
    private String endpoint;
    @Value("${wsaa.service}")
    private String service;
    @Value("${wsaa.p12Path:}")
    private String p12Path;
    @Value("${wsaa.p12Password:}")
    private String p12Password;
    @Value("${wsaa.p12Alias:}")
    private String p12Alias;
    @Value("${wsaa.renewMinutesBeforeExpiry:10}")
    private long renewBeforeMin;

    private volatile WsaaAuth cached;

    public synchronized WsaaAuth getOrRefreshTA() {
        if (cached == null || cached.isExpiredOrNear(renewBeforeMin)) {
            cached = loginCms();
        }
        return cached;
    }

    /** 🔐 Desactiva validación SSL temporal (solo para homologación) */
    public static void disableSslVerification() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                        }
                    }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 🔹 LoginCms: obtiene Token & Sign del WSAA */
    public WsaaAuth loginCms() {
        try {
            if (p12Path == null || p12Path.isBlank()) {
                log.warn("⚠️ No se configuró certificado P12. Usando modo dummy (solo pruebas WSFEv1.dummy).");
                WsaaAuth dummy = new WsaaAuth();
                dummy.setToken("DUMMY-TOKEN");
                dummy.setSign("DUMMY-SIGN");
                dummy.setExpirationTime(Instant.now().plus(12, ChronoUnit.HOURS));
                return dummy;
            }

            disableSslVerification();

            String tra = WsaaCrypto.buildTRA(service, 12 * 60);
            String cmsB64 = WsaaCrypto.signCmsDetachedBase64(tra, p12Path, p12Password, p12Alias);

            log.info("📤 Enviando LoginCms a AFIP: {}", endpoint);

            SOAPMessage req = buildSoapLoginCms(cmsB64);

            // 🩵 FIX: agregar cabecera SOAPAction
            req.getMimeHeaders().addHeader("SOAPAction", "loginCms");

            SOAPConnection con = SOAPConnectionFactory.newInstance().createConnection();
            SOAPMessage resp = con.call(req, endpoint);
            con.close();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            resp.writeTo(out);
            String xmlResp = out.toString(StandardCharsets.UTF_8);
            log.info("📥 Respuesta WSAA:\n{}", xmlResp);

            SOAPBody body = resp.getSOAPBody();
            if (body == null || body.getFirstChild() == null) {
                throw new RuntimeException("❌ Cuerpo SOAP vacío. AFIP devolvió respuesta vacía o error SOAPAction.");
            }

            String taXml = body.getFirstChild().getTextContent();
            if (taXml == null || taXml.isBlank()) {
                throw new RuntimeException("❌ Texto del TA vacío. WSAA no devolvió XML válido.");
            }

            return parseTA(taXml);

        } catch (Exception e) {
            log.error("❌ WSAA LoginCms error", e);
            throw new RuntimeException("WSAA LoginCms error: " + e.getMessage(), e);
        }
    }

    /** 🧩 Crea el SOAPMessage para loginCms */
    private SOAPMessage buildSoapLoginCms(String cmsB64) throws Exception {
        MessageFactory mf = MessageFactory.newInstance();
        SOAPMessage msg = mf.createMessage();
        SOAPEnvelope env = msg.getSOAPPart().getEnvelope();
        env.addNamespaceDeclaration("ser", "http://wsaa.afip.gov.ar/ws/services/LoginCms");

        SOAPBody body = env.getBody();
        SOAPElement loginCms = body.addChildElement("loginCms", "ser");
        SOAPElement in0 = loginCms.addChildElement("in0");
        in0.addTextNode(cmsB64);

        msg.saveChanges();
        return msg;
    }

    /** 📦 Parseo del TA (Token + Sign + Expiration) */
    private WsaaAuth parseTA(String taXml) throws Exception {
        javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        Document doc = dbf.newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(taXml.getBytes(StandardCharsets.UTF_8)));

        XPath xp = XPathFactory.newInstance().newXPath();
        String token = xp.evaluate("/loginTicketResponse/credentials/token", doc);
        String sign = xp.evaluate("/loginTicketResponse/credentials/sign", doc);
        String exp = xp.evaluate("/loginTicketResponse/header/expirationTime", doc);

        if (token == null || token.isEmpty()) {
            throw new RuntimeException("Token vacío en TA.");
        }

        WsaaAuth out = new WsaaAuth();
        out.setToken(token);
        out.setSign(sign);
        out.setExpirationTime(Instant.parse(exp));
        return out;
    }
}

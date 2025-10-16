package com.addoc.arca.arca;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;

import com.addoc.arca.arca.crypto.WsaaCrypto;

import jakarta.xml.soap.MessageFactory;
import jakarta.xml.soap.SOAPBody;
import jakarta.xml.soap.SOAPConnection;
import jakarta.xml.soap.SOAPConnectionFactory;
import jakarta.xml.soap.SOAPElement;
import jakarta.xml.soap.SOAPEnvelope;
import jakarta.xml.soap.SOAPMessage;

@Service
public class WsaaClient {

    private static final Logger log = LoggerFactory.getLogger(WsaaClient.class);

    @Value("${wsaa.endpoint}") private String endpoint;
    @Value("${wsaa.service}")  private String service;
    @Value("${wsaa.p12Path:}") private String p12Path;
    @Value("${wsaa.p12Password:}") private String p12Password;
    @Value("${wsaa.p12Alias:}") private String p12Alias;
    @Value("${wsaa.renewMinutesBeforeExpiry:10}") private long renewBeforeMin;

    private volatile WsaaAuth cached;

    public synchronized WsaaAuth getOrRefreshTA() {
        if (cached == null || cached.isExpiredOrNear(renewBeforeMin)) {
            cached = loginCms();
        }
        return cached;
    }

    public WsaaAuth loginCms() {
        try {
            // 🧩 Si no hay .p12 configurado → modo dummy
            if (p12Path == null || p12Path.isBlank()) {
                log.warn("⚠️ No se configuró certificado P12. Usando modo dummy (solo pruebas WSFEv1.dummy).");
                WsaaAuth dummy = new WsaaAuth();
                dummy.setToken("DUMMY-TOKEN");
                dummy.setSign("DUMMY-SIGN");
                dummy.setExpirationTime(Instant.now().plus(12, ChronoUnit.HOURS));
                return dummy;
            }

            // 1️⃣ Generar TRA
            String tra = WsaaCrypto.buildTRA(service, 12 * 60);

            // 2️⃣ Firmar CMS y codificar Base64
            String cmsB64 = WsaaCrypto.signCmsDetachedBase64(tra, p12Path, p12Password, p12Alias);

            // 3️⃣ Armar y enviar SOAP request
            SOAPMessage req = buildSoapLoginCms(cmsB64);
            SOAPConnection con = SOAPConnectionFactory.newInstance().createConnection();
            SOAPMessage resp = con.call(req, endpoint);
            con.close();

            // 4️⃣ Parsear TA (Token + Sign + Expiration)
            String taXml = resp.getSOAPBody().getFirstChild().getTextContent();
            return parseTA(taXml);

        } catch (Exception e) {
            log.error("❌ WSAA LoginCms error", e);
            throw new RuntimeException("WSAA LoginCms error: " + e.getMessage(), e);
        }
    }

    private SOAPMessage buildSoapLoginCms(String cmsB64) throws Exception {
        MessageFactory mf = MessageFactory.newInstance();
        SOAPMessage msg = mf.createMessage();
        SOAPEnvelope env = msg.getSOAPPart().getEnvelope();
        env.addNamespaceDeclaration("ser", "https://wsaa.afip.gov.ar/ws/services/LoginCms");

        SOAPBody body = env.getBody();
        SOAPElement loginCms = body.addChildElement("loginCms", "ser");
        SOAPElement in0 = loginCms.addChildElement("in0");
        in0.addTextNode(cmsB64);

        msg.saveChanges();
        return msg;
    }

    private WsaaAuth parseTA(String taXml) throws Exception {
        javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        Document doc = dbf.newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(taXml.getBytes(StandardCharsets.UTF_8)));

        XPath xp = XPathFactory.newInstance().newXPath();
        String token = xp.evaluate("/loginTicketResponse/credentials/token", doc);
        String sign  = xp.evaluate("/loginTicketResponse/credentials/sign", doc);
        String exp   = xp.evaluate("/loginTicketResponse/header/expirationTime", doc);

        WsaaAuth out = new WsaaAuth();
        out.setToken(token);
        out.setSign(sign);
        out.setExpirationTime(Instant.parse(exp));
        return out;
    }
}

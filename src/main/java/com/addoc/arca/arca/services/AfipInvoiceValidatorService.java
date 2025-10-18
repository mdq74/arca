package com.addoc.arca.arca.services;

import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

import javax.xml.namespace.QName;
import javax.xml.ws.Service;

import org.springframework.web.util.UriComponentsBuilder;

import com.addoc.arca.arca.WsaaAuth;
import com.addoc.arca.arca.WsaaClient;
import com.addoc.arca.arca.dto.InvoiceValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@org.springframework.stereotype.Service
public class AfipInvoiceValidatorService {

    private final WsaaClient wsaaClient;

    private static final String WSDL_URL = "https://wsw.afip.gov.ar/wsfev1/service.asmx?WSDL";
    private static final String NAMESPACE_URI = "http://ar.gov.afip.dif.FEV1/";
    private static final String SERVICE_NAME = "Service";
    private static final String AFIP_QR_BASE_URL = "https://www.afip.gob.ar/fe/qr/";

    public AfipInvoiceValidatorService(WsaaClient wsaaClient) {
        this.wsaaClient = wsaaClient;
    }

    /**
     * 🔹 Valida un comprobante por CAE ante AFIP (WSFEv1)
     */
    public InvoiceValidationResult validateInvoice(String cuitEmisor, int tipo, int ptoVta, int nroCbte) {
        InvoiceValidationResult result = new InvoiceValidationResult();
        result.setEmisorCuit(cuitEmisor);
        result.setTipoComprobante(tipo);
        result.setPuntoVenta(ptoVta);
        result.setNumeroComprobante(nroCbte);

        try {
            WsaaAuth ta = wsaaClient.getOrRefreshTA();
            URL url = new URL(WSDL_URL);
            QName qname = new QName(NAMESPACE_URI, SERVICE_NAME);
            Service service = Service.create(url, qname);
            ar.gov.afip.dif.FEV1.ServiceSoap port = service.getPort(ar.gov.afip.dif.FEV1.ServiceSoap.class);

            ar.gov.afip.dif.FEV1.FEAuthRequest auth = new ar.gov.afip.dif.FEV1.FEAuthRequest();
            auth.setToken(ta.getToken());
            auth.setSign(ta.getSign());
            auth.setCuit(Long.parseLong(cuitEmisor));

            ar.gov.afip.dif.FEV1.FECompConsultaReq req = new ar.gov.afip.dif.FEV1.FECompConsultaReq();
            req.setCbteTipo(tipo);
            req.setPtoVta(ptoVta);
            req.setCbteNro(nroCbte);

            ar.gov.afip.dif.FEV1.FECompConsultaResponse resp = port.feCompConsultar(auth, req);

            if (resp != null && resp.getResultGet() != null) {
                var data = resp.getResultGet();
                result.setCae(data.getCodAutorizacion());
                result.setCaeExpiration(LocalDate.parse(data.getFchVto(), DateTimeFormatter.BASIC_ISO_DATE));

                boolean vigente = result.getCae() != null &&
                        LocalDate.now().isBefore(result.getCaeExpiration().plusDays(1));

                result.setValid(vigente);
                result.setMessage(vigente ? "Comprobante vigente" : "CAE vencido");
            } else {
                result.setValid(false);
                result.setMessage("Comprobante no encontrado o no autorizado en AFIP");
            }

        } catch (Exception e) {
            result.setValid(false);
            result.setMessage("Error en validación: " + e.getMessage());
        }

        return result;
    }

        /**
     * 🔹 Valida un CAE contra AFIP WSFEv1 (requiere tipo, punto de venta y número)
     * Usa WSAA para autenticarse y compara el CAE informado con el autorizado por AFIP.
     */
    public InvoiceValidationResult validateCaeOnline(String cuitEmisor, int tipo, int ptoVta, int nroCbte, String caeInformado) {
        InvoiceValidationResult result = new InvoiceValidationResult();
        result.setEmisorCuit(cuitEmisor);
        result.setTipoComprobante(tipo);
        result.setPuntoVenta(ptoVta);
        result.setNumeroComprobante(nroCbte);
        result.setCae(caeInformado);

        try {
            // 🔐 Obtener TA (token/sign) mediante WSAA
            WsaaAuth ta = wsaaClient.getOrRefreshTA();

            // 🧩 Instanciar cliente SOAP WSFEv1
            URL url = new URL(WSDL_URL);
            QName qname = new QName(NAMESPACE_URI, SERVICE_NAME);
            Service service = Service.create(url, qname);
            ar.gov.afip.dif.FEV1.ServiceSoap port = service.getPort(ar.gov.afip.dif.FEV1.ServiceSoap.class);

            // 📄 Autenticación
            ar.gov.afip.dif.FEV1.FEAuthRequest auth = new ar.gov.afip.dif.FEV1.FEAuthRequest();
            auth.setToken(ta.getToken());
            auth.setSign(ta.getSign());
            auth.setCuit(Long.parseLong(cuitEmisor));

            // 🔍 Consulta del comprobante
            ar.gov.afip.dif.FEV1.FECompConsultaReq req = new ar.gov.afip.dif.FEV1.FECompConsultaReq();
            req.setCbteTipo(tipo);
            req.setPtoVta(ptoVta);
            req.setCbteNro(nroCbte);

            ar.gov.afip.dif.FEV1.FECompConsultaResponse resp = port.feCompConsultar(auth, req);

            if (resp != null && resp.getResultGet() != null) {
                var data = resp.getResultGet();
                String caeAfip = data.getCodAutorizacion();
                LocalDate vto = LocalDate.parse(data.getFchVto(), DateTimeFormatter.BASIC_ISO_DATE);

                result.setCaeExpiration(vto);
                result.setValid(caeAfip != null && caeAfip.equals(caeInformado));

                if (result.isValid()) {
                    boolean vigente = LocalDate.now().isBefore(vto.plusDays(1));
                    result.setValid(vigente);
                    result.setMessage(vigente
                            ? "✅ CAE válido y vigente según AFIP"
                            : "⚠️ CAE válido pero vencido (" + vto + ")");
                } else {
                    result.setValid(false);
                    result.setMessage("❌ CAE no coincide con el comprobante autorizado por AFIP");
                }
            } else {
                result.setValid(false);
                result.setMessage("❌ Comprobante no encontrado o no autorizado en AFIP");
            }

        } catch (Exception e) {
            result.setValid(false);
            result.setMessage("Error al validar CAE con AFIP: " + e.getMessage());
        }

        return result;
    }


    /**
     * 🧩 Decodifica localmente el QR de AFIP sin llamar al sitio web.
     * Ideal para validaciones internas o ambientes sin Internet.
     */
    public JsonNode validateQrOffline(String qrInput) throws Exception {
        String paramP = extractParam(qrInput);

        try {
            // Decodificar Base64URL a JSON
            byte[] decodedBytes = Base64.getUrlDecoder().decode(paramP);
            String json = new String(decodedBytes, java.nio.charset.StandardCharsets.UTF_8);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(json);

            // Validaciones básicas
            if (!node.has("cuit") || !node.has("codAut")) {
                throw new IllegalArgumentException("El QR no contiene datos válidos de AFIP");
            }

            return node;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Formato de QR inválido o Base64 corrupto");
        }
    }

    


    /**
     * 🔍 Valida los datos públicos de un comprobante AFIP desde un QR.
     * No requiere WSAA ni certificado.
     */
    public JsonNode validateQr(String qrInput) throws Exception {
        String paramP = extractParam(qrInput);

        String url = UriComponentsBuilder
                .fromHttpUrl(AFIP_QR_BASE_URL)
                .queryParam("p", paramP)
                .toUriString();

        // Creamos un RestTemplate con headers tipo navegador
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
        headers.add("Accept", "application/json, text/plain, */*");
        headers.add("Referer", "https://www.afip.gob.ar");

        org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);
        org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();

        org.springframework.http.ResponseEntity<String> response = restTemplate.exchange(url,
                org.springframework.http.HttpMethod.GET, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            String body = response.getBody();

            // Si AFIP respondió HTML, abortamos
            if (body.trim().startsWith("<")) {
                throw new IllegalStateException("AFIP devolvió HTML (acceso denegado o QR inválido)");
            }

            ObjectMapper mapper = new ObjectMapper();
            return mapper.readTree(body);
        } else {
            throw new IllegalStateException("Error HTTP de AFIP: " + response.getStatusCode());
        }
    }

    private String extractParam(String input) {
        if (input == null)
            throw new IllegalArgumentException("El valor del QR no puede ser nulo");
        if (input.contains("?p="))
            return input.substring(input.indexOf("?p=") + 3);
        return input.trim();
    }
}

package com.addoc.arca.arca.services;

import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import javax.xml.namespace.QName;
import javax.xml.ws.Service;

import com.addoc.arca.arca.WsaaAuth;
import com.addoc.arca.arca.WsaaClient;
import com.addoc.arca.arca.dto.InvoiceValidationResult;

@org.springframework.stereotype.Service
public class AfipInvoiceValidatorService {

    private final WsaaClient wsaaClient;

    private static final String WSDL_URL = "https://wswhomo.afip.gov.ar/wsfev1/service.asmx?WSDL";
    private static final String NAMESPACE_URI = "http://ar.gov.afip.dif.FEV1/";
    private static final String SERVICE_NAME = "Service";

    public AfipInvoiceValidatorService(WsaaClient wsaaClient) {
        this.wsaaClient = wsaaClient;
    }

    /**
     * 🔹 Valida un comprobante por CAE ante AFIP (WSFEv1)
     * Usa exclusivamente el binding JAX-WS estándar generado con wsimport.
     */
    public InvoiceValidationResult validateInvoice(String cuitEmisor, int tipo, int ptoVta, int nroCbte) {
        InvoiceValidationResult result = new InvoiceValidationResult();
        result.setEmisorCuit(cuitEmisor);
        result.setTipoComprobante(tipo);
        result.setPuntoVenta(ptoVta);
        result.setNumeroComprobante(nroCbte);

        try {
            // 1️⃣ Obtener Token + Sign automáticamente (WSAA)
            WsaaAuth ta = wsaaClient.getOrRefreshTA();

            // 2️⃣ Crear cliente SOAP WSFEv1
            URL url = new URL(WSDL_URL);
            QName qname = new QName(NAMESPACE_URI, SERVICE_NAME);
            Service service = Service.create(url, qname);
            ar.gov.afip.dif.FEV1.ServiceSoap port = service.getPort(ar.gov.afip.dif.FEV1.ServiceSoap.class);

            // 3️⃣ Armar autenticación
            ar.gov.afip.dif.FEV1.FEAuthRequest auth = new ar.gov.afip.dif.FEV1.FEAuthRequest();
            auth.setToken(ta.getToken());
            auth.setSign(ta.getSign());
            auth.setCuit(Long.parseLong(cuitEmisor));

            // 4️⃣ Armar la request
            ar.gov.afip.dif.FEV1.FECompConsultaReq req = new ar.gov.afip.dif.FEV1.FECompConsultaReq();
            req.setCbteTipo(tipo);
            req.setPtoVta(ptoVta);
            req.setCbteNro(nroCbte);

            // 5️⃣ Invocar FECompConsultar
            ar.gov.afip.dif.FEV1.FECompConsultaResponse resp = port.feCompConsultar(auth, req);

            // 6️⃣ Procesar respuesta
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
}

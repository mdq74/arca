package com.addoc.arca.arca.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.addoc.arca.arca.dto.InvoiceValidationResult;
import com.addoc.arca.arca.services.AfipInvoiceValidatorService;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

@RestController
@RequestMapping("/api/afip")
public class AfipController {

    private final AfipInvoiceValidatorService validatorService;

    public AfipController(AfipInvoiceValidatorService validatorService) {
        this.validatorService = validatorService;
    }

    /**
     * 🔹 Valida un comprobante ante AFIP WSFEv1 usando WSAA.
     */
    @GetMapping("/validateInvoice")
    public ResponseEntity<InvoiceValidationResult> validateInvoice(
            @RequestParam String cuit,
            @RequestParam int tipo,
            @RequestParam int ptoVta,
            @RequestParam int nro) {

        try {
            InvoiceValidationResult result = validatorService.validateInvoice(cuit, tipo, ptoVta, nro);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            InvoiceValidationResult error = new InvoiceValidationResult();
            error.setEmisorCuit(cuit);
            error.setValid(false);
            error.setMessage("Error en validación: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 🔹 Valida un comprobante a partir de su QR público.
     * Ejemplo:
     * GET
     * /api/afip/validate-qr?qr=https://www.afip.gob.ar/fe/qr/?p=eyJ2ZXIiOjEsIm...
     */
    @GetMapping("/validate-qr")
    public ResponseEntity<?> validateQr(@RequestParam String qr) {
        try {
            JsonNode data = validatorService.validateQr(qr);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/validate-cae-online")
    public ResponseEntity<InvoiceValidationResult> validateCaeOnline(
            @RequestParam String cuit,
            @RequestParam int tipo,
            @RequestParam int ptoVta,
            @RequestParam int nro,
            @RequestParam String cae) {

        InvoiceValidationResult result = validatorService.validateCaeOnline(cuit, tipo, ptoVta, nro, cae);
        return ResponseEntity.ok(result);
    }

    /**
     * 🔹 Decodifica localmente un QR de AFIP sin conexión.
     * Ejemplo:
     * GET /api/afip/decode-qr?qr=https://www.afip.gob.ar/fe/qr/?p=...
     */
    @GetMapping("/decode-qr")
    public ResponseEntity<?> decodeQr(@RequestParam String qr) {
        try {
            JsonNode data = validatorService.validateQrOffline(qr);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 🔹 Endpoint dummy para probar conectividad WSFEv1.
     * Devuelve el estado de los tres servidores de AFIP.
     */
    @GetMapping("/dummy")
    public ResponseEntity<Map<String, Object>> dummy() {
        try {
            String result = validatorService.callFedummy();
            boolean ok = result.contains("OK");
            return ResponseEntity.status(ok ? HttpStatus.OK : HttpStatus.BAD_GATEWAY)
                    .body(Map.of(
                            "status", ok ? "OK" : "FAIL",
                            "message", result));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

}

package com.addoc.arca.arca.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.addoc.arca.arca.dto.InvoiceValidationResult;
import com.addoc.arca.arca.services.AfipInvoiceValidatorService;

@RestController
@RequestMapping("/api/afip")
public class AfipController {

    private final AfipInvoiceValidatorService validatorService;

    public AfipController(AfipInvoiceValidatorService validatorService) {
        this.validatorService = validatorService;
    }

    /**
     * 🔹 Valida un comprobante ante AFIP WSFEv1 usando WSAA para autenticación automática.
     * Ejemplo: GET /api/afip/validate?cuit=20123456789&tipo=1&ptoVta=1&nro=1234
     */
    @GetMapping("/validate")
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
     * 🔹 Endpoint simple para probar conectividad con AFIP WSFEv1 (Dummy)
     * GET /api/afip/dummy
     */
    @GetMapping("/dummy")
    public ResponseEntity<String> dummy() {
        try {
            // ✅ Usa la llamada raw, que no depende de JAXB
            InvoiceValidationResult result = validatorService.validateInvoice("20123456789", 1, 1, 1234);
            return ResponseEntity.ok("✅ Dummy AFIP WSFEv1: " + result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("❌ Error dummy AFIP: " + e.getMessage());
        }
    }
}

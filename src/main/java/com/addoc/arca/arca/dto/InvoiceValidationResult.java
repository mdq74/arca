package com.addoc.arca.arca.dto;

import java.time.LocalDate;

public class InvoiceValidationResult {
    private boolean valid;
    private String cae;
    private LocalDate caeExpiration;
    private String message;
    private String emisorCuit;
    private int tipoComprobante;
    private int puntoVenta;
    private int numeroComprobante;

    // Getters y setters
    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getCae() {
        return cae;
    }

    public void setCae(String cae) {
        this.cae = cae;
    }

    public LocalDate getCaeExpiration() {
        return caeExpiration;
    }

    public void setCaeExpiration(LocalDate caeExpiration) {
        this.caeExpiration = caeExpiration;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getEmisorCuit() {
        return emisorCuit;
    }

    public void setEmisorCuit(String emisorCuit) {
        this.emisorCuit = emisorCuit;
    }

    public int getTipoComprobante() {
        return tipoComprobante;
    }

    public void setTipoComprobante(int tipoComprobante) {
        this.tipoComprobante = tipoComprobante;
    }

    public int getPuntoVenta() {
        return puntoVenta;
    }

    public void setPuntoVenta(int puntoVenta) {
        this.puntoVenta = puntoVenta;
    }

    public int getNumeroComprobante() {
        return numeroComprobante;
    }

    public void setNumeroComprobante(int numeroComprobante) {
        this.numeroComprobante = numeroComprobante;
    }
}

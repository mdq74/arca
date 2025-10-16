package com.addoc.arca.arca;

import java.time.Instant;

public class WsaaAuth {
  private String token;
  private String sign;
  private Instant expirationTime;

  public String getToken() { return token; }
  public String getSign() { return sign; }
  public Instant getExpirationTime() { return expirationTime; }

  public void setToken(String token) { this.token = token; }
  public void setSign(String sign) { this.sign = sign; }
  public void setExpirationTime(Instant expirationTime) { this.expirationTime = expirationTime; }

  public boolean isExpiredOrNear(long renewBeforeMinutes) {
    return expirationTime == null ||
           Instant.now().plusSeconds(renewBeforeMinutes * 60).isAfter(expirationTime);
  }
}

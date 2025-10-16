package com.addoc.arca.arca.controllers;

import org.springframework.web.bind.annotation.*;

import com.addoc.arca.arca.WsaaAuth;
import com.addoc.arca.arca.WsaaClient;

@RestController
@RequestMapping("/api/wsaa")
public class WsaaController {
  private final WsaaClient wsaa;
  public WsaaController(WsaaClient wsaa) { this.wsaa = wsaa; }

  /** Devuelve el TA cacheado (renueva si está por expirar) */
  @GetMapping("/ta")
  public WsaaAuth getTA() { return wsaa.getOrRefreshTA(); }

  /** Fuerza loginCms inmediato (ignora cache) */
  @PostMapping("/refresh")
  public WsaaAuth refresh() { return wsaa.loginCms(); }
}

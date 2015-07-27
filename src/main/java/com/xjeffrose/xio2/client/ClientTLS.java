package com.xjeffrose.xio2.client;

import com.xjeffrose.log.Log;
import com.xjeffrose.xio2.TLS.KeyStoreGenerator;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.logging.Logger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class ClientTLS {
  private static final Logger log = Log.getLogger(ClientTLS.class.getName());

  private SSLContext sslCtx;
  private SSLEngineResult sslResult;

  private ByteBuffer rawRequest;
  private ByteBuffer encryptedRequest;

  private ByteBuffer encryptedResponse;
  private ByteBuffer rawResponse;

  private char[] passphrase = "changeit".toCharArray();
  private SSLEngineResult.HandshakeStatus handshakeStatus;
  private SocketChannel channel;

  public boolean client;
  public SSLEngine engine;

  public ClientTLS(
      SocketChannel channel,
      ByteBuffer rawRequest,
      ByteBuffer encryptedRequest,
      ByteBuffer encryptedResponse,
      ByteBuffer rawResponse) {
    this.channel = channel;
    this.rawRequest = rawRequest;
    this.encryptedRequest = encryptedRequest;
    this.encryptedResponse = encryptedResponse;
    this.rawResponse = rawResponse;
    this.client = true;
    genEngine();
    try {
      engine.beginHandshake();
    } catch (SSLException e) {
      e.printStackTrace();
    }
  }

  private TrustManager[] getTrustAllCerts() {
    TrustManager[] trustAllCerts = new TrustManager[]{
        new X509TrustManager() {
          public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return null;
          }

          public void checkClientTrusted(
              java.security.cert.X509Certificate[] certs, String authType) {
          }

          public void checkServerTrusted(
              java.security.cert.X509Certificate[] certs, String authType) {
          }
        }
    };
    return trustAllCerts;
  }

  private void genEngine() {
    try {
      KeyStore ks = KeyStoreGenerator.Build();
      KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
      kmf.init(ks, passphrase);

      sslCtx = SSLContext.getInstance("TLSv1.2");
      sslCtx.init(kmf.getKeyManagers(), getTrustAllCerts(), new SecureRandom());

      SSLParameters params = new SSLParameters();
      params.setProtocols(new String[]{"TLSv1.2"});

      engine = sslCtx.createSSLEngine("localhost", 4433);
      engine.setSSLParameters(params);
      engine.setUseClientMode(true);

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void handleSSLResult(boolean network) {

    switch (sslResult.getStatus()) {
      case OK:
        log.info("OKKKKKK");
        break;
//        ctx.handshakeOK = true;
      case BUFFER_UNDERFLOW:
        read();
        break;
      case BUFFER_OVERFLOW:
        if (network) {
          encryptedRequest.flip();
          write();
          encryptedRequest.compact();
        } else {
          rawResponse.flip();
          rawResponse.compact();
        }
        break;
      case CLOSED:
        System.out.print("Closed");
        break;
    }
  }

  private void read() {
    int nread = 1;
    while (nread > 0) {
      try {
        nread = channel.read(encryptedResponse);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      if (nread == -1) {
        try {
          log.severe("Fool tried to close the channel, yo");
          //ctx.channel.close();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
  }

  private void write() {
    try {
      channel.write(encryptedRequest);
      handleSSLResult(true);
    } catch (Exception e) {
      log.severe("Pooo face" + e);
    }
  }

  public boolean execute() {
    try {
      engine.beginHandshake();
    } catch (SSLException e) {
      e.printStackTrace();
    }
    while (true) {
      handshakeStatus = engine.getHandshakeStatus();
      switch (handshakeStatus) {
        case NEED_TASK:
          log.info("need_task");

          Runnable task;
          while ((task = engine.getDelegatedTask()) != null) {
            task.run();
          }
          break;

        case NEED_UNWRAP:
          log.info("need_unwrap");
          read();
          encryptedResponse.flip();
          unwrap();
          encryptedResponse.compact();
          break;

        case NEED_WRAP:
          log.info("need_wrap");
          wrap();
          encryptedRequest.flip();
          write();
          encryptedRequest.compact();
          break;

        case FINISHED:
          log.info("Successful TLS Handshake");
//          ctx.handshakeOK = true;
          return true;
        case NOT_HANDSHAKING:
          log.info("Not handshaking (whatever that means)");
          return true;
        default:
          log.info("got rando status " + handshakeStatus);
          break;
      }
    }
  }

  public void unwrap() {
    try {
      sslResult = engine.unwrap(encryptedResponse, rawResponse);
      log.info(sslResult.toString());
      handleSSLResult(false);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void wrap() {
    try {
      sslResult = engine.wrap(rawRequest, encryptedRequest);
      log.info(sslResult.toString());
      handleSSLResult(true);
    } catch (SSLException e) {
      e.printStackTrace();
    }
  }
}

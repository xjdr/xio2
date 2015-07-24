package com.xjeffrose.xio2.TLS;

import com.xjeffrose.log.Log;
import com.xjeffrose.xio2.http.Http;
import com.xjeffrose.xio2.http.HttpResponse;
import com.xjeffrose.xio2.server.ChannelContext;
import com.xjeffrose.xio2.util.BB;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.logging.Logger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;

public class TLS {
  private static final Logger log = Log.getLogger(TLS.class.getName());

  private SSLContext sslCtx;
  private SSLEngine engine;
  private ChannelContext ctx;
  private SSLEngineResult sslResult;

  private ByteBuffer encryptedRequest;
  private ByteBuffer decryptedRequest;
  private ByteBuffer rawResponse;
  private ByteBuffer encryptedResponse;

  private char[] passphrase = "changeit".toCharArray();

  private SSLEngineResult.HandshakeStatus handshakeStatus;

  public TLS(ChannelContext ctx) {
    this.ctx = ctx;
    genEngine();
  }

  private void genEngine() {
    try {
      KeyStore ks = KeyStoreGenerator.Build();
      KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
      kmf.init(ks, passphrase);

      sslCtx = SSLContext.getInstance("TLSv1.2");
      sslCtx.init(kmf.getKeyManagers(), null, new SecureRandom());

      SSLParameters params = new SSLParameters();
      params.setProtocols(new String[]{"TLSv1.2"});

      engine = sslCtx.createSSLEngine();

      engine.setSSLParameters(params);

      engine.setNeedClientAuth(false);
      engine.setUseClientMode(false);

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void handleSSLResult(SSLEngineResult result, boolean network) {

    log.info("handleSSLResult " + result);
    switch (sslResult.getStatus()) {
      case OK:
        break;
      case BUFFER_UNDERFLOW:
        read();
        break;
      case BUFFER_OVERFLOW:
        if (network) {
          rawResponse.flip();
          write();
          rawResponse.compact();
        } else {
          decryptedRequest.flip();
          log.info(BB.BBtoString(decryptedRequest));
          decryptedRequest.compact();
        }
      case CLOSED:
        System.out.print("Closed");
        break;
      default:
        break;
    }
  }


  public void read() {
    int nread = 1;
    int total_read = 0;
    while (nread > 0) {
      try {
        nread = ctx.channel.read(encryptedRequest);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      if (nread == -1) {
        try {
//                ctx.channel.close();
        } catch (Exception e) {
          e.printStackTrace();
        }
      } else {
        total_read += nread;
      }
    }
    log.info("read: total read " + total_read);
  }

  public void write() {
    try {
      ctx.channel.write(encryptedResponse);
      handleSSLResult(sslResult, true);
    } catch (Exception e) {
      log.severe("POoo face" + e);
    }
  }

  public SSLEngine doHandshake() {
    encryptedRequest = ByteBuffer.allocateDirect(engine.getSession().getPacketBufferSize());
    decryptedRequest = ByteBuffer.allocateDirect(engine.getSession().getApplicationBufferSize());
    rawResponse = ByteBuffer.allocateDirect(engine.getSession().getApplicationBufferSize());
    encryptedResponse = ByteBuffer.allocateDirect(engine.getSession().getPacketBufferSize());

    try {
      engine.beginHandshake();
      handshakeStatus = engine.getHandshakeStatus();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    while (handshakeStatus != SSLEngineResult.HandshakeStatus.FINISHED &&
        handshakeStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
      handshakeStatus = engine.getHandshakeStatus();
      log.info("doHandshake " + handshakeStatus);
      switch (handshakeStatus) {

        case NEED_TASK:
          Runnable task;
          while ((task = engine.getDelegatedTask()) != null) {
            task.run();
          }
          handshakeStatus = engine.getHandshakeStatus();
          break;

        case NEED_UNWRAP:
          read();
          encryptedRequest.flip();
          unwrap();
          encryptedRequest.compact();
          handshakeStatus = engine.getHandshakeStatus();
          break;

        case NEED_WRAP:
          wrap();
          encryptedResponse.flip();
          write();
          encryptedResponse.compact();
          handshakeStatus = engine.getHandshakeStatus();
          break;
        case FINISHED:
          log.info(sslResult.toString());
          log.info(handshakeStatus.toString());
          log.info("FINISHED!!!!!");
          rawResponse = HttpResponse.DefaultResponse(Http.Version.HTTP1_1, Http.Status.OK).toBB();
          return engine;
      }
    }
    return engine;
  }


  public void readMoreCauseUnderflow() {
    int nread = 1;
    while (nread > 0) {
      try {
        nread = ctx.channel.read(encryptedRequest);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      if (nread == -1) {
        try {
          log.severe("FOol tried to close teh channel, yo");
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
  }

  public void unwrap() {
    try {
      sslResult = engine.unwrap(encryptedRequest, decryptedRequest);
      handleSSLResult(sslResult, false);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void wrap() {
    try {
      sslResult = engine.wrap(rawResponse, encryptedResponse);
      handleSSLResult(sslResult, true);
    } catch (SSLException e) {
      e.printStackTrace();
    }
  }
}
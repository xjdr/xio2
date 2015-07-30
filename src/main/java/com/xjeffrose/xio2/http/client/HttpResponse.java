package com.xjeffrose.xio2.http.client;


import com.xjeffrose.xio2.http.Http;
import com.xjeffrose.xio2.http.HttpObject;
import com.xjeffrose.xio2.util.BB;
import java.nio.ByteBuffer;

public class HttpResponse extends HttpObject {

  public HttpResponse() { }

  public static HttpResponse DefaultResponse(Http.Version version, Http.Status status) {
    final HttpResponse resp = new HttpResponse();

    resp.setVersion(version);
    resp.setStatus(status);
    resp.headers.set("Content-Type", "text/html; charset=UTF-8");
    resp.headers.set("Date", Http.date());
    resp.headers.set("Server", "xio2");

    return resp;
  }

  public static HttpResponse DefaultResponse(
      Http.Version version,
      Http.Status status,
      String body) {
    final HttpResponse resp = new HttpResponse();

    resp.setVersion(version);
    resp.setStatus(status);
    resp.headers.set("Content-Type", "text/html; charset=UTF-8");
    resp.headers.set("Date", Http.date());
    resp.headers.set("Server", "xio2");
    resp.headers.set("Content-Length", Integer.toString(body.length()));
    resp.body.set(body);

    return resp;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append(getHttpVersion() + " ");
    sb.append(getStatus() + "\r\n");
    sb.append("Content-Type" + ": " + headers.get("Content-Type") + "\r\n");
    sb.append("Date" + ": " + headers.get("Date") + "\r\n");
    sb.append("Server" + ": " + headers.get("Server") + "\r\n");
    if (headers.headerMap.containsKey("Content-Length")) {
      if (headers.get("Content-Length").equals("0")) {
        sb.append("Content-Length: 0\r\n");
        sb.append("\r\n");
      } else {
        sb.append("Content-Length" + ": " + headers.get("Content-Length") + "\r\n");
        sb.append("\r\n");
        sb.append(getBody());
      }
    }
    return sb.toString();
  }

  public ByteBuffer toBB() {
    return BB.StringtoBB(toString());
  }
}

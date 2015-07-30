package com.xjeffrose.xio2.http.client.LoadBalancerStrategies;

import java.net.InetSocketAddress;

public class NullLoadBalancer implements LoadBalancingStrategy {
  private final InetSocketAddress addr;

  public NullLoadBalancer(InetSocketAddress addr) {
    this.addr = addr;
  }

  @Override
  public InetSocketAddress nextAddress() {
    return addr;
  }
}

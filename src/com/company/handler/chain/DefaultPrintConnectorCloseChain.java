package com.company.handler.chain;

import com.company.clink.core.Connector;

/**
 * 关闭链接链式结构
 */
public class DefaultPrintConnectorCloseChain extends ConnectorCloseChain {

    @Override
    protected boolean consume(ClientHandler handler, Connector connector) {
        System.out.println(handler.getClientInfo() + ":Exit!!, Key:" + handler.getKey().toString());
        return false;
    }
}

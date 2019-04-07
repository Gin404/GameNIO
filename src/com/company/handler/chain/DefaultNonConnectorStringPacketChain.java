package com.company.handler.chain;


import com.company.clink.box.StringReceivePacket;

/**
 * 默认String接收节点，不做任何事情
 */
public class DefaultNonConnectorStringPacketChain extends ConnectorStringPacketChain {
    @Override
    protected boolean consume(ClientHandler handler, StringReceivePacket packet) {
        return false;
    }
}

package com.company.handler.chain;

import com.company.Utils.CloseUtils;
import com.company.clink.box.StringReceivePacket;
import com.company.clink.core.Connector;
import com.company.clink.core.ReceivePacket;

import java.io.*;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;


public class ClientHandler extends Connector{
    private final String clientInfo;
    private final Executor deliveryPool;
    private final ConnectorCloseChain closeChain = new DefaultPrintConnectorCloseChain();
    private final ConnectorStringPacketChain stringPacketChain = new DefaultNonConnectorStringPacketChain();

    public ClientHandler(SocketChannel socketChannel, Executor deliveryPool) throws IOException {
        this.deliveryPool = deliveryPool;
        this.clientInfo = socketChannel.getRemoteAddress().toString();

        System.out.println("新客户端连接：" + clientInfo);
        setup(socketChannel);
    }

    public String getClientInfo(){
        return this.clientInfo;
    }

    public void exit() {
        CloseUtils.close(this);
        closeChain.handle(this, this);
    }

    @Override
    public void onChannelClosed(SocketChannel channel) {
        super.onChannelClosed(channel);
        closeChain.handle(this, this);
    }

    @Override
    public void onReceiveNewMessage(StringReceivePacket packet) {
        super.onReceiveNewMessage(packet);
        deliveryStringPacket(packet);
    }

    private void deliveryStringPacket(StringReceivePacket packet) {
        deliveryPool.execute(() -> {
            stringPacketChain.handle(this, packet);
        });
    }

    /**
     * 获取当前链接的消息处理责任链 链头
     *
     * @return ConnectorStringPacketChain
     */
    public ConnectorStringPacketChain getStringPacketChain() {
        return stringPacketChain;
    }

    /**
     * 获取当前链接的关闭链接处理责任链 链头
     *
     * @return ConnectorCloseChain
     */
    public ConnectorCloseChain getCloseChain() {
        return closeChain;
    }

}

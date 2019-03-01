package com.company.Client;

import com.company.Constants.TCPConstants;
import com.company.Utils.CloseUtils;
import com.company.clink.box.StringReceivePacket;
import com.company.clink.core.Connector;
import com.company.clink.core.ReceivePacket;

import java.io.*;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

public class TCPClient extends Connector {

    public TCPClient(SocketChannel socketChannel) throws IOException {
        setup(socketChannel);
    }

    public void exit(){
        CloseUtils.close(this);
    }

    @Override
    public void onChannelClosed(SocketChannel channel) {
        super.onChannelClosed(channel);
        System.out.println("连接已经关闭，无法读取数据！");
    }

    @Override
    public void onReceiveNewMessage(StringReceivePacket packet){
        super.onReceiveNewMessage(packet);
    }


    public static TCPClient start() throws IOException {
        SocketChannel socketChannel = SocketChannel.open();
        // 连接本地，端口2000；超时时间3000ms
        socketChannel.connect(new InetSocketAddress(Inet4Address.getByName(TCPConstants.IP), TCPConstants.PORT_SERVER));

        System.out.println("已发起服务器连接，并进入后续流程～");
        System.out.println("客户端信息：" + socketChannel.getLocalAddress().toString());
        System.out.println("服务器信息：" + socketChannel.getRemoteAddress().toString());

        try {
            return new TCPClient(socketChannel);
        }catch (Exception e){
            System.out.println("连接异常");
            CloseUtils.close(socketChannel);
        }

        return null;
    }
}

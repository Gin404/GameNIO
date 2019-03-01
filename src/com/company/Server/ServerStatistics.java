package com.company.Server;


import com.company.clink.box.StringReceivePacket;
import com.company.handler.chain.ClientHandler;
import com.company.handler.chain.ConnectorStringPacketChain;

class ServerStatistics {
    long receiveSize;
    long sendSize;

    ConnectorStringPacketChain statisticsChain() {
        return new StatisticsConnectorStringPacketChain();
    }

    /**
     * 接收数据的责任链节点，添加到首节点之后，则可以在每次收到消息时得到回调
     * 然后可以进行接收消息的统计
     */
    class StatisticsConnectorStringPacketChain extends ConnectorStringPacketChain {

        @Override
        protected boolean consume(ClientHandler handler, StringReceivePacket packet) {
            // 接收数据量自增
            receiveSize++;
            return false;
        }

    }
}

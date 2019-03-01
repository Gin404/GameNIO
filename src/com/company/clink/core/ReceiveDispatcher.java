package com.company.clink.core;

import com.company.clink.box.StringReceivePacket;

import java.io.Closeable;

/**
 * 接收的数据的调度封装
 * 把一份IoArgs或多份IoArgs组合成一份Packet
 */
public interface ReceiveDispatcher extends Closeable {
    void start();
    void stop();

    /**
     * 收到数据通知到外层
     */
    interface ReceivePacketCallback{
        /**
         * 接收数据完成便回调
         * @param packet
         */
        void onReceivePacketCompleted(StringReceivePacket packet);
    }
}

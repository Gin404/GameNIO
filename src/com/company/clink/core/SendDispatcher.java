package com.company.clink.core;

import java.io.Closeable;

/**
 * 发送调度者
 * 缓存所有需要发送的数据， 通过队列对数据进行发送
 * 并且在发送时，实现对数据的基本包装
 */
public interface SendDispatcher extends Closeable {
    /**
     * 发送一份数据
     * @param sendPacket
     */
    void send(SendPacket sendPacket);

    /**
     * 取消发送数据
     * @param sendPacket
     */
    void cancel(SendPacket sendPacket);
}

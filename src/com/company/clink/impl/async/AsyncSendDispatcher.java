package com.company.clink.impl.async;

import com.company.Utils.CloseUtils;
import com.company.clink.core.IoArgs;
import com.company.clink.core.SendDispatcher;
import com.company.clink.core.SendPacket;
import com.company.clink.core.Sender;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncSendDispatcher implements SendDispatcher{
    private final Sender sender;
    private final Queue<SendPacket> sendPacketQueue = new ConcurrentLinkedQueue<>();

    private final AtomicBoolean isSending = new AtomicBoolean();
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    private SendPacket packetTemp;
    private IoArgs ioArgs = new IoArgs();

    //当前packet的大小和进度
    private int total;
    private int position;

    public AsyncSendDispatcher(Sender sender){
        this.sender = sender;
    }

    @Override
    public void send(SendPacket sendPacket) {
        sendPacketQueue.offer(sendPacket);
        if (isSending.compareAndSet(false, true)){
            sendNextPacket();
        }
    }

    @Override
    public void cancel(SendPacket sendPacket) {

    }

    private SendPacket takePacket(){
        SendPacket packet = sendPacketQueue.poll();
        if (packet != null && packet.isCanceled()){
            //已取消
            return takePacket();
        }
        return packet;
    }

    private void sendNextPacket(){
        SendPacket temp = packetTemp;
        if (temp != null){
            CloseUtils.close(temp);
        }

        SendPacket packet = packetTemp = takePacket();

        if (packet == null){
            //队列为空，取消发送状态
            isSending.set(false);
            return;
        }

        total = packet.length();
        position = 0;

        sendCurrentPacket();
    }

    private void sendCurrentPacket(){
        IoArgs args = ioArgs;

        //开始清理
        args.startWriting();

        if (position >= total){
            sendNextPacket();
            return;
        }else if (position == 0){
            //首包，需要携带长度信息
            args.writeLength(total);
        }

        byte[] bytes = packetTemp.bytes();
        //把bytes写入到ioArgs
        int count = args.readFrom(bytes, position);
        position += count;

        //完成封装
        args.finishWriting();

        try {
            sender.sendAsync(args, ioArgsEventListener);
        } catch (IOException e) {
            closeAndNotify();
        }
    }

    private void closeAndNotify(){
        CloseUtils.close(this);
    }

    private final IoArgs.IoArgsEventListener ioArgsEventListener = new IoArgs.IoArgsEventListener() {
        @Override
        public void onStarted(IoArgs args) {

        }

        @Override
        public void onCompleted(IoArgs args) {
            //继续发送当前包
            sendCurrentPacket();
        }
    };

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)){
            isSending.set(false);
            SendPacket sendPacket = this.packetTemp;
            if (sendPacket != null){
                packetTemp = null;
                CloseUtils.close(sendPacket);
            }
        }
    }
}

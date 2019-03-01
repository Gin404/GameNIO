package com.company.clink.impl.async;

import com.company.Utils.CloseUtils;
import com.company.clink.box.StringReceivePacket;
import com.company.clink.core.IoArgs;
import com.company.clink.core.ReceiveDispatcher;
import com.company.clink.core.ReceivePacket;
import com.company.clink.core.Receiver;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncReceiveDispatcher implements ReceiveDispatcher {
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    private final Receiver receiver;
    private final ReceivePacketCallback callback;

    private IoArgs ioArgs = new IoArgs();
    private StringReceivePacket packetTemp;
    private byte[] buffer;
    private int total;
    private int position;

    public AsyncReceiveDispatcher(Receiver receiver, ReceivePacketCallback callback){
        this.receiver = receiver;
        this.receiver.setReceiveListener(ioArgsEventListener);
        this.callback = callback;
    }

    @Override
    public void start() {
        registerReceive();
    }

    private void registerReceive(){
        try {
            receiver.receiveAsync(ioArgs);
        }catch (IOException e){
            closeAndNotify();
        }
    }

    private void closeAndNotify(){
        CloseUtils.close(this);
    }

    @Override
    public void stop() {

    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)){
            ReceivePacket receivePacket = packetTemp;
            if (receivePacket != null){
                packetTemp = null;
                CloseUtils.close(receivePacket);
            }
        }
    }

    /**
     * 解析数据到packet
     * @param args
     */
    private void assemblePacket(IoArgs args){
        if (packetTemp == null){
            int length = args.readLength();
            packetTemp = new StringReceivePacket(length);
            buffer = new byte[length];
            total = length;
            position = 0;
        }

        int count = args.writeTo(buffer, 0);
        if (count > 0){
            packetTemp.save(buffer, count);
            position += count;
            if (position == total){
                completePacket();
                packetTemp = null;
            }
        }
    }

    private void completePacket() {
        StringReceivePacket stringReceivePacket = this.packetTemp;
        CloseUtils.close(stringReceivePacket);
        callback.onReceivePacketCompleted(stringReceivePacket);
    }

    private IoArgs.IoArgsEventListener ioArgsEventListener = new IoArgs.IoArgsEventListener() {
        @Override
        public void onStarted(IoArgs args) {
            int receiveSize;
            if (packetTemp == null){
                receiveSize = 4;
            }else {
                receiveSize = Math.min(total-position, args.capacity());
            }
            //设置本次接收数据大小
            args.setLimit(receiveSize);
        }

        @Override
        public void onCompleted(IoArgs args) {
            //解析数据
            assemblePacket(args);
            //继续接收下一条
            registerReceive();
        }
    };
}

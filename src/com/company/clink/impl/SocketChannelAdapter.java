package com.company.clink.impl;

import com.company.Utils.CloseUtils;
import com.company.clink.core.IoArgs;
import com.company.clink.core.IoProvider;
import com.company.clink.core.Receiver;
import com.company.clink.core.Sender;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;

public class SocketChannelAdapter implements Sender, Receiver, Closeable {
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    //具体发送承载
    private final SocketChannel channel;
    private final IoProvider ioProvider;
    private final OnChannelStatusChangedListener listener;

    private IoArgs.IoArgsEventListener receiveIoEventListener;
    private IoArgs.IoArgsEventListener sendIoEventListener;

    private IoArgs receiveArgsTemp;

    public SocketChannelAdapter(SocketChannel channel, IoProvider ioProvider, OnChannelStatusChangedListener listener) throws IOException {
        this.channel = channel;
        this.ioProvider = ioProvider;
        this.listener = listener;

        channel.configureBlocking(false);
    }

    @Override
    public void setReceiveListener(IoArgs.IoArgsEventListener listener) {
        receiveIoEventListener = listener;
    }

    @Override
    public boolean receiveAsync(IoArgs args) throws IOException {
        if (isClosed.get()){
            throw new IOException("Current channel is closed.");
        }

        receiveArgsTemp = args;

        //返回接收是否成功
        return ioProvider.registerInput(channel, inputCallback);
    }

    @Override
    public boolean sendAsync(IoArgs args, IoArgs.IoArgsEventListener listener) throws IOException {
        if (isClosed.get()){
            throw new IOException("Current channel is closed.");
        }

        sendIoEventListener = listener;
        //当前发送的数据附加到回调中
        outputCallback.setAttach(args);
        //返回接收是否成功
        return ioProvider.registerOutput(channel, outputCallback);
    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)){
            //解除注册回调
            ioProvider.unRegisterInput(channel);
            ioProvider.unRegisterOutput(channel);
            //关闭
            CloseUtils.close(channel);
            //回调当前channel已关闭
            listener.onChannelClosed(channel);
        }
    }

    private final IoProvider.HandleInputCallback inputCallback = new IoProvider.HandleInputCallback() {
        //若想观察重复select现象可在此处设置
        //Thread.sleep(5000);
        @Override
        protected void canProviderInput() {
            if (isClosed.get()){
                return;
            }

            IoArgs args = receiveArgsTemp;
            //转换为局部变量
            IoArgs.IoArgsEventListener listener = SocketChannelAdapter.this.receiveIoEventListener;

            listener.onStarted(args);

            //具体读取
            try {
                if (args.readFrom(channel) > 0){
                    //读取完成回调
                    listener.onCompleted(args);
                }else {
                    throw new IOException("Cannot read any data.");
                }
            }catch (IOException e){
                CloseUtils.close(SocketChannelAdapter.this);
            }
        }
    };

    //待实现
    private final IoProvider.HandleOutputCallback outputCallback = new IoProvider.HandleOutputCallback() {
        @Override
        protected void canProviderOutput(Object attach) {
            if (isClosed.get()){
                return;
            }

            IoArgs args = getAttach();
            IoArgs.IoArgsEventListener listener = sendIoEventListener;

            listener.onStarted(args);

            //具体发送
            try {
                if (args.writeTo(channel) > 0){
                    //发送完成回调
                    listener.onCompleted(args);
                }else {
                    throw new IOException("Cannot write any data.");
                }
            }catch (IOException e){
                CloseUtils.close(SocketChannelAdapter.this);
            }
        }
    };


    //channel关闭回调，由connector实现，
    public interface OnChannelStatusChangedListener{
        void onChannelClosed(SocketChannel channel);
    }
}

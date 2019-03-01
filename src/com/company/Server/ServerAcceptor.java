package com.company.Server;

import com.company.Utils.CloseUtils;

import java.io.IOException;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;

public class ServerAcceptor extends Thread {
    private boolean done = false;
    private final Selector selector;
    private final AcceptListener listener;
    private final CountDownLatch latch = new CountDownLatch(1);

    ServerAcceptor(AcceptListener listener) throws IOException {
        super("Sever-accept-thread");
        this.listener = listener;
        this.selector = Selector.open();
    }

    boolean awaitRunning(){
        try{
            latch.await();
            return true;
        }catch (InterruptedException e){
            return false;
        }
    }

    @Override
    public void run(){
        super.run();
        //回调已进入运行
        latch.countDown();

        Selector selector = this.selector;

        // 轮询，等待客户端连接
        do {
            // 得到客户端

            try {
                //是否有准备IO的channel
                if (selector.select() == 0){
                    if (done){
                        break;
                    }
                    continue;
                }

                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()){
                    if (done){
                        break;
                    }
                    SelectionKey key = iterator.next();
                    //使用过就移除
                    iterator.remove();

                    //检查客户端是否是到达状态
                    if (key.isAcceptable()){
                        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
                        //非阻塞状态拿到客户端
                        SocketChannel socketChannel = serverSocketChannel.accept();
                        listener.onNewSocketArrived(socketChannel);
                    }

                }
            } catch (IOException e) {
                e.printStackTrace();
            }



        }while (!done);

        System.out.println("Server acceptor stopped.");
    }

    void exit() {
        done = true;
        CloseUtils.close(selector);
    }
    Selector getSelector() {
        return selector;
    }

    interface AcceptListener{
        void onNewSocketArrived(SocketChannel socketChannel);
    }
}

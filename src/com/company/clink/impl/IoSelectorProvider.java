package com.company.clink.impl;

import com.company.Utils.CloseUtils;
import com.company.clink.core.IoProvider;

import java.io.IOException;
import java.nio.channels.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 提供注册，所有channel均在此注册
 */
public class IoSelectorProvider implements IoProvider {
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    //是否处于input注册和output注册的过程
    private final AtomicBoolean inRegInput = new AtomicBoolean(false);
    private final AtomicBoolean inRegOutput = new AtomicBoolean(false);

    private final Selector readSelector;
    private final Selector writeSelector;

    private final HashMap<SelectionKey, Runnable> inputCallBackMap = new HashMap<>();
    private final HashMap<SelectionKey, Runnable> outputCallBackMap = new HashMap<>();

    private final ExecutorService inputHandlePool;
    private final ExecutorService outputHandlePool;

    public IoSelectorProvider() throws IOException {
        readSelector = Selector.open();
        writeSelector = Selector.open();

        inputHandlePool = Executors.newFixedThreadPool(4, new IoProviderThreadFactory("IoProvider-Input-Thread"));
        outputHandlePool = Executors.newFixedThreadPool(4, new IoProviderThreadFactory("IoProvider-Output-Thread"));

        //开始输入和输出监听
        startRead();
        startWrite();
    }

    private void startRead() {
        Thread thread = new SelectThread("IoSelectorProvider ReadSelector Thread",
                isClosed, inRegInput, readSelector, inputCallBackMap, inputHandlePool, SelectionKey.OP_READ);
        thread.start();
    }

    //处理相应的key：将相应的runnable交给对应的线程池
    private static void handleSelection(SelectionKey key, int keyOps, HashMap<SelectionKey,Runnable> map,
                                 ExecutorService pool, AtomicBoolean locker) {
        synchronized (locker) {
            try {
                //******
                //防止线程池未处理完读取，selector再次select到这个key， 取消对keyOps的监听
                key.interestOps(key.readyOps() & ~keyOps); //这里和key.cancel()等价，因为读写selector分开
            }catch (CancelledKeyException e){
                return;
            }
        }
        Runnable runnable = null;

        try{
            runnable = map.get(key);
        }catch (Exception ignored){

        }

        if (runnable != null && !pool.isShutdown()){
            //异步调度
            pool.execute(runnable);
        }


    }

    private void startWrite(){
        Thread thread = new SelectThread("IoSelectorProvider WriteSelector Thread",
                isClosed, inRegOutput, writeSelector, outputCallBackMap, outputHandlePool, SelectionKey.OP_WRITE);
        thread.start();
    }

    static class SelectThread extends Thread{
        private final AtomicBoolean isClosed;
        private final AtomicBoolean locker;
        private final Selector selector;
        private final HashMap<SelectionKey, Runnable> callMap;
        private final ExecutorService pool;
        private final int keyOps;

        SelectThread(String name, AtomicBoolean isClosed, AtomicBoolean locker,
                            Selector selector,
                            HashMap<SelectionKey, Runnable> callMap,
                            ExecutorService pool, int keyOps){
            super(name);
            this.setPriority(Thread.MAX_PRIORITY);
            this.isClosed = isClosed;
            this.locker = locker;
            this.selector = selector;
            this.callMap = callMap;
            this.pool = pool;
            this.keyOps = keyOps;
        }

        @Override
        public void run() {
            AtomicBoolean locker = this.locker;
            AtomicBoolean isClosed = this.isClosed;
            Selector selector = this.selector;
            HashMap<SelectionKey, Runnable> callMap = this.callMap;
            ExecutorService pool = this.pool;

            super.run();
            while (!isClosed.get()){
                try {
                    if (selector.select() == 0) {
                        waitSelection(locker);
                        continue;
                    }

                    Set<SelectionKey> selectionKeys = selector.selectedKeys();
                    Iterator<SelectionKey> iterator = selectionKeys.iterator();
                    //拿到每一个事件后交给线程池去做
                    while (iterator.hasNext()){
                        SelectionKey selectionKey = iterator.next();
                        if (selectionKey.isValid()) {
                            handleSelection(selectionKey, keyOps, callMap, pool, locker);
                        }
                        iterator.remove();
                    }
                }catch (IOException e){
                    e.printStackTrace();
                }catch (ClosedSelectorException e){
                    break;
                }
            }
        }
    }

    @Override
    public boolean registerInput(SocketChannel channel, HandleInputCallback callback) {
        //与select线程是不同的线程，所以考虑线程安全
        return registerSelection(channel, readSelector, SelectionKey.OP_READ, inRegInput, inputCallBackMap, callback) != null;
    }

    @Override
    public boolean registerOutput(SocketChannel channel, HandleOutputCallback callback) {
        return registerSelection(channel, writeSelector, SelectionKey.OP_WRITE, inRegOutput, outputCallBackMap, callback) != null;
    }

    private static void waitSelection(final AtomicBoolean locker) {
        synchronized (locker){
            if (locker.get()){
                try {
                    locker.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static SelectionKey registerSelection(SocketChannel channel, Selector selector, int registerOps, AtomicBoolean locker,
                                 HashMap<SelectionKey, Runnable> map, Runnable runnable){
        synchronized (locker){
            //设置锁定状态
            locker.set(true);

            try{
                //唤醒当前的selector，让selector不处于select()状态
                selector.wakeup();

                SelectionKey key = null;
                if (channel.isRegistered()){
                    //查询是否注册过
                    key = channel.keyFor(selector);

                    if (key != null){
                        key.interestOps(key.readyOps() | registerOps);
                    }
                }

                if (key == null){
                    //注册selector得到key
                    key = channel.register(selector, registerOps);
                    //注册回调
                    map.put(key, runnable);
                }
                return key;

            }catch (ClosedChannelException
                    |CancelledKeyException
                    |ClosedSelectorException e){
                return null;
            }finally {
                // 解除锁定状态
                locker.set(false);
                try {
                    //通知
                    locker.notify();
                }catch (Exception ignored){

                }
            }
        }
    }

    @Override
    public void unRegisterInput(SocketChannel channel) {
        unRegisterSelection(channel, readSelector, inputCallBackMap, inRegInput);
    }

    @Override
    public void unRegisterOutput(SocketChannel channel) {
        unRegisterSelection(channel, writeSelector, outputCallBackMap, inRegOutput);
    }

    private static void unRegisterSelection(SocketChannel channel, Selector selector,
                                            Map<SelectionKey, Runnable> map, AtomicBoolean locker){
        synchronized (locker) {
            locker.set(true);
            selector.wakeup();
            try {
                if (channel.isRegistered()) {
                    SelectionKey key = channel.keyFor(selector);
                    if (key != null) {
                        //取消监听
                        key.cancel();
                        map.remove(key);
                    }
                }
            }finally {
                locker.set(false);
                try {
                    locker.notify();
                }catch (Exception ignored){

                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)){
            inputHandlePool.shutdown();
            outputHandlePool.shutdown();

            inputCallBackMap.clear();
            outputCallBackMap.clear();

            CloseUtils.close(readSelector, writeSelector);
        }
    }

    static class IoProviderThreadFactory implements ThreadFactory {
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        IoProviderThreadFactory(String namePrefix) {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() :
                    Thread.currentThread().getThreadGroup();
            this.namePrefix = namePrefix;
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r,
                    namePrefix + threadNumber.getAndIncrement(),
                    0);
            if (t.isDaemon())
                t.setDaemon(false);
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }
}

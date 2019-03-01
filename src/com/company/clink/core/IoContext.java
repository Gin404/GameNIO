package com.company.clink.core;

import java.io.IOException;

public class IoContext {
    private static IoContext INSTANCE;
    private final IoProvider ioProvider;

    private IoContext(IoProvider ioProvider) {
        this.ioProvider = ioProvider;
    }

    public IoProvider getIoProvider() {
        return ioProvider;
    }

    public static IoContext get() {
        return INSTANCE;
    }

    public static StartedBoot setup() {
        return new StartedBoot();
    }

    public static void close() throws IOException {
        if (INSTANCE != null) {
            INSTANCE.callClose();
        }
    }

    private void callClose() throws IOException {
        ioProvider.close();
    }

    //启动引导
    public static class StartedBoot {
        private IoProvider ioProvider;

        private StartedBoot() {

        }

        //注入ioprovider具体实现
        public StartedBoot ioProvider(IoProvider ioProvider) {
            this.ioProvider = ioProvider;
            return this;
        }

        //返回单例
        public IoContext start() {
            INSTANCE = new IoContext(ioProvider);
            return INSTANCE;
        }
    }

}

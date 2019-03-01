package com.company.Server;

import com.company.Constants.TCPConstants;
import com.company.clink.core.IoContext;
import com.company.clink.impl.IoSelectorProvider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Server {

    public static void main(String[] args) throws IOException {
        IoContext.setup()
                .ioProvider(new IoSelectorProvider())
                .start();

        TCPServer tcpServer = new TCPServer(TCPConstants.PORT_SERVER);
        boolean isSucceed = tcpServer.start();

        if (!isSucceed) {
            System.out.println("Start TCP server failed!");
            return;
        }

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));

        String str;
        do {
            str = bufferedReader.readLine();
            if (str == null
                    || str.length() == 0
                    || "00bye00".equalsIgnoreCase(str)){
                break;
            }
            tcpServer.broadcast(str);
        } while (true);

        tcpServer.stop();
        IoContext.close();
    }
}

package com.company.Server;

import com.company.Constants.Foo;
import com.company.Utils.CloseUtils;
import com.company.clink.box.StringReceivePacket;
import com.company.clink.core.Connector;
import com.company.handler.chain.ClientHandler;
import com.company.handler.chain.ConnectorCloseChain;
import com.company.handler.chain.ConnectorStringPacketChain;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class TCPServer implements ServerAcceptor.AcceptListener,  Room.GroupMessageAdapter {
    //服务器端口号
    private final int port;
    private final AtomicInteger roomId = new AtomicInteger(0);
    private final List<ClientHandler> clientHandlerList = new ArrayList<>();
    private final ExecutorService forwardingThreadPoolExecutor;
    private final ConcurrentHashMap<Integer, Room> rooms = new ConcurrentHashMap<>();
    private ServerAcceptor acceptor;
    private Selector selector;
    private ServerSocketChannel server;

    private final ServerStatistics statistics = new ServerStatistics();

    public TCPServer(int port) {
        this.port = port;

        //转发线程池
        this.forwardingThreadPoolExecutor = Executors.newSingleThreadExecutor();
        //this.rooms.put(Foo.DEFAULT_ROOM_NAME, new Room(Foo.DEFAULT_ROOM_NAME, this));
    }


    public boolean start(){
        try {
            // 启动Acceptor线程
            ServerAcceptor acceptor = new ServerAcceptor(this);

            ServerSocketChannel server = ServerSocketChannel.open();
            //配置为非阻塞
            server.configureBlocking(false);
            //绑定本地端口
            server.bind(new InetSocketAddress(port));
            //注册客户端连接到达监听
            server.register(acceptor.getSelector(), SelectionKey.OP_ACCEPT);

            this.server = server;
            this.acceptor = acceptor;
            //线程启动
            acceptor.start();
            if (acceptor.awaitRunning()) {
                System.out.println("服务器准备就绪～");
                System.out.println("服务器信息：" + server.getLocalAddress().toString());
                return true;
            } else {
                System.out.println("启动异常！");
                return false;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void stop() {
        if (acceptor != null) {
            acceptor.exit();
        }

        ClientHandler[] connectorHandlers;
        synchronized (clientHandlerList) {
            connectorHandlers = clientHandlerList.toArray(new ClientHandler[0]);
            clientHandlerList.clear();
        }
        for (ClientHandler connectorHandler : connectorHandlers) {
            connectorHandler.exit();
        }

        CloseUtils.close(server);
    }


    void broadcast(String str) {
        str = "系统通知："+ str;
        synchronized (clientHandlerList) {
            for (ClientHandler clientHandler : clientHandlerList) {
                sendMessageToClient(clientHandler, str);
            }
        }
    }

    /**
     * 发送消息给某个客户端
     *
     * @param handler 客户端
     * @param msg     消息
     */
    @Override
    public void sendMessageToClient(ClientHandler handler, String msg) {
        handler.send(msg);
        statistics.sendSize++;
    }


    /**
     * 获取当前的状态信息
     */
    Object[] getStatusString() {
        return new String[]{
                "客户端数量：" + clientHandlerList.size(),
                "发送数量：" + statistics.sendSize,
                "接收数量：" + statistics.receiveSize
        };
    }


    @Override
    public void onNewSocketArrived(SocketChannel socketChannel) {
        try {
            ClientHandler clientHandler = new ClientHandler(socketChannel, forwardingThreadPoolExecutor);
            System.out.println(clientHandler.getClientInfo() + " : Connected");
            // 添加收到消息的处理责任链
            clientHandler.getStringPacketChain()
                    .appendLast(statistics.statisticsChain())
                    .appendLast(new ParseCommandConnectorStringPacketChain());

            // 添加关闭链接时的责任链
            clientHandler.getCloseChain()
                    .appendLast(new RemoveQueueOnConnectorClosedChain());


            synchronized (TCPServer.this){
                clientHandlerList.add(clientHandler);
                System.out.println("当前客户端数量：" + clientHandlerList.size());
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("客户端连接异常："+ e.getMessage());
        }
    }


    /**
     * 移除队列，在链接关闭回调时
     */
    private class RemoveQueueOnConnectorClosedChain extends ConnectorCloseChain {

        @Override
        protected boolean consume(ClientHandler handler, Connector connector) {
            int rid = handler.getRid();
            Room room = rooms.get(rid);

            //最后一个人，直接移除房间
            if (room != null && room.getCapacity().get() == 1){
                rooms.remove(rid);
                return true;
            }

            if(room != null && room.removeMember(handler)){
                if (room.getInGame().get()){
                    sendMessageToClient(handler, "23");
                    int c = room.rndRmv();
                    room.forward(handler, "32 " + c);
                }else {
                    sendMessageToClient(handler, "23");
                    room.forward(handler, "31 " + room.getCapacity().get());
                }
            }

            return true;
        }
    }

    /**
     * 解析收到的消息，当前节点主要做命令的解析，
     * 如果子节点也未进行数据消费，那么则进行二次消费，直接返回收到的数据
     */
    private class ParseCommandConnectorStringPacketChain extends ConnectorStringPacketChain {
        @Override
        protected boolean consume(ClientHandler handler, StringReceivePacket packet) {
            String str = packet.string();
            System.out.println(str);
            //新建房间，房间号自增，回送房间号给创建者
            if (str.startsWith(Foo.COMMAND_ROOM_CREATE)){
                int newId = roomId.getAndIncrement();
                Room nRoom = new Room(newId, TCPServer.this);
                nRoom.addMember(handler);
                rooms.put(newId, nRoom);
                handler.setRid(newId);
                handler.setHandlerType(0);
                sendMessageToClient(handler, "10 " + newId );
                return true;
            }
            //加入房间，根据请求者的房间号将其加入相应room实例，对不存在的房间和房间人满做相应的回应
            else if (str.startsWith(Foo.COMMAND_ROOM_JOIN)){
                int rid = Integer.parseInt(str.substring(Foo.COMMAND_ROOM_JOIN.length()+1));
                Room room = rooms.get(rid);
                if (room == null){
                    sendMessageToClient(handler, "21");
                    return true;
                }
                if (room.addMember(handler)){
                    sendMessageToClient(handler, "22 " + room.getCapacity().get());
                    handler.setRid(rid);
                    handler.setHandlerType(1);
                    room.forward(handler, "30 " + room.getCapacity().get());
                }else {
                    sendMessageToClient(handler, "20");
                    return true;
                }
                return true;
            }
            //离开房间，通知其他人人数变更
            else if (str.startsWith(Foo.COMMAND_ROOM_LEAVE)){
                Room room = rooms.get(handler.getRid());
                if (room != null && room.getCapacity().get() == 1){
                    rooms.remove(handler.getRid());
                    return true;
                }

                if (room.removeMember(handler)){
                    if (room.getInGame().get()){
                        sendMessageToClient(handler, "23");
                        int c = room.rndRmv();
                        room.forward(handler, "32 " + c);
                    }else {
                        sendMessageToClient(handler, "23");
                        room.forward(handler, "31 " + room.getCapacity().get());
                    }
                }

                return true;
            }
            //游戏开始，发送广播
            else if (str.startsWith(Foo.COMMAND_START)){
                Room room = rooms.get(handler.getRid());
                room.start();
                sendMessageToClient(handler, "40");
                room.forward(handler, "40");
                return true;
            }
            //玩家选颜色
            else if (str.startsWith(Foo.COMMAND_CHOICE)){
                Room room = rooms.get(handler.getRid());
                int colour = Integer.parseInt(str.substring(Foo.COMMAND_CHOICE.length()+1));
                if (room.isUnluck(colour)){
                    sendMessageToClient(handler, "50");
                    handler.setUnluck(true);
                }else {
                    sendMessageToClient(handler, "51");
                    handler.setUnluck(false);
                }
                room.removeColor(colour);
                room.forward(handler, "60 " + colour);
                return true;
            }

            //显示图片之后点击继续，同步结束或者继续
            else if(str.startsWith(Foo.COMMAND_CONTINUE)){
                Room room = rooms.get(handler.getRid());
                if (handler.isUnluck()){
                    int capacity = room.getCapacity().get();
                    room.forwardCont();
                    room.end();
                }else {
                    sendMessageToClient(handler, "61");
                    room.forward(handler, "61");
                }
                return true;
            }

            //退出
            else if (str.startsWith(Foo.COMMAND_END)){
                Room room = rooms.get(handler.getRid());
                room.end();
                room.forward(handler, "70");
                return true;
            }
            return false;
        }

        @Override
        protected boolean consumeAgain(ClientHandler handler, StringReceivePacket stringReceivePacket) {
            // 捡漏的模式，当我们第一遍未消费，然后又没有加入到群，自然没有后续的节点消费
            // 此时我们进行二次消费，返回发送过来的消息
            sendMessageToClient(handler, stringReceivePacket.string());
            return true;
        }

    }
}

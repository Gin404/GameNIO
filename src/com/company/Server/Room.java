package com.company.Server;

import com.company.clink.box.StringReceivePacket;
import com.company.handler.chain.ClientHandler;
import com.company.handler.chain.ConnectorStringPacketChain;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Room {
    private final int roomId;
    private final GroupMessageAdapter adapter;
    private final List<ClientHandler> members = new ArrayList<>();
    private final AtomicBoolean inGame = new AtomicBoolean(false);
    private final AtomicInteger colors = new AtomicInteger(4095);

    public Room(int roomId, GroupMessageAdapter adapter){
        this.roomId = roomId;
        this.adapter = adapter;
        this.inGame.set(false);
    }

    public void initializeGame(){
        synchronized (this) {
            inGame.set(false);
            colors.set(4095);
        }
    }

    public void removeColor(int color){
        synchronized (colors){
            int colorsTemp = colors.get();
            colorsTemp = colorsTemp ^ color;
            colors.set(colorsTemp);
        }
    }

    public void addColor(int color){
        synchronized (colors){
            int colorsTemp = colors.get();
            colorsTemp = colorsTemp | color;
            colors.set(colorsTemp);
        }
    }

    public int getRoomId(){
        return this.roomId;
    }

    public boolean addMember(ClientHandler handler){
        synchronized (members){
            if (!members.contains(handler)){
                members.add(handler);
                handler.getStringPacketChain()
                        .appendLast(new ForwardConnectorStringPacketChain());
                System.out.println("Room[" + roomId + "] add new member:" + handler.getClientInfo());
                return true;
            }
        }
        return false;
    }

    public boolean removeMember(ClientHandler handler){
        synchronized (members) {
            if (members.remove(handler)) {
                handler.getStringPacketChain()
                        .remove(ForwardConnectorStringPacketChain.class);
                System.out.println("Group[" + roomId + "] leave member:" + handler.getClientInfo());
                return true;
            }
        }
        return false;

    }

    /**
     * 进行消息转发的责任链节点
     */
    private class ForwardConnectorStringPacketChain extends ConnectorStringPacketChain {

        @Override
        protected boolean consume(ClientHandler handler, StringReceivePacket stringReceivePacket) {
            synchronized (members) {
                for (ClientHandler member : members) {
                    if (member == handler) {
                        continue;
                    }
                    adapter.sendMessageToClient(member, stringReceivePacket.string());
                }
                return true;
            }
        }
    }

    /**
     * 进行消息发送的Adapter
     */
    interface GroupMessageAdapter {
        /**
         * 发送消息的接口
         *
         * @param handler 客户端
         * @param msg     消息
         */
        void sendMessageToClient(ClientHandler handler, String msg);
    }

}

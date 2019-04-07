package com.company.Server;

import com.company.Constants.Foo;
import com.company.clink.box.StringReceivePacket;
import com.company.handler.chain.ClientHandler;
import com.company.handler.chain.ConnectorStringPacketChain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Room {
    private final int roomId;
    private int unluck;
    private final Random random;
    private final GroupMessageAdapter adapter;
    private final List<ClientHandler> members = new ArrayList<>();
    private final AtomicBoolean inGame = new AtomicBoolean(false);
    private final AtomicInteger colors = new AtomicInteger(0);
    private final AtomicInteger capacity = new AtomicInteger(0);

    public Room(int roomId, GroupMessageAdapter adapter){
        this.roomId = roomId;
        this.adapter = adapter;
        this.inGame.set(false);
        unluck = -1;
        random = new Random();
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

    /**
     * 游戏中有人离开，随机返回一个消失颜色，但肯定不是倒霉色
     * @return
     */
    public int rndRmv(){
        //1101110
        int newCap = capacity.decrementAndGet();
        int cls = colors.get();

        int c = 0;
        int rd = 1;
        while (cls >= 0){
            c = cls%2;
            cls /= 2;
            if (c == 1 && c != unluck){
                return rd;
            }
            rd *= 2;
        }

        return 0;
    }

    public void start(){
        int c = capacity.get();
        colors.set((int)Math.pow(2, c)-1);
        unluck = random.nextInt(c);
        unluck = (int)Math.pow(2, unluck);
        inGame.set(true);
    }

    public void end(){
        unluck = -1;
        inGame.set(false);
        colors.set(0);
    }

    public boolean isUnluck(int color){
        if (color == unluck){
            return true;
        }else {
            return false;
        }
    }

    public AtomicBoolean getInGame() {
        return inGame;
    }

    public AtomicInteger getCapacity() {
        return capacity;
    }

    public int getRoomId(){
        return this.roomId;
    }

    public boolean addMember(ClientHandler handler){
        if (capacity.get() == Foo.MAX_CAPACITY || inGame.get() == true){
            return false;
        }
        synchronized (members){
            if (!members.contains(handler)){
                members.add(handler);
                capacity.incrementAndGet();
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
                capacity.decrementAndGet();
                handler.getStringPacketChain()
                        .remove(ForwardConnectorStringPacketChain.class);
                System.out.println("Group[" + roomId + "] leave member:" + handler.getClientInfo());
                return true;
            }
        }
        return false;

    }

    public boolean forward(ClientHandler handler, String string){
        synchronized (members) {
            for (ClientHandler member : members) {
                if (member == handler) {
                    continue;
                }
                adapter.sendMessageToClient(member, string);
            }
            return true;
        }
    }

    public boolean forwardCont(){
        synchronized (members) {
            for (ClientHandler member : members) {
                adapter.sendMessageToClient(member, "62 " + member.getHandlerType() + " " + capacity);
            }
            return true;
        }
    }

    /**
     * 进行消息转发的责任链节点
     */
    private class ForwardConnectorStringPacketChain extends ConnectorStringPacketChain {

        @Override
        protected boolean consume(ClientHandler handler, StringReceivePacket stringReceivePacket) {
            return forward(handler, stringReceivePacket.toString());
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

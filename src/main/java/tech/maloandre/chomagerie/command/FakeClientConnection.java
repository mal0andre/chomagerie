package tech.maloandre.chomagerie.command;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

import java.net.SocketAddress;
import java.util.function.Consumer;

final class FakeClientConnection extends Connection {
    private static final SocketAddress ADDRESS = new SocketAddress() {
    };
    private PacketListener packetListener;
    private boolean connected = true;

    FakeClientConnection() {
        super(PacketFlow.SERVERBOUND);
    }

    @Override
    public void send(Packet<?> packet) {
    }

    @Override
    public void send(Packet<?> packet, ChannelFutureListener listener) {
    }

    @Override
    public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) {
    }

    @Override
    public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocolInfo, T packetListener) {
        this.packetListener = packetListener;
    }

    @Override
    public void runOnceConnected(Consumer<Connection> consumer) {
        consumer.accept(this);
    }

    @Override
    public void flushChannel() {
    }

    @Override
    public void tick() {
        if (packetListener instanceof net.minecraft.network.TickablePacketListener tickablePacketListener) {
            tickablePacketListener.tick();
        }
    }

    @Override
    public void setReadOnly() {
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public boolean isConnecting() {
        return false;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return ADDRESS;
    }

    @Override
    public String getLoggableAddress(boolean redacted) {
        return "chomplayer";
    }

    @Override
    public void disconnect(Component reason) {
        connected = false;
    }

    @Override
    public void disconnect(DisconnectionDetails details) {
        connected = false;
    }

    @Override
    public void handleDisconnection() {
    }

    @Override
    public PacketListener getPacketListener() {
        return packetListener;
    }
}

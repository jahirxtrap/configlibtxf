package com.jahirtrap.configlib;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class TXFConfigServer {
    private static final Map<String, Map<String, Object>> cachedDefaults = new HashMap<>();
    private static final Map<String, Map<String, Object>> cachedServerValues = new HashMap<>();
    private static boolean registered = false;
    private static final String PROTOCOL_VERSION = "1";
    private static SimpleChannel CHANNEL;

    public static void register() {
        if (registered) return;
        registered = true;
        CHANNEL = NetworkRegistry.ChannelBuilder
                .named(new ResourceLocation("configlibtxf", "config_sync"))
                .networkProtocolVersion(() -> PROTOCOL_VERSION)
                .clientAcceptedVersions(PROTOCOL_VERSION::equals)
                .serverAcceptedVersions(PROTOCOL_VERSION::equals)
                .simpleChannel();
        CHANNEL.registerMessage(0, ConfigSyncMessage.class,
                ConfigSyncMessage::encode,
                ConfigSyncMessage::decode,
                ConfigSyncMessage::handle);
        MinecraftForge.EVENT_BUS.addListener(TXFConfigServer::onPlayerJoin);
        if (FMLEnvironment.dist.isClient())
            MinecraftForge.EVENT_BUS.addListener(TXFConfigServer::onClientDisconnect);
    }

    private static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            sendToPlayer(player);
    }

    private static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        resetAll();
    }

    public static boolean hasSyncFields(String modid) {
        Class<? extends TXFConfig> config = TXFConfig.configClass.get(modid);
        if (config == null) return false;
        for (Field field : config.getFields())
            if (field.isAnnotationPresent(TXFConfig.Entry.class) && field.getAnnotation(TXFConfig.Entry.class).syncServer())
                return true;
        return false;
    }

    public static void sendToPlayer(ServerPlayer player) {
        for (var entry : TXFConfig.configClass.entrySet()) {
            String modid = entry.getKey();
            if (!hasSyncFields(modid)) continue;
            byte[] data = serialize(modid);
            if (data != null)
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ConfigSyncMessage(modid, data));
        }
    }

    public static byte[] serialize(String modid) {
        Class<? extends TXFConfig> config = TXFConfig.configClass.get(modid);
        if (config == null) return null;
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (Field field : config.getFields()) {
                if (!field.isAnnotationPresent(TXFConfig.Entry.class)) continue;
                if (!field.getAnnotation(TXFConfig.Entry.class).syncServer()) continue;
                writeField(buf, field);
            }
        } catch (Exception e) {
            return null;
        }
        return buf.array();
    }

    public static void apply(String modid, byte[] data) {
        Class<? extends TXFConfig> config = TXFConfig.configClass.get(modid);
        if (config == null) return;
        cacheDefaults(modid, config);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            for (Field field : config.getFields()) {
                if (!field.isAnnotationPresent(TXFConfig.Entry.class)) continue;
                if (!field.getAnnotation(TXFConfig.Entry.class).syncServer()) continue;
                readField(buf, field);
            }
        } catch (Exception ignored) {
        }
        cacheServerValues(modid, config);
    }

    public static void reapply(String modid) {
        var serverVals = cachedServerValues.get(modid);
        if (serverVals == null) return;
        Class<? extends TXFConfig> config = TXFConfig.configClass.get(modid);
        if (config == null) return;
        for (var entry : serverVals.entrySet()) {
            try {
                Field field = config.getField(entry.getKey());
                field.set(null, entry.getValue());
            } catch (Exception ignored) {
            }
        }
    }

    public static boolean isActive(String modid) {
        return cachedServerValues.containsKey(modid);
    }

    public static Object getServerValue(String modid, String fieldName) {
        var vals = cachedServerValues.get(modid);
        return vals != null ? vals.get(fieldName) : null;
    }

    public static void reset(String modid) {
        var defaults = cachedDefaults.get(modid);
        if (defaults == null) return;
        Class<? extends TXFConfig> config = TXFConfig.configClass.get(modid);
        if (config == null) return;
        for (var entry : defaults.entrySet()) {
            try {
                Field field = config.getField(entry.getKey());
                field.set(null, entry.getValue());
            } catch (Exception ignored) {
            }
        }
    }

    public static void resetAll() {
        for (String modid : cachedDefaults.keySet()) reset(modid);
        cachedServerValues.clear();
    }

    private static void cacheServerValues(String modid, Class<? extends TXFConfig> config) {
        Map<String, Object> serverVals = new HashMap<>();
        try {
            for (Field field : config.getFields()) {
                if (!field.isAnnotationPresent(TXFConfig.Entry.class)) continue;
                if (!field.getAnnotation(TXFConfig.Entry.class).syncServer()) continue;
                serverVals.put(field.getName(), field.get(null));
            }
        } catch (Exception ignored) {
        }
        cachedServerValues.put(modid, serverVals);
    }

    private static void cacheDefaults(String modid, Class<? extends TXFConfig> config) {
        if (cachedDefaults.containsKey(modid)) return;
        Map<String, Object> defaults = new HashMap<>();
        try {
            for (Field field : config.getFields()) {
                if (!field.isAnnotationPresent(TXFConfig.Entry.class)) continue;
                if (!field.getAnnotation(TXFConfig.Entry.class).syncServer()) continue;
                defaults.put(field.getName(), field.get(null));
            }
        } catch (Exception ignored) {
        }
        cachedDefaults.put(modid, defaults);
    }

    @SuppressWarnings("unchecked")
    private static void writeField(FriendlyByteBuf buf, Field field) throws Exception {
        Class<?> type = field.getType();
        Object val = field.get(null);
        if (type == boolean.class) buf.writeBoolean((boolean) val);
        else if (type == int.class) buf.writeInt((int) val);
        else if (type == float.class) buf.writeFloat((float) val);
        else if (type == double.class) buf.writeDouble((double) val);
        else if (type == String.class) buf.writeUtf((String) val);
        else if (type.isEnum()) buf.writeUtf(((Enum<?>) val).name());
        else if (type == List.class) {
            List<String> list = (List<String>) val;
            buf.writeInt(list.size());
            for (String s : list) buf.writeUtf(s);
        }
    }

    @SuppressWarnings("unchecked")
    private static void readField(FriendlyByteBuf buf, Field field) throws Exception {
        Class<?> type = field.getType();
        if (type == boolean.class) field.setBoolean(null, buf.readBoolean());
        else if (type == int.class) field.setInt(null, buf.readInt());
        else if (type == float.class) field.setFloat(null, buf.readFloat());
        else if (type == double.class) field.setDouble(null, buf.readDouble());
        else if (type == String.class) field.set(null, buf.readUtf());
        else if (type.isEnum()) field.set(null, Enum.valueOf((Class<Enum>) type, buf.readUtf()));
        else if (type == List.class) {
            int size = buf.readInt();
            List<String> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) list.add(buf.readUtf());
            field.set(null, list);
        }
    }

    public record ConfigSyncMessage(String modid, byte[] data) {
        public static void encode(ConfigSyncMessage msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.modid);
            buf.writeByteArray(msg.data);
        }

        public static ConfigSyncMessage decode(FriendlyByteBuf buf) {
            return new ConfigSyncMessage(buf.readUtf(), buf.readByteArray());
        }

        public static void handle(ConfigSyncMessage msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().setPacketHandled(true);
            TXFConfigServer.apply(msg.modid, msg.data);
        }
    }
}

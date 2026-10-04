package com.vomiter.farmersbp.network;

import com.vomiter.farmersbp.FarmerSBP;
import com.vomiter.farmersbp.cutting.CuttingBoardWrapper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public class ModNetwork {
    private static final String PROTOCOL = "1";
    public static SimpleChannel CHANNEL;
    public static void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CHANNEL = NetworkRegistry.newSimpleChannel(
                    FarmerSBP.modLoc("main"),
                    () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals
            );

            int id = 0;
            CHANNEL.messageBuilder(FBPCuttingResponse.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                    .encoder(FBPCuttingResponse::encode)
                    .decoder(FBPCuttingResponse::decode)
                    .consumerMainThread(FBPCuttingResponse::handle)
                    .add();
        });

    }

    public record FBPCuttingResponse(int enumOrdinal){
        public static void encode(FBPCuttingResponse pkt, FriendlyByteBuf buf){
            buf.writeInt(pkt.enumOrdinal);
        }

        public static FBPCuttingResponse decode(FriendlyByteBuf buf){
            return new FBPCuttingResponse(buf.readInt());
        }

        public static void handle(FBPCuttingResponse pkt, Supplier<NetworkEvent.Context> ctx) {
            CuttingBoardWrapper.setCuttingResult(CuttingBoardWrapper.CuttingResult.values()[pkt.enumOrdinal()]);
            ctx.get().setPacketHandled(true);
        }
    }

}

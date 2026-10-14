package com.vomiter.farmersbp.network;

import com.vomiter.farmersbp.FarmerSBP;
import com.vomiter.farmersbp.cutting.CuttingBoardWrapper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ModNetwork {
    private ModNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
                FBPCuttingResponse.TYPE,
                FBPCuttingResponse.STREAM_CODEC,
                FBPCuttingResponse::handle
        );
    }

    public record FBPCuttingResponse(int enumOrdinal) implements CustomPacketPayload {
        public static final Type<FBPCuttingResponse> TYPE =
                new Type<>(FarmerSBP.modLoc("cutting_response"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FBPCuttingResponse> STREAM_CODEC =
                StreamCodec.of(
                        (buf, payload) -> buf.writeVarInt(payload.enumOrdinal),
                        buf -> new FBPCuttingResponse(buf.readVarInt())
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void handle(FBPCuttingResponse payload, IPayloadContext context) {
            CuttingBoardWrapper.CuttingResult[] values = CuttingBoardWrapper.CuttingResult.values();
            if (payload.enumOrdinal >= 0 && payload.enumOrdinal < values.length) {
                CuttingBoardWrapper.setCuttingResult(values[payload.enumOrdinal]);
            }
        }
    }
}

package net.spaceeye.vmod.limits

import dev.architectury.networking.NetworkManager
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import net.spaceeye.vmod.networking.*
import net.spaceeye.vmod.networking.SerializableItem.get
import kotlin.math.max
import kotlin.math.min

data class DoubleLimit(var minValue: Double = -Double.MAX_VALUE, var maxValue: Double = Double.MAX_VALUE) { fun get(num: Double) = max(minValue, min(maxValue, num)) }
data class IntLimit   (var minValue: Int    =  Int.MIN_VALUE,    var maxValue: Int    = Int.MAX_VALUE   ) { fun get(num: Int)    = max(minValue, min(maxValue, num)) }

data class StrLimit   (var sizeLimit:Int = Int.MAX_VALUE) {
    fun get(str: String): String {
        if (str.length <= sizeLimit) { return str }
        return str.dropLast(str.length - sizeLimit)
    }
}

class ServerLimitsInstance: AutoSerializable {
    val compliance: DoubleLimit by get(0, DoubleLimit(1e-300, 1.0))
    val maxForce: DoubleLimit by get(1, DoubleLimit(1.0))
    val fixedDistance: DoubleLimit by get(2, DoubleLimit())
    val extensionDistance: DoubleLimit by get(3, DoubleLimit(0.001))
    val extensionSpeed: DoubleLimit by get(4, DoubleLimit(0.001))
    val distanceFromBlock: DoubleLimit by get(5, DoubleLimit(0.0001))
    val stripRadius: IntLimit by get(6, IntLimit(1, 10))
    val scale: DoubleLimit by get(7, DoubleLimit(0.001))
    val precisePlacementAssistSides: IntLimit by get(8, IntLimit(2, 11))

    val physRopeSegments: IntLimit by get(9, IntLimit(1, 100))
    val physRopeMassPerSegment: DoubleLimit by get(10, DoubleLimit(0.01, 10000.0))
    val physRopeRadius: DoubleLimit by get(11, DoubleLimit(0.01, 10.0))

    val channelLength: StrLimit by get(12, StrLimit(50))
}

object ServerLimits: NetworkingRegisteringFunctions {
    init {
        SerializableItem.registerSerializationItem(DoubleLimit::class, {it: Any, buf: FriendlyByteBuf -> it as DoubleLimit; buf.writeDouble(it.minValue); buf.writeDouble(it.maxValue) }) {buf -> DoubleLimit(buf.readDouble(), buf.readDouble())}
        SerializableItem.registerSerializationItem(IntLimit::class, {it: Any, buf: FriendlyByteBuf -> it as IntLimit; buf.writeInt(it.minValue); buf.writeInt(it.maxValue) }) {buf -> IntLimit(buf.readInt(), buf.readInt())}
        SerializableItem.registerSerializationItem(StrLimit::class, {it: Any, buf: FriendlyByteBuf -> it as StrLimit; buf.writeInt(it.sizeLimit)}) {buf -> StrLimit(buf.readInt())}
    }

    var instance: ServerLimitsInstance = ServerLimitsInstance()

    fun update() { c2sRequestServerLimitsUpdate.sendToServer(C2SRequestServerLimitsUpdate()) }

    private class C2SRequestServerLimitsUpdate: Serializable {
        override fun serialize(): FriendlyByteBuf {return getBuffer()}
        override fun deserialize(buf: FriendlyByteBuf) {}
    }

    private val c2sRequestServerLimitsUpdate = "request_server_limits_update" idWithConnc {
        object : C2SConnection<C2SRequestServerLimitsUpdate>(it, "server_limits") {
            override fun serverHandler(buf: FriendlyByteBuf, context: NetworkManager.PacketContext) {
                s2cSendServerLimitsUpdate.sendToClient(context.player as ServerPlayer, instance)
            }
        }
    }

    private val s2cSendServerLimitsUpdate = "send_server_limits_update" idWithConns {
        object : S2CConnection<ServerLimitsInstance>(it, "server_limits") {
            override fun clientHandler(buf: FriendlyByteBuf, context: NetworkManager.PacketContext) {
                val update = ServerLimitsInstance()
                update.deserialize(buf)
                instance = update
            }
        }
    }
}
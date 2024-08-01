package net.spaceeye.vmod.limits

import dev.architectury.networking.NetworkManager
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import net.spaceeye.vmod.VMConfig
import net.spaceeye.vmod.networking.*
import net.spaceeye.vmod.networking.SerializableItem.get
import net.spaceeye.vmod.toolgun.ClientToolGunState
import net.spaceeye.vmod.toolgun.ServerToolGunState
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

    fun updateFromServer() { c2sRequestServerLimits.sendToServer(C2SRequestServerLimits()) }
    fun tryUpdateToServer() { c2sSendUpdatedServerLimits.sendToServer(instance) }

    private class C2SRequestServerLimits: Serializable {
        override fun serialize(): FriendlyByteBuf {return getBuffer()}
        override fun deserialize(buf: FriendlyByteBuf) {}
    }

    private class S2CServerLimitsUpdateWasRejected: Serializable {
        override fun serialize(): FriendlyByteBuf {return getBuffer()}
        override fun deserialize(buf: FriendlyByteBuf) {}
    }

    private val c2sRequestServerLimits = "request_server_limits" idWithConnc {
        object : C2SConnection<C2SRequestServerLimits>(it, "server_limits") {
            override fun serverHandler(buf: FriendlyByteBuf, context: NetworkManager.PacketContext) {
                s2cSendCurrentServerLimits.sendToClient(context.player as ServerPlayer, instance)
            }
        }
    }

    private val s2cSendCurrentServerLimits = "send_current_server_limits" idWithConns {
        object : S2CConnection<ServerLimitsInstance>(it, "server_limits") {
            override fun clientHandler(buf: FriendlyByteBuf, context: NetworkManager.PacketContext) {
                val update = ServerLimitsInstance()
                update.deserialize(buf)
                instance = update
            }
        }
    }

    private val c2sSendUpdatedServerLimits = "send_updated_server_limits" idWithConnc {
        object : C2SConnection<ServerLimitsInstance>(it, "server_limits") {
            override fun serverHandler(buf: FriendlyByteBuf, context: NetworkManager.PacketContext) {
                val player = context.player as ServerPlayer
                if (!(ServerToolGunState.playerHasAccess(player) && player.hasPermissions(VMConfig.SERVER.PERMISSIONS.VMOD_CHANGING_SERVER_SETTINGS_LEVEL))) {
                    s2cServerLimitsUpdateWasRejected.sendToClient(player, S2CServerLimitsUpdateWasRejected())
                    return
                }

                val newInstance = ServerLimitsInstance()
                newInstance.deserialize(buf)

                instance = newInstance

            }
        }
    }

    private val s2cServerLimitsUpdateWasRejected = "server_limits_update_was_rejected" idWithConns {
        object : S2CConnection<S2CServerLimitsUpdateWasRejected>(it, "server_limits") {
            override fun clientHandler(buf: FriendlyByteBuf, context: NetworkManager.PacketContext) {
                ClientToolGunState.closeGUI()
                ClientToolGunState.addHUDError("Server Limits update was rejected")
            }
        }
    }
}
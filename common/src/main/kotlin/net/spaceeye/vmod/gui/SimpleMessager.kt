package net.spaceeye.vmod.gui

import dev.architectury.event.EventResult
import dev.architectury.event.events.client.ClientRawInputEvent
import dev.architectury.event.events.common.TickEvent
import dev.architectury.networking.NetworkManager
import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.WindowScreen
import gg.essential.elementa.components.ScrollComponent
import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.UIText
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.constraints.ChildBasedSizeConstraint
import gg.essential.elementa.constraints.SiblingConstraint
import gg.essential.elementa.constraints.animation.Animations
import gg.essential.elementa.dsl.*
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.spaceeye.vmod.blockentities.SimpleMessagerBlockEntity
import net.spaceeye.vmod.guiElements.*
import net.spaceeye.vmod.limits.ServerLimits
import net.spaceeye.vmod.network.*
import net.spaceeye.vmod.networking.C2SConnection
import net.spaceeye.vmod.networking.S2CConnection
import net.spaceeye.vmod.networking.Serializable
import net.spaceeye.vmod.toolgun.ClientToolGunState
import net.spaceeye.vmod.translate.*
import net.spaceeye.vmod.utils.ClientClosable
import net.spaceeye.vmod.utils.Vector3d
import net.spaceeye.vmod.utils.vs.posShipToWorld
import org.lwjgl.glfw.GLFW
import org.valkyrienskies.mod.common.getShipManagingPos
import java.awt.Color
import java.util.*

//TODO this is horrible
object SimpleMessagerNetworking {
    class C2SRequestStatePacket(): Serializable {
        lateinit var pos: BlockPos

        constructor(buf: FriendlyByteBuf): this() { deserialize(buf) }
        constructor(pos: BlockPos): this() {
            this.pos = pos
        }

        override fun serialize(): FriendlyByteBuf {
            return getBuffer().writeBlockPos(pos)
        }

        override fun deserialize(buf: FriendlyByteBuf) {
            pos = buf.readBlockPos()
        }

    }

    class S2CRequestStateResponse(): Serializable {
        constructor(buf: FriendlyByteBuf): this() { deserialize(buf) }
        constructor(succeeded: Boolean): this() {this.succeeded = succeeded}
        constructor(succeeded: Boolean, msg: Message, channel: String): this() {
            this.succeeded = succeeded
            this.msg = msg
            this.channel = channel
        }

        var succeeded: Boolean = false
        lateinit var msg: Message
        var channel: String = ""

        override fun serialize(): FriendlyByteBuf {
            val buf = getBuffer()

            buf.writeBoolean(succeeded)
            if (!succeeded) { return buf }

            buf.writeNbt(MessageTypes.serialize(msg))
            buf.writeUtf(channel)

            return buf
        }

        override fun deserialize(buf: FriendlyByteBuf) {
            succeeded = buf.readBoolean()
            if (!succeeded) {return}

            msg = MessageTypes.deserialize(buf.readNbt()!!)
            channel = buf.readUtf()
        }
    }

    class C2SSendStateUpdate(): Serializable {
        constructor(buf: FriendlyByteBuf): this() { deserialize(buf) }
        constructor(msg: Message, channel: String, pos: BlockPos): this() {
            this.msg = msg
            this.channel = channel
            this.pos = pos
        }

        lateinit var msg: Message
        var channel: String = ""
        lateinit var pos: BlockPos

        override fun serialize(): FriendlyByteBuf {
            val buf = getBuffer()

            buf.writeNbt(MessageTypes.serialize(msg))
            buf.writeBlockPos(pos)
            buf.writeUtf(channel)

            return buf
        }

        override fun deserialize(buf: FriendlyByteBuf) {
            msg = MessageTypes.deserialize(buf.readNbt()!!)
            pos = buf.readBlockPos()
            channel = buf.readUtf()
        }

    }

    class S2CStateUpdatedResponse(): Serializable {
        constructor(buf: FriendlyByteBuf): this() { deserialize(buf) }
        constructor(succeeded: Boolean): this() {
            this.succeeded = succeeded
        }

        var succeeded = false

        override fun serialize(): FriendlyByteBuf {
            val buf = getBuffer()

            buf.writeBoolean(succeeded)

            return buf
        }

        override fun deserialize(buf: FriendlyByteBuf) {
            succeeded = buf.readBoolean()
        }

    }

    val c2sRequestState = "request_state" idWithConnc {
        object : C2SConnection<C2SRequestStatePacket>(it, "simple_messager") {
            override fun serverHandler(buf: FriendlyByteBuf, context: NetworkManager.PacketContext) {
                val req = C2SRequestStatePacket(buf)
                val player = context.player as ServerPlayer
                val level = player.serverLevel() as ServerLevel

                var succeeded = (Vector3d(player.position()) - Vector3d(req.pos) + 0.5).sqrDist() <= 64
                if (!succeeded) {
                    val ship = level.getShipManagingPos(req.pos)
                    if (ship != null) {
                        succeeded = (Vector3d(player.position()) - posShipToWorld(ship, Vector3d(req.pos)) + 0.5).sqrDist() <= 64
                    }
                }

                if (!succeeded) {
                    s2cRequestStateResponse.sendToClient(player, S2CRequestStateResponse(false))
                    return
                }

                ordersQueue.add {
                    val be = level.getBlockEntity(req.pos)
                    if (be !is SimpleMessagerBlockEntity) {
                        s2cRequestStateResponse.sendToClient(player, S2CRequestStateResponse(false))
                        return@add
                    }
                    s2cRequestStateResponse.sendToClient(player, S2CRequestStateResponse(true, be.msg, be.channel))
                }
            }
        }
    }
    val s2cRequestStateResponse = "request_state_response" idWithConns {
        object : S2CConnection<S2CRequestStateResponse>(it, "simple_massager") {
            override fun clientHandler(buf: FriendlyByteBuf, context: NetworkManager.PacketContext) {
                val res = S2CRequestStateResponse(buf)

                if (!res.succeeded) {
                    SimpleMessagerGUI.updateNoSuccess()
                    return
                }

                SimpleMessagerGUI.update(res.msg, res.channel)
                SimpleMessagerGUI.open()
            }
        }
    }

    val c2sSendStateUpdate = "send_state_update" idWithConnc {
        object : C2SConnection<C2SSendStateUpdate>(it, "simple_messager") {
            override fun serverHandler(buf: FriendlyByteBuf, context: NetworkManager.PacketContext) {
                val req = C2SSendStateUpdate(buf)

                val player = context.player as ServerPlayer
                val level = player.serverLevel()

                var succeeded = (Vector3d(player.position()) - Vector3d(req.pos) + 0.5).sqrDist() <= 64
                if (!succeeded) {
                    val ship = level.getShipManagingPos(req.pos)
                    if (ship != null) {
                        succeeded = (Vector3d(player.position()) - posShipToWorld(ship, Vector3d(req.pos)) + 0.5).sqrDist() <= 64
                    }
                }

                if (!succeeded) {
                    s2cStateUpdatedResponse.sendToClient(player, S2CStateUpdatedResponse(false))
                    return
                }

                ordersQueue.add {
                    val be = level.getBlockEntity(req.pos)
                    if (be !is SimpleMessagerBlockEntity) {
                        s2cStateUpdatedResponse.sendToClient(player, S2CStateUpdatedResponse(false))
                        return@add
                    }

                    be.msg = req.msg
                    be.channel = ServerLimits.instance.channelLength.get(req.channel)

                    s2cStateUpdatedResponse.sendToClient(player, S2CStateUpdatedResponse(true))
                }
            }
        }
    }
    val s2cStateUpdatedResponse = "state_updated_response" idWithConns {
        object : S2CConnection<S2CStateUpdatedResponse>(it, "simple_messager") {
            override fun clientHandler(buf: FriendlyByteBuf, context: NetworkManager.PacketContext) {
                if (!S2CStateUpdatedResponse(buf).succeeded) {
                    Minecraft.getInstance().setScreen(null)
                }

                SimpleMessagerGUI.updateSuccess()
            }
        }
    }

    val ordersQueue = Collections.synchronizedList<() -> Unit>(mutableListOf())

    init {
        TickEvent.SERVER_PRE.register {
            if (ordersQueue.isEmpty()) { return@register }
            synchronized(ordersQueue) {
                ordersQueue.forEach { it() }
                ordersQueue.clear()
            }
        }
    }

    private infix fun <TT: Serializable> String.idWithConnc(constructor: (String) -> C2SConnection<TT>): C2SConnection<TT> {
        val instance = constructor(this)
        try { // Why? so that if it's registered on dedicated client/server it won't die
            NetworkManager.registerReceiver(instance.side, instance.id, instance.getHandler())
        } catch(e: NoSuchMethodError) {}
        return instance
    }

    private infix fun <TT: Serializable> String.idWithConns(constructor: (String) -> S2CConnection<TT>): S2CConnection<TT> {
        val instance = constructor(this)
        try { // Why? so that if it's registered on dedicated client/server it won't die
            NetworkManager.registerReceiver(instance.side, instance.id, instance.getHandler())
        } catch(e: NoSuchMethodError) {}
        return instance
    }
}

object SimpleMessagerGUI: ClientClosable() {
    private var gui: SimpleMessagerGUIInstance? = null

    override fun close() {
        gui = null
        open = false
    }

    fun get(level: ClientLevel, pos: BlockPos): SimpleMessagerGUIInstance {
        gui = SimpleMessagerGUIInstance(level, pos)
        SimpleMessagerNetworking.c2sRequestState.sendToServer(SimpleMessagerNetworking.C2SRequestStatePacket(pos))
        return gui!!
    }

    fun tryOpen(level: ClientLevel, pos: BlockPos) {
        get(level, pos)
    }

    fun open() {
        open = true
    }

    fun update(msg: Message, channel: String) = gui!!.updateState(msg, channel)
    fun updateSuccess() {gui!!.updateSuccess()}
    fun updateNoSuccess() {gui!!.updateNoSuccess()}

    @Volatile
    var open = false

    init {
        TickEvent.PLAYER_POST.register {
            if (!open) {return@register}
            if (it is ServerPlayer) { return@register }
            Minecraft.getInstance().setScreen(gui)
            gui?.updateGui()
            open = false
        }

        ClientRawInputEvent.KEY_PRESSED.register {
            client, keyCode, scanCode, action, modifiers ->
            if (gui != null && Minecraft.getInstance().screen == gui) {
                if (action == GLFW.GLFW_PRESS && ClientToolGunState.GUI_MENU_OPEN_OR_CLOSE.matches(keyCode, scanCode)) {
                    Minecraft.getInstance().setScreen(null)
                }
            }
            return@register EventResult.pass()
        }
    }
}

class SimpleMessagerGUIInstance(val level: ClientLevel, val pos: BlockPos): WindowScreen(ElementaVersion.V5) {
    var channel = ""

    var msg: Message? = Signal()

    val mainWindow = UIBlock(Color(240, 240, 240)).constrain {
        x = CenterConstraint()
        y = CenterConstraint()

        width = 90f.percent()
        height = 90f.percent()
    } childOf window

    val itemsHolder = ScrollComponent().constrain {
        x = 1f.percent()
        y = 1f.percent()

        width = 98.percent()
        height = 98.percent()
    } childOf mainWindow

    var applyBtn = Button(Color(120, 120, 120), APPLY_CHANGES.get()) {
        if (msg == null) {return@Button}
        SimpleMessagerNetworking.c2sSendStateUpdate.sendToServer(
            SimpleMessagerNetworking.C2SSendStateUpdate(
            msg!!, channel, pos
        ))
    }.constrain {
        width = 98.percent()
        height = ChildBasedSizeConstraint() + 4.pixels()

        x = 2.pixels()
        y = 2.pixels()
    } childOf itemsHolder

    var entry: TextEntry

    init {
        UIText("Press TAB to close", shadow = false) constrain {x = CenterConstraint(); y = SiblingConstraint() + 2f.pixels; color = Color.BLACK.toConstraint()} childOf itemsHolder
        entry = makeTextEntry(CHANNEL.get(), ::channel, 2f, 2f, itemsHolder, ServerLimits.instance.channelLength)
        msg = Signal()
    }

    fun updateState(msg: Message, channel: String) {
        this.msg = msg
        this.channel = channel
    }

    fun updateGui() {
        itemsHolder.removeChild(entry)

        entry = makeTextEntry(CHANNEL.get(), ::channel, 2f, 2f, itemsHolder, ServerLimits.instance.channelLength)
    }

    fun updateSuccess() {
        applyBtn.animate {
            setColorAnimation(
                Animations.OUT_EXP,
                applyBtn.animationTime,
                Color(0, 170, 0).toConstraint()
            )
            setColorAnimation(
                Animations.OUT_EXP,
                applyBtn.animationTime,
                Color(150, 150, 150).toConstraint()
            )
        }
    }
    fun updateNoSuccess() {
        applyBtn.animate {
            setColorAnimation(
                Animations.OUT_EXP,
                applyBtn.animationTime,
                Color(170, 0, 0).toConstraint()
            )
        }
    }
}
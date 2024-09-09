package net.spaceeye.vmod.toolgun.serverSettings.modes

import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.components.UIText
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.constraints.ChildBasedSizeConstraint
import gg.essential.elementa.constraints.FillConstraint
import gg.essential.elementa.constraints.SiblingConstraint
import gg.essential.elementa.dsl.*
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.TranslatableComponent
import net.spaceeye.vmod.guiElements.DItem
import net.spaceeye.vmod.guiElements.makeDropDown
import net.spaceeye.vmod.networking.Serializable
import net.spaceeye.vmod.networking.regC2S
import net.spaceeye.vmod.networking.regS2C
import net.spaceeye.vmod.toolgun.PlayerAccessManager
import net.spaceeye.vmod.toolgun.PlayersRolesData
import net.spaceeye.vmod.toolgun.serverSettings.ServerSettingsGUIBuilder
import net.spaceeye.vmod.utils.EmptyPacket
import java.awt.Color
import java.util.UUID

class PlayerRoleManager: ServerSettingsGUIBuilder {
    override val itemName: TranslatableComponent
        get() = TranslatableComponent("Player Role Manager")

    override fun makeGUISettings(parentWindow: UIContainer) {
        callback = { data ->
            data.playersRoles.forEach { (_, item) ->

                val ctn = UIContainer() constrain {
                    x = 2.pixels
                    y = SiblingConstraint(2f)

                    width = 100.percent - 2.pixels
                    height = ChildBasedSizeConstraint()
                }

                val text = UIText("${item.nickname} ${item.role}", false) constrain {
                    x = 2.pixels
                    y = CenterConstraint()

                    color = Color.BLACK.toConstraint()
                } childOf ctn

                val change = makeDropDown("Roles", ctn, 2f, 2f, data.allRoles.map {
                    DItem(it, it == item.role) {
                        val pkt = C2SChangePlayerRole()
                        pkt.newRole = it
                        pkt.uuid = item.uuid

                        callback = { data ->
                            text.setText("${item.nickname} ${data.playersRoles[item.uuid]?.role}")
                        }

                        c2sChangePlayerRole.sendToServer(pkt)
                    }
                }) constrain {
                    width = FillConstraint()
                    x = SiblingConstraint(2f)
                    y = CenterConstraint()
                }

                ctn childOf parentWindow
            }
        }
        c2sRequestPlayersRolesData.sendToServer(EmptyPacket())
    }

    companion object {
        var callback: ((PlayersRolesData) -> Unit)? = null
        val c2sRequestPlayersRolesData = regC2S<EmptyPacket>("request_players_roles_data", "player_role_manager") {pkt, player ->
            s2cSendPlayersRolesData.sendToClient(player, PlayersRolesData())
        }
        val s2cSendPlayersRolesData = regS2C<PlayersRolesData>("send_players_roles_data", "player_role_manager") {pkt ->
            callback!!(pkt)
            callback = null
        }

        val c2sChangePlayerRole = regC2S<C2SChangePlayerRole>("try_change_player_role", "player_role_manager",
            {player -> player.hasPermissions(4)}) {pkt, player ->
            PlayerAccessManager.setPlayerRole(pkt.uuid, pkt.newRole)
            s2cSendPlayersRolesData.sendToClient(player, PlayersRolesData())
        }

        class C2SChangePlayerRole(): Serializable {
            lateinit var newRole: String
            lateinit var uuid: UUID

            override fun serialize(): FriendlyByteBuf {
                val buf = getBuffer()
                buf.writeUtf(newRole)
                buf.writeUUID(uuid)
                return buf
            }

            override fun deserialize(buf: FriendlyByteBuf) {
                newRole = buf.readUtf()
                uuid = buf.readUUID()
            }
        }
    }
}
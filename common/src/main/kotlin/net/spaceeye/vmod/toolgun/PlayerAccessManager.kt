package net.spaceeye.vmod.toolgun

import dev.architectury.event.events.common.PlayerEvent
import io.netty.buffer.Unpooled
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import net.spaceeye.vmod.networking.Serializable
import java.util.UUID

data class PlayerAccessState(val uuid: UUID, val nickname: String, var role: String)

class RolePermissionsData(): Serializable {
    var allRoles = mutableListOf<String>()
    var allPermissionsList = mutableListOf<String>()
    var rolesPermissions = mutableMapOf<String, MutableSet<String>>()

    override fun serialize(): FriendlyByteBuf {
        val buf = FriendlyByteBuf(Unpooled.buffer(512))

        synchronized(PlayerAccessManager) {
            buf.writeCollection(PlayerAccessManager.allRoles) { buf, it -> buf.writeUtf(it) }
            buf.writeCollection(PlayerAccessManager.allPermissionsList) { buf, it -> buf.writeUtf(it) }
            val schema = PlayerAccessManager.allPermissionsList.mapIndexed { i, item -> Pair(item, i) }.associate { it }

            buf.writeCollection(PlayerAccessManager.rolesPermissions.toList()) { buf, it ->
                buf.writeUtf(it.first)
                //TODO not optimal but do i care about it?
                buf.writeCollection(it.second) { buf, it -> buf.writeVarInt(schema[it]!!) }
            }
        }
        return buf
    }

    override fun deserialize(buf: FriendlyByteBuf) {
        allRoles = buf.readCollection({ mutableListOf() }) {buf -> buf.readUtf()}
        allPermissionsList = buf.readCollection({ mutableListOf() }) {buf.readUtf()}
        rolesPermissions = (buf.readCollection({mutableListOf<Pair<String, MutableSet<String>>>()}) {
                buf ->Pair(
            buf.readUtf(),
            buf.readCollection({ mutableSetOf() }) {buf -> allPermissionsList[buf.readVarInt()]}
        )
        }).associate { it }.toMutableMap()
    }
}

class PlayersRolesData(): Serializable {
    var playersRoles = mutableMapOf<UUID, PlayerAccessState>()
    var allRoles = mutableListOf<String>()

    override fun serialize(): FriendlyByteBuf {
        val buf = FriendlyByteBuf(Unpooled.buffer(512))

        synchronized(PlayerAccessManager) {
            buf.writeCollection(PlayerAccessManager.playersRoles.toList()) { buf, it -> buf.writeUUID(it.first); buf.writeUtf(it.second.nickname); buf.writeUtf(it.second.role)}
            buf.writeCollection(PlayerAccessManager.allRoles) { buf, it -> buf.writeUtf(it) }
        }

        return buf
    }

    override fun deserialize(buf: FriendlyByteBuf) {
        playersRoles = buf.readCollection({ mutableListOf<Pair<UUID, PlayerAccessState>>()}) { buf ->
            val uuid = buf.readUUID()
            Pair(uuid, PlayerAccessState(uuid, buf.readUtf(), buf.readUtf()))
        }.associate { it }.toMutableMap()
        allRoles = buf.readCollection({ mutableListOf() }) {buf -> buf.readUtf()}
    }
}

object PlayerAccessManager {
    var allPermissionsList = mutableListOf<String>()
    var rolesPermissions = mutableMapOf<String, MutableSet<String>>()
    var playersRoles = mutableMapOf<UUID, PlayerAccessState>()

    val allPermissions = mutableSetOf<String>()
    val allRoles = mutableListOf<String>()
    const val defaultRoleName = "default"

    init {
        PlayerEvent.PLAYER_JOIN.register { getPlayerState(it) }
    }


    @Synchronized fun afterInit() {
        addRole(defaultRoleName)
    }

    @Synchronized private fun getPlayerState(player: ServerPlayer): PlayerAccessState =
        playersRoles.getOrPut(player.uuid) {
            PlayerAccessState(player.uuid, player.gameProfile.name, defaultRoleName)
        }

    @Synchronized fun hasPermission(player: ServerPlayer, permission: String): Boolean {
        val state = getPlayerState(player)
        val permissions = rolesPermissions[state.role] ?: return false
        return permissions.contains(permission)
    }

    @Synchronized fun setPlayerRole(player: ServerPlayer, role: String): Boolean {
        if (!rolesPermissions.containsKey(role)) {return false}
        val state = getPlayerState(player)
        state.role = role
        return true
    }

    @Synchronized fun addRole(role: String) {
        if (rolesPermissions.containsKey(role)) { return }
        val permissions = mutableSetOf<String>();
        permissions.addAll(allPermissions)
        rolesPermissions[role] = permissions
        allRoles.add(role)
    }

    @Synchronized fun setRole(role: String, permissions: MutableSet<String>) {
        rolesPermissions[role] = permissions
    }

    @Synchronized fun addPermission(permission: String) {
        if (allPermissions.contains(permission)) { throw AssertionError("Permission already exists") }
        allPermissions.add(permission)
        allPermissionsList.add(permission)
    }

    @Synchronized fun addPermissionToRole(role: String, permission: String) {
        if (!allPermissions.contains(permission)) { throw AssertionError("Permission wasn't registered") }
        rolesPermissions[role]?.add(permission) ?: throw AssertionError("Role doesn't exist")
    }

    @Synchronized fun removePermissionFromRole(role: String, permission: String) {
        if (!allPermissions.contains(permission)) { throw AssertionError("Permission wasn't registered") }
        rolesPermissions[role]?.remove(permission) ?: throw AssertionError("Role doesn't exist")
    }

    @Synchronized fun save() {
        
    }

    @Synchronized fun load() {

    }
}
package net.spaceeye.vmod

import net.spaceeye.vmod.config.*

object VMConfig {
    lateinit var server_config_holder: AbstractConfigBuilder
    lateinit var client_config_holder: AbstractConfigBuilder
    lateinit var common_config_holder: AbstractConfigBuilder

    val SERVER = Server()
    val CLIENT = Client()
    val COMMON = Common()

    class Client: ConfigSubDirectory() {
        val TOOLGUN = ClientToolgunSettings()
        val RENDERING = ClientRenderingSettings()

        class ClientToolgunSettings: ConfigSubDirectory() {
            var MAX_RAYCAST_DISTANCE: Double by CDouble(100.0, "", Pair(1.0, Double.MAX_VALUE))

            val SCHEMATIC_PACKET_PART_SIZE: Int by CInt(30000, "Reload the game for change to take the effect.", Pair(512, 1000000))
        }

        class ClientRenderingSettings: ConfigSubDirectory() {
            val MAX_RENDERING_DISTANCE: Double by CDouble(200.0, "Max distance in blocks some renderers are able to render. Reload the game for change to take the effect.", Pair(1.0, Double.MAX_VALUE))
        }
    }
    class Common: ConfigSubDirectory()
    class Server: ConfigSubDirectory() {
        val AUTOREMOVE_MASSLESS_SHIPS: Boolean by CBool(true, "MAY BREAK SOMETHING. if true, will automatically remove massless ships if they appear.")

        val TOOLGUN = ServerToolgunSettings()
        val PERMISSIONS = Permissions()
        val SCHEMATICS = Schematics()

        class ServerToolgunSettings: ConfigSubDirectory() {
            val MAX_RAYCAST_DISTANCE: Double by CDouble(100.0, "", Pair(1.0, Double.MAX_VALUE))

            //TODO use this
            val MAX_SHIPS_ALLOWED_TO_COPY: Int by CInt(-1, "Number of connected ships a player can copy in one request. <=0 for unlimited.")

            val SCHEMATIC_PACKET_PART_SIZE: Int by CInt(30000, "Reload the game for change to take the effect.", Pair(512, 1000000))
        }

        class Permissions: ConfigSubDirectory() {
            var VMOD_COMMANDS_PERMISSION_LEVEL: Int by CInt(2, "", Pair(0, 4))
            var VMOD_OP_COMMANDS_PERMISSION_LEVEL: Int by CInt(4, "", Pair(0, 4))
            var VMOD_TOOLGUN_PERMISSION_LEVEL: Int by CInt(2, "", Pair(0, 4))

            //using config to store shit
            var ALWAYS_ALLOWED: String by CString("", "DO NOT CHANGE")
            var ALWAYS_DISALLOWED: String by CString("", "DO NOT CHANGE")
        }

        class Schematics: ConfigSubDirectory() {
            var TIMEOUT_TIME: Int by CInt(50, "", Pair(0, 20000))
        }
    }
}
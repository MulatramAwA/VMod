package net.spaceeye.vmod

import net.spaceeye.vmod.config.AbstractConfigBuilder
import net.spaceeye.vmod.config.CDouble
import net.spaceeye.vmod.config.CInt
import net.spaceeye.vmod.config.ConfigSubDirectory

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
        }

        class ClientRenderingSettings: ConfigSubDirectory() {
            val MAX_RENDERING_DISTANCE: Double by CDouble(200.0, "Max distance in blocks some renderers are able to render. Reload the game for change to take the effect.", Pair(1.0, Double.MAX_VALUE))
        }
    }
    class Common: ConfigSubDirectory()
    class Server: ConfigSubDirectory() {
        val TOOLGUN = ServerToolgunSettings()

        class ServerToolgunSettings: ConfigSubDirectory() {
            val MAX_RAYCAST_DISTANCE: Double by CDouble(100.0, "", Pair(1.0, Double.MAX_VALUE))

            val MAX_SHIPS_ALLOWED_TO_COPY: Int by CInt(-1, "Number of connected ships a player can copy in one request. <=0 for unlimited.")
        }
    }
}
package net.spaceeye.vmod.toolgun.modes.util

import gg.essential.elementa.components.UIContainer
import net.minecraft.network.FriendlyByteBuf
import net.spaceeye.vmod.guiElements.DItem
import net.spaceeye.vmod.guiElements.makeDropDown
import net.spaceeye.vmod.guiElements.makeTextEntry
import net.spaceeye.vmod.limits.ServerLimits
import net.spaceeye.vmod.rendering.SynchronisedRenderingData
import net.spaceeye.vmod.rendering.types.special.PrecisePlacementAssistRenderer
import net.spaceeye.vmod.translate.*

interface PlacementModesState {
    var posMode: PositionModes
    var precisePlacementAssistSideNum: Int
    var precisePlacementAssistRendererId: Int
}

interface PlacementModesCEH: PlacementModesState {
    fun pmOnOpen() {
        if (posMode != PositionModes.PRECISE_PLACEMENT) {return}
        precisePlacementAssistRendererId = SynchronisedRenderingData.clientSynchronisedData.addClientsideRenderer(PrecisePlacementAssistRenderer(precisePlacementAssistSideNum))
    }

    fun pmOnClose() {
        SynchronisedRenderingData.clientSynchronisedData.removeClientsideRenderer(precisePlacementAssistRendererId)
    }
}

interface PlacementModesGUI: PlacementModesState {
    private var internalPrecisePlacementAssistSideNum: Int
        get() = precisePlacementAssistSideNum
        set(value) {
            precisePlacementAssistSideNum = value
            if (posMode != PositionModes.PRECISE_PLACEMENT) {return}
            SynchronisedRenderingData.clientSynchronisedData.removeClientsideRenderer(precisePlacementAssistRendererId)
            precisePlacementAssistRendererId = SynchronisedRenderingData.clientSynchronisedData.addClientsideRenderer(PrecisePlacementAssistRenderer(precisePlacementAssistSideNum))
        }

    fun pmMakePlacementModesGUIPart(parentWindow: UIContainer, offset: Float) {
        makeTextEntry("Precise Placement Assist Sides", ::internalPrecisePlacementAssistSideNum, offset, offset, parentWindow, ServerLimits.instance.precisePlacementAssistSides)
        makeDropDown(HITPOS_MODES.get(), parentWindow, offset, offset, listOf(
            DItem(NORMAL.get(),            posMode == PositionModes.NORMAL)            { posMode = PositionModes.NORMAL           ; SynchronisedRenderingData.clientSynchronisedData.removeClientsideRenderer(precisePlacementAssistRendererId) },
            DItem(CENTERED_ON_SIDE.get(),  posMode == PositionModes.CENTERED_ON_SIDE)  { posMode = PositionModes.CENTERED_ON_SIDE ; SynchronisedRenderingData.clientSynchronisedData.removeClientsideRenderer(precisePlacementAssistRendererId) },
            DItem(CENTERED_IN_BLOCK.get(), posMode == PositionModes.CENTERED_IN_BLOCK) { posMode = PositionModes.CENTERED_IN_BLOCK; SynchronisedRenderingData.clientSynchronisedData.removeClientsideRenderer(precisePlacementAssistRendererId) },
            DItem("Precise Placement",     posMode == PositionModes.PRECISE_PLACEMENT) {
                posMode = PositionModes.PRECISE_PLACEMENT
                SynchronisedRenderingData.clientSynchronisedData.removeClientsideRenderer(precisePlacementAssistRendererId)
                precisePlacementAssistRendererId = SynchronisedRenderingData.clientSynchronisedData.addClientsideRenderer(PrecisePlacementAssistRenderer(precisePlacementAssistSideNum))
            })
        )
    }

    fun pmMakePlacementModesNoCenteredInBlockGUIPart(parentWindow: UIContainer, offset: Float) {
        makeTextEntry("Precise Placement Assist Sides", ::internalPrecisePlacementAssistSideNum, offset, offset, parentWindow, ServerLimits.instance.precisePlacementAssistSides)
        makeDropDown(HITPOS_MODES.get(), parentWindow, offset, offset, listOf(
            DItem(NORMAL.get(),            posMode == PositionModes.NORMAL)            { posMode = PositionModes.NORMAL           ; SynchronisedRenderingData.clientSynchronisedData.removeClientsideRenderer(precisePlacementAssistRendererId) },
            DItem(CENTERED_ON_SIDE.get(),  posMode == PositionModes.CENTERED_ON_SIDE)  { posMode = PositionModes.CENTERED_ON_SIDE ; SynchronisedRenderingData.clientSynchronisedData.removeClientsideRenderer(precisePlacementAssistRendererId) },
            DItem("Precise Placement",     posMode == PositionModes.PRECISE_PLACEMENT) {
                posMode = PositionModes.PRECISE_PLACEMENT
                SynchronisedRenderingData.clientSynchronisedData.removeClientsideRenderer(precisePlacementAssistRendererId)
                precisePlacementAssistRendererId = SynchronisedRenderingData.clientSynchronisedData.addClientsideRenderer(PrecisePlacementAssistRenderer(precisePlacementAssistSideNum))
            })
        )
    }
}

interface PlacementModesSerializable: PlacementModesState {
    fun pmSerialize(buf: FriendlyByteBuf) {
        buf.writeEnum(posMode)
        buf.writeInt(precisePlacementAssistSideNum)
    }

    fun pmDeserialize(buf: FriendlyByteBuf) {
        posMode = buf.readEnum(posMode.javaClass)
        precisePlacementAssistSideNum = buf.readInt()
    }

    fun pmServerSideVerifyLimits() {
        precisePlacementAssistSideNum = ServerLimits.instance.precisePlacementAssistSides.get(precisePlacementAssistSideNum)
    }
}
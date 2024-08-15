package net.spaceeye.vmod.constraintsManaging.util

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.spaceeye.vmod.constraintsManaging.MExtensionTypes
import net.spaceeye.vmod.utils.RegistryObject
import net.spaceeye.vmod.utils.Vector3d
import org.valkyrienskies.core.api.ships.properties.ShipId

interface MConstraintExtension: RegistryObject {
    fun onInit(obj: ExtendableMConstraint)

    fun onAfterMoveShipyardPositions(level: ServerLevel, previous: BlockPos, new: BlockPos, newShipId: ShipId) {TODO()}

    fun onBeforeCopyMConstraint(level: ServerLevel, mapped: Map<ShipId, ShipId>) {}
    //should add new extension to new MConstraint
    fun onAfterCopyMConstraint(level: ServerLevel, mapped: Map<ShipId, ShipId>, new: ExtendableMConstraint) {}

    fun onBeforeOnScaleByMConstraint(level: ServerLevel, scaleBy: Double, scalingCenter: Vector3d) {}

    // will be called after MConstraint is serialized
    fun onSerialize(): CompoundTag?
    // will be called after MConstraint is deserialized
    // if returns true then everything is ok
    // if returns false then it failed to deserialize and extension will not be added
    fun onDeserialize(tag: CompoundTag, lastDimensionIds: Map<ShipId, String>): Boolean

    fun onMakeMConstraint(level: ServerLevel)

    fun onDeleteMConstraint(level: ServerLevel)

    companion object {
        fun fromType(type: String) = MExtensionTypes.typeToSupplier(type).get()
    }
}
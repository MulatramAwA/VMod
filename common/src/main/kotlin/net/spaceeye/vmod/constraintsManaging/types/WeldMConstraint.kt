package net.spaceeye.vmod.constraintsManaging.types

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.spaceeye.vmod.constraintsManaging.*
import net.spaceeye.vmod.utils.vs.VSConstraintDeserializationUtil.deserializeConstraint
import net.spaceeye.vmod.utils.vs.VSConstraintDeserializationUtil.tryConvertDimensionId
import net.spaceeye.vmod.rendering.SynchronisedRenderingData
import net.spaceeye.vmod.rendering.types.BaseRenderer
import net.spaceeye.vmod.utils.*
import net.spaceeye.vmod.utils.vs.VSConstraintSerializationUtil
import net.spaceeye.vmod.utils.vs.posShipToWorld
import net.spaceeye.vmod.utils.vs.posWorldToShip
import org.joml.Quaterniond
import org.valkyrienskies.core.api.ships.QueryableShipData
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.core.api.ships.properties.ShipId
import org.valkyrienskies.core.apigame.constraints.*
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.physics_api.ConstraintId

class WeldMConstraint(): MConstraint, MRenderable {
    lateinit var aconstraint1: VSAttachmentConstraint
    lateinit var aconstraint2: VSAttachmentConstraint
    lateinit var rconstraint1: VSTorqueConstraint

    var attachmentPoints_ = mutableListOf<BlockPos>()

    val cIDs = mutableListOf<ConstraintId>()

    override var renderer: BaseRenderer? = null

    constructor(
        // shipyard pos
        spoint1: Vector3d,
        spoint2: Vector3d,
        // world pos
        rpoint1: Vector3d,
        rpoint2: Vector3d,
        ship1: Ship?,
        ship2: Ship?,
        shipId0: ShipId,
        shipId1: ShipId,
        compliance: Double,
        maxForce: Double,
        fixedLength: Double = -1.0,
        attachmentPoints: List<BlockPos>,
        renderer: BaseRenderer?
    ): this() {
        aconstraint1 = VSAttachmentConstraint(
            shipId0, shipId1,
            compliance,
            spoint1.toJomlVector3d(), spoint2.toJomlVector3d(),
            maxForce, if (fixedLength < 0) (rpoint1 - rpoint2).dist() else fixedLength)

        val dist1 = rpoint1 - rpoint2
        val len = dist1.dist()
        val dir = dist1.normalize() * ( if (len < 10 || len > 30) 20 else 40)

        val rpoint1 = rpoint1 + dir
        val rpoint2 = rpoint2 - dir

        val dist2 = rpoint1 - rpoint2
        val addDist = dist2.dist() - dist1.dist()

        val spoint1 = if (ship1 != null) posWorldToShip(ship1, rpoint1) else Vector3d(rpoint1)
        val spoint2 = if (ship2 != null) posWorldToShip(ship2, rpoint2) else Vector3d(rpoint2)

        aconstraint2 = VSAttachmentConstraint(
            shipId0, shipId1,
            compliance,
            spoint1.toJomlVector3d(), spoint2.toJomlVector3d(),
            maxForce, if (fixedLength < 0) (rpoint1 - rpoint2).dist() else fixedLength + addDist
        )

        val rot1 = ship1?.transform?.shipToWorldRotation ?: Quaterniond()
        val rot2 = ship2?.transform?.shipToWorldRotation ?: Quaterniond()

        rconstraint1 = VSFixedOrientationConstraint(shipId0, shipId1, compliance, rot1.invert(Quaterniond()), rot2.invert(Quaterniond()), 1e300)

        this.renderer = renderer
        attachmentPoints_ = attachmentPoints.toMutableList()
    }

    override var mID: ManagedConstraintId = -1
    override val typeName: String get() = "WeldMConstraint"
    override var saveCounter: Int = -1

    override fun stillExists(allShips: QueryableShipData<Ship>, dimensionIds: Collection<ShipId>): Boolean {
        val ship1Exists = allShips.contains(aconstraint1.shipId0)
        val ship2Exists = allShips.contains(aconstraint1.shipId1)

        return     (ship1Exists && ship2Exists)
                || (ship1Exists && dimensionIds.contains(aconstraint1.shipId1))
                || (ship2Exists && dimensionIds.contains(aconstraint1.shipId0))
    }

    override fun attachedToShips(dimensionIds: Collection<ShipId>): List<ShipId> {
        val toReturn = mutableListOf<ShipId>()

        if (!dimensionIds.contains(aconstraint1.shipId0)) {toReturn.add(aconstraint1.shipId0)}
        if (!dimensionIds.contains(aconstraint1.shipId1)) {toReturn.add(aconstraint1.shipId1)}

        return toReturn
    }

    override fun getAttachmentPoints(): List<BlockPos> = attachmentPoints_
    override fun moveShipyardPosition(level: ServerLevel, previous: BlockPos, new: BlockPos, newShipId: ShipId) {
        if (previous != attachmentPoints_[0] && previous != attachmentPoints_[1]) {return}
        cIDs.forEach { level.shipObjectWorld.removeConstraint(it) }
        cIDs.clear()

        val shipIds = mutableListOf(aconstraint1.shipId0, aconstraint1.shipId1)
        val localPoints = mutableListOf(
            listOf(aconstraint1.localPos0, aconstraint2.localPos0),
            listOf(aconstraint1.localPos1, aconstraint2.localPos1)
        )
        updatePositions(newShipId, previous, new, attachmentPoints_, shipIds, localPoints)

        aconstraint1 = VSAttachmentConstraint(shipIds[0], shipIds[1], aconstraint1.compliance, localPoints[0][0], localPoints[1][0], aconstraint1.maxForce, aconstraint1.fixedDistance)
        aconstraint2 = VSAttachmentConstraint(shipIds[0], shipIds[1], aconstraint2.compliance, localPoints[0][1], localPoints[1][1], aconstraint2.maxForce, aconstraint2.fixedDistance)
        rconstraint1 = VSFixedOrientationConstraint(shipIds[0], shipIds[1], rconstraint1.compliance, rconstraint1.localRot0, rconstraint1.localRot1, rconstraint1.maxTorque)

        cIDs.add(level.shipObjectWorld.createNewConstraint(aconstraint1)!!)
        cIDs.add(level.shipObjectWorld.createNewConstraint(aconstraint2)!!)
        cIDs.add(level.shipObjectWorld.createNewConstraint(rconstraint1)!!)

        renderer = updateRenderer(localPoints[0][0], localPoints[1][0], shipIds[0], shipIds[1], mID)

        renderer = SynchronisedRenderingData.serverSynchronisedData.getRenderer(mID)
    }

    override fun copyMConstraint(level: ServerLevel, mapped: Map<ShipId, ShipId>): MConstraint? {
        return commonCopy(level, mapped, aconstraint1, attachmentPoints_, renderer) {
            nShip1Id, nShip2Id, nShip1, nShip2, localPos0, localPos1, newAttachmentPoints, newRenderer ->

            val rpoint1 = if (nShip1 != null) { posShipToWorld(nShip1, localPos0) } else localPos0
            val rpoint2 = if (nShip2 != null) { posShipToWorld(nShip2, localPos1) } else localPos1

            val con = WeldMConstraint(localPos0, localPos1, rpoint1, rpoint2, nShip1, nShip2, nShip1Id, nShip2Id, aconstraint1.compliance, aconstraint1.maxForce, aconstraint1.fixedDistance, newAttachmentPoints, newRenderer)
            con
        }
    }

    override fun onScaleBy(level: ServerLevel, scaleBy: Double) {
        aconstraint1 = VSAttachmentConstraint(aconstraint1.shipId0, aconstraint1.shipId1, aconstraint1.compliance, aconstraint1.localPos0, aconstraint1.localPos1, aconstraint1.maxForce, aconstraint1.fixedDistance * scaleBy)
        aconstraint2 = VSAttachmentConstraint(aconstraint2.shipId0, aconstraint2.shipId1, aconstraint2.compliance, aconstraint2.localPos0, aconstraint2.localPos1, aconstraint2.maxForce, aconstraint2.fixedDistance * scaleBy)

        level.shipObjectWorld.removeConstraint(cIDs[0])
        level.shipObjectWorld.removeConstraint(cIDs[1])

        cIDs[0] = level.shipObjectWorld.createNewConstraint(aconstraint1)!!
        cIDs[1] = level.shipObjectWorld.createNewConstraint(aconstraint2)!!
    }

    override fun getVSIds(): Set<VSConstraintId> {
        return cIDs.toSet()
    }

    override fun nbtSerialize(): CompoundTag? {
        val tag = CompoundTag()

        tag.put("c1", VSConstraintSerializationUtil.serializeConstraint(aconstraint1) ?: return null)
        tag.put("c2", VSConstraintSerializationUtil.serializeConstraint(aconstraint2) ?: return null)
        tag.put("c3", VSConstraintSerializationUtil.serializeConstraint(rconstraint1) ?: return null)

        tag.putInt("managedID", mID)
        tag.put("attachmentPoints", serializeBlockPositions(attachmentPoints_))

        serializeRenderer(tag)

        return tag
    }

    override fun nbtDeserialize(tag: CompoundTag, lastDimensionIds: Map<ShipId, String>): MConstraint? {
        mID = tag.getInt("managedID")
        attachmentPoints_ = deserializeBlockPositions(tag.get("attachmentPoints")!!)
        deserializeRenderer(tag)

        tryConvertDimensionId(tag["c1"] as CompoundTag, lastDimensionIds); aconstraint1 = (deserializeConstraint(tag["c1"] as CompoundTag) ?: return null) as VSAttachmentConstraint
        tryConvertDimensionId(tag["c2"] as CompoundTag, lastDimensionIds); aconstraint2 = (deserializeConstraint(tag["c2"] as CompoundTag) ?: return null) as VSAttachmentConstraint
        tryConvertDimensionId(tag["c3"] as CompoundTag, lastDimensionIds); rconstraint1 = (deserializeConstraint(tag["c3"] as CompoundTag) ?: return null) as VSTorqueConstraint
        return this
    }

    private fun <T> clean(level: ServerLevel): T? {
        cIDs.forEach { level.shipObjectWorld.removeConstraint(it) }
        return null
    }

    override fun onMakeMConstraint(level: ServerLevel): Boolean {
        cIDs.add(level.shipObjectWorld.createNewConstraint(aconstraint1) ?: clean(level) ?: return false)
        cIDs.add(level.shipObjectWorld.createNewConstraint(aconstraint2) ?: clean(level) ?: return false)
        cIDs.add(level.shipObjectWorld.createNewConstraint(rconstraint1) ?: clean(level) ?: return false)

        if (renderer != null) { SynchronisedRenderingData.serverSynchronisedData.addRenderer(aconstraint1.shipId0, aconstraint1.shipId1, mID, renderer!!)
        } else { renderer = SynchronisedRenderingData.serverSynchronisedData.getRenderer(mID) }
        return true
    }

    override fun onDeleteMConstraint(level: ServerLevel) {
        cIDs.forEach { level.shipObjectWorld.removeConstraint(it) }
        SynchronisedRenderingData.serverSynchronisedData.removeRenderer(mID)
    }
}
package net.spaceeye.vmod.constraintsManaging

import net.spaceeye.vmod.constraintsManaging.types.*
import net.spaceeye.vmod.utils.Registry

object MConstraintTypes: Registry<MConstraint>() {
    init {
        register(::BasicMConstraint)
        register(::RopeMConstraint)
        register(::WeldMConstraint)
        register(::HydraulicsMConstraint)
        register(::AxisMConstraint)
        register(::PhysRopeMConstraint)
    }
}
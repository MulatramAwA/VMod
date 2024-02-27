package net.spaceeye.vsource.constraintsSaving.types

import java.util.function.Supplier

object MConstraintTypes {
    private val strToIdx = mutableMapOf<String, Int>()
    private val suppliers = mutableListOf<Supplier<MConstraint>>()

    init {
        register { BasicMConstraint() }
    }

    private fun register(supplier: Supplier<MConstraint>) {
        suppliers.add(supplier)
        strToIdx[supplier.get().typeName] = suppliers.size - 1
    }

    fun typeToIdx(type: String) = strToIdx[type]
    fun idxToSupplier(idx: Int) = suppliers[idx]
}
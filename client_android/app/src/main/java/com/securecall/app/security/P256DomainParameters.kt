package com.securecall.app.security

import java.math.BigInteger
import java.security.spec.ECFieldFp
import java.security.spec.ECParameterSpec

/** Provider-independent validation for the NIST P-256 domain parameters. */
internal object P256DomainParameters {
    private fun hex(value: String) = BigInteger(value, 16)

    private val prime = hex("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF")
    private val coefficientA = hex("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFC")
    private val coefficientB = hex("5AC635D8AA3A93E7B3EBBD55769886BC651D06B0CC53B0F63BCE3C3E27D2604B")
    private val generatorX = hex("6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296")
    private val generatorY = hex("4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5")
    private val order = hex("FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551")

    fun matches(parameters: ECParameterSpec?): Boolean {
        val value = parameters ?: return false
        val field = value.curve.field as? ECFieldFp ?: return false
        return field.p == prime
            && value.curve.a == coefficientA
            && value.curve.b == coefficientB
            && value.generator.affineX == generatorX
            && value.generator.affineY == generatorY
            && value.order == order
            && value.cofactor == 1
    }
}

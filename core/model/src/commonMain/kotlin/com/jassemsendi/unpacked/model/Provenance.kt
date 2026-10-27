package com.jassemsendi.unpacked.model

/** Where one Event or Claim came from: the Export, the file inside it and the record's position in that file. */
public data class Provenance(
    val exportId: ExportId,
    val path: String,
    val recordIndex: Int,
) {
    /** A stable 64-bit identity for this location, for deduplication in later stages. */
    val hash: Long get() = fnv1a64("${exportId.value}\u0000$path\u0000$recordIndex".encodeToByteArray())
}

private const val FNV_OFFSET_BASIS: Long = -3750763034362895579L
private const val FNV_PRIME: Long = 0x100000001b3L
private const val BYTE_MASK: Long = 0xff

/** FNV-1a, 64-bit. */
public fun fnv1a64(bytes: ByteArray): Long {
    var hash = FNV_OFFSET_BASIS
    for (byte in bytes) {
        hash = hash xor (byte.toLong() and BYTE_MASK)
        hash *= FNV_PRIME
    }
    return hash
}

package com.jassemsendi.unpacked.ui.settings

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryCreate
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * The Keychain through the Security framework: generic passwords under the app's service, kept on this device only
 * and readable once it has been unlocked after a start. Each write replaces the item, so there is never more than
 * one per account.
 */
@OptIn(ExperimentalForeignApi::class)
internal class SecurityKeychain(
    private val service: String = SERVICE,
) : Keychain {
    override fun read(account: String): String? =
        memScoped {
            retaining(service, account) { cfService, cfAccount ->
                val query =
                    dictionaryOf(
                        kSecClass to kSecClassGenericPassword,
                        kSecAttrService to cfService,
                        kSecAttrAccount to cfAccount,
                        kSecReturnData to kCFBooleanTrue,
                        kSecMatchLimit to kSecMatchLimitOne,
                    )
                val found = alloc<CFTypeRefVar>()
                val status = SecItemCopyMatching(query, found.ptr)
                CFRelease(query)
                when (status) {
                    errSecSuccess -> (CFBridgingRelease(found.value) as NSData).text()
                    errSecItemNotFound -> null
                    else -> throw KeychainException(status)
                }
            }
        }

    override fun write(
        account: String,
        value: String,
    ) {
        delete(account)
        memScoped {
            retaining(service, account) { cfService, cfAccount ->
                val data = CFBridgingRetain(value.data())
                val item =
                    dictionaryOf(
                        kSecClass to kSecClassGenericPassword,
                        kSecAttrService to cfService,
                        kSecAttrAccount to cfAccount,
                        kSecValueData to data,
                        kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                    )
                val status = SecItemAdd(item, null)
                CFRelease(item)
                CFBridgingRelease(data)
                if (status != errSecSuccess) throw KeychainException(status)
            }
        }
    }

    override fun delete(account: String) {
        memScoped {
            retaining(service, account) { cfService, cfAccount ->
                val query =
                    dictionaryOf(
                        kSecClass to kSecClassGenericPassword,
                        kSecAttrService to cfService,
                        kSecAttrAccount to cfAccount,
                    )
                val status = SecItemDelete(query)
                CFRelease(query)
                if (status != errSecSuccess && status != errSecItemNotFound) throw KeychainException(status)
            }
        }
    }

    /** Runs [block] with the two strings bridged to Core Foundation, releasing them after. */
    private inline fun <T> retaining(
        first: String,
        second: String,
        block: (CFTypeRef?, CFTypeRef?) -> T,
    ): T {
        val cfFirst = CFBridgingRetain(first)
        val cfSecond = CFBridgingRetain(second)
        try {
            return block(cfFirst, cfSecond)
        } finally {
            CFBridgingRelease(cfFirst)
            CFBridgingRelease(cfSecond)
        }
    }

    /** A CFDictionary of the pairs, to release with CFRelease once the Security call has read it. */
    private fun MemScope.dictionaryOf(vararg pairs: Pair<CFStringRef?, CFTypeRef?>): CFDictionaryRef? {
        val keys = allocArrayOf(pairs.map { it.first as CPointer<*>? })
        val values = allocArrayOf(pairs.map { it.second as CPointer<*>? })
        return CFDictionaryCreate(null, keys.reinterpret(), values.reinterpret(), pairs.size.convert(), null, null)
    }

    private fun NSData.text(): String? = NSString.create(this, NSUTF8StringEncoding) as String?

    @Suppress("CAST_NEVER_SUCCEEDS")
    private fun String.data(): NSData? = (this as NSString).dataUsingEncoding(NSUTF8StringEncoding)

    companion object {
        const val SERVICE: String = "com.jassemsendi.unpacked"
    }
}

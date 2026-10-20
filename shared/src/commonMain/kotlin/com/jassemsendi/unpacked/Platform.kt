package com.jassemsendi.unpacked

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
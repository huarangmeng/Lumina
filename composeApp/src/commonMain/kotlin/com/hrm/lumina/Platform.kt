package com.hrm.lumina

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
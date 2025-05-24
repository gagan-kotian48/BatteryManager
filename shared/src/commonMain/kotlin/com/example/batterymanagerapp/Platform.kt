package com.example.batterymanagerapp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
package com.eps.wakey.utils

class EyeBlinkPeriod {
    val eyesClosed: Float?
        get() = field
    val eyesOpened: Float?
        get() = field
    constructor(eyesOpened: Float, eyesClosed: Float){
        this.eyesOpened = eyesOpened
        this.eyesClosed = eyesClosed
    }
}
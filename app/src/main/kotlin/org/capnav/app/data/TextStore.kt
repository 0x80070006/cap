package org.capnav.app.data

import org.capnav.app.security.EncryptedFile

/** Persistence seam for a single document; production stores are encrypted at rest. */
interface TextStore {
    fun read(): String?
    fun write(text: String)
    fun wipe()
}

class EncryptedTextStore(private val file: EncryptedFile) : TextStore {
    override fun read() = file.read()
    override fun write(text: String) = file.write(text)
    override fun wipe() = file.wipe()
}

class MemoryTextStore(var content: String? = null) : TextStore {
    override fun read() = content
    override fun write(text: String) {
        content = text
    }
    override fun wipe() {
        content = null
    }
}

package xyz.fieldatlas.assets

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class Sha256Test {
    @Test fun hashesKnownVector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.digest(ByteArrayInputStream("abc".encodeToByteArray())),
        )
    }

    @Test fun hashesEmptyStream() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.digest(ByteArrayInputStream(byteArrayOf())),
        )
    }
}

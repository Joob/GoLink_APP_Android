package co.golink.tester.data.encryption

import androidx.media3.common.C
import java.io.ByteArrayOutputStream
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Servidor falso com Range sobre um objeto GLK1: cobre offsets, janelas e o último bloco. */
class EncryptedMediaDataSourceTest {
    private val key = ByteArray(32) { 9 }
    private val plain = ByteArray(9 * 1024 * 1024 + 321) { (it * 31 + it / 251).toByte() }
    private val obj = EncryptedFileCodec.encrypt(plain, key)
    private val ranges = mutableListOf<String>()

    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        val range = chain.request().header("Range")!!
        ranges += range
        val (from, to) = range.removePrefix("bytes=").split('-').map { it.toInt() }
        val end = minOf(to, obj.size - 1)
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(206).message("Partial")
            .header("Content-Range", "bytes $from-$end/${obj.size}")
            .body(obj.copyOfRange(from, end + 1).toResponseBody("application/octet-stream".toMediaType()))
            .build()
    }.build()

    private fun source() = EncryptedMediaDataSource.Factory(client, "https://x.test/f", "t", key).createDataSource() as EncryptedMediaDataSource

    private fun readAll(position: Long, length: Long = C.LENGTH_UNSET.toLong(), bufSize: Int = 4096): ByteArray {
        val ds = source()
        ds.seek(position, length)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(bufSize)
        while (true) {
            val n = ds.read(buf, 0, buf.size)
            if (n == C.RESULT_END_OF_INPUT) break
            out.write(buf, 0, n)
        }
        ds.close()
        return out.toByteArray()
    }

    @Test
    fun readsWholeFileAcrossWindows() {
        assertArrayEquals(plain, readAll(0))
        assertTrue("mais do que uma janela", ranges.size >= 3)
    }

    @Test
    fun readsFromMidBlockToEnd() {
        assertArrayEquals(plain.copyOfRange(8_500_000, plain.size), readAll(8_500_000, bufSize = 777))
    }

    @Test
    fun readsBoundedRangeAcrossBlockBoundary() {
        assertArrayEquals(plain.copyOfRange(65_530, 65_550), readAll(65_530, length = 20))
    }

    @Test
    fun endOfFilePositionIsEmpty() {
        assertEquals(0, readAll(plain.size.toLong()).size)
    }
}

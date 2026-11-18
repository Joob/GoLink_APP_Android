package co.golink.tester.data.network

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Call.Factory do player de media que permite cancelar os pedidos em curso.
 * O Media3 não dá à DataSource forma de interromper uma leitura: o loader fica
 * preso no read() do socket até ao readTimeout (60 s) depois de o player ser
 * libertado. [cancelAll] fecha os sockets e o read() lança logo.
 */
class CancellableCallFactory(private val client: OkHttpClient) : Call.Factory {
    private val calls = java.util.Collections.newSetFromMap(java.util.WeakHashMap<Call, Boolean>())
    @Volatile private var cancelled = false

    override fun newCall(request: Request): Call {
        val call = client.newCall(request)
        synchronized(calls) {
            // Depois de cancelado, um pedido novo (retry do loader) já nasce cancelado.
            if (cancelled) call.cancel() else calls.add(call)
        }
        return call
    }

    fun cancelAll() {
        val snapshot = synchronized(calls) {
            cancelled = true
            calls.toList().also { calls.clear() }
        }
        snapshot.forEach { runCatching { it.cancel() } }
    }
}

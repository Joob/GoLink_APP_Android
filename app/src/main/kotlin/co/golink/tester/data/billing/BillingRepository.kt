package co.golink.tester.data.billing

import co.golink.tester.data.download.FileDownloader
import co.golink.tester.domain.billing.BillingSummary
import co.golink.tester.domain.billing.Invoice
import co.golink.tester.domain.billing.Plan
import co.golink.tester.domain.billing.toInvoice
import co.golink.tester.domain.billing.toPlan
import co.golink.tester.network.BillingApi
import javax.inject.Inject
import javax.inject.Singleton

/** Billing só de leitura (ver [BillingApi]). */
@Singleton
class BillingRepository @Inject constructor(
    private val api: BillingApi,
    private val downloader: FileDownloader,
) {
    suspend fun summary(): Result<BillingSummary> = runCatching {
        val response = api.summary()
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        response.body() ?: error("Resposta vazia")
    }

    suspend fun listPlans(): Result<List<Plan>> = runCatching {
        val response = api.listPlans()
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        val envelopes = response.body()?.data ?: error("Resposta vazia")
        envelopes
            .filter { (it.data.attributes.type ?: "fixed") == "fixed" }
            .map { it.data.toPlan() }
    }

    /** Uma página de faturas; devolve também se há mais. */
    suspend fun invoices(page: Int): Result<Pair<List<Invoice>, Boolean>> = runCatching {
        val response = api.invoices(page)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        val body = response.body() ?: error("Resposta vazia")
        val meta = body.meta
        body.data.map { it.data.toInvoice() } to (meta != null && meta.current_page < meta.last_page)
    }

    /** Guarda o PDF de uma fatura em Downloads. */
    suspend fun downloadInvoice(invoice: Invoice): Result<Unit> = runCatching {
        val response = api.invoicePdf(invoice.id)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        val bytes = response.body()?.use { it.bytes() } ?: error("Resposta vazia")
        val name = fileName(response.headers()["Content-Disposition"]) ?: "Invoice-${invoice.id.take(8)}.pdf"
        downloader.saveBytesToDownloads(name, "application/pdf", bytes)
    }

    /** Guarda o ZIP com todas as faturas em Downloads. */
    suspend fun downloadAllInvoices(): Result<Unit> = runCatching {
        val response = api.invoicesZip()
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        val body = response.body() ?: error("Resposta vazia")
        val name = fileName(response.headers()["Content-Disposition"]) ?: "GoLink-Invoices.zip"
        downloader.writeToDownloads(name, "application/zip") { out ->
            body.byteStream().use { it.copyTo(out) }
        }
    }

    private fun fileName(disposition: String?): String? =
        disposition?.let { Regex("filename=\"?([^\";]+)\"?").find(it)?.groupValues?.get(1) }
            ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
}

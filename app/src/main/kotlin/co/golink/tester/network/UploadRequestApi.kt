package co.golink.tester.network

import co.golink.tester.domain.uploadrequest.CreateUploadRequestBody
import co.golink.tester.domain.uploadrequest.UploadRequestEnvelope
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface UploadRequestApi {
    @POST("api/file-request")
    suspend fun createFileRequest(@Body body: CreateUploadRequestBody): Response<UploadRequestEnvelope>
}

package co.golink.tester.network

import co.golink.tester.domain.calendar.CalendarListResponse
import co.golink.tester.domain.calendar.CreatedIdResponse
import co.golink.tester.domain.calendar.EventListResponse
import co.golink.tester.domain.calendar.EventResponse
import co.golink.tester.domain.calendar.LookupKeysRequest
import co.golink.tester.domain.calendar.LookupKeysResponse
import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Calendário E2E (routes/calendar.php, prefixo api/calendar). */
interface CalendarApi {
    @GET("api/calendar/calendars")
    suspend fun calendars(): Response<CalendarListResponse>

    @POST("api/calendar/calendars")
    suspend fun createCalendar(@Body body: JsonObject): Response<CreatedIdResponse>

    @PATCH("api/calendar/calendars/{id}")
    suspend fun updateCalendar(@Path("id") id: String, @Body body: JsonObject): Response<ResponseBody>

    // Dono: apaga (em fila). Membro: sai.
    @DELETE("api/calendar/calendars/{id}")
    suspend fun deleteCalendar(@Path("id") id: String): Response<ResponseBody>

    @POST("api/calendar/calendars/{id}/members")
    suspend fun addMember(@Path("id") id: String, @Body body: JsonObject): Response<ResponseBody>

    @DELETE("api/calendar/calendars/{id}/members/{userId}")
    suspend fun removeMember(@Path("id") id: String, @Path("userId") userId: String): Response<ResponseBody>

    @POST("api/calendar/lookup-keys")
    suspend fun lookupKeys(@Body body: LookupKeysRequest): Response<LookupKeysResponse>

    @GET("api/calendar/events")
    suspend fun events(@Query("from") from: String, @Query("to") to: String): Response<EventListResponse>

    @GET("api/calendar/events/{id}")
    suspend fun event(@Path("id") id: String): Response<EventResponse>

    @POST("api/calendar/events")
    suspend fun createEvent(@Body body: JsonObject): Response<EventResponse>

    // 409 {type:'conflict', data: evento atual} quando a versão já mudou.
    @PATCH("api/calendar/events/{id}")
    suspend fun updateEvent(@Path("id") id: String, @Body body: JsonObject): Response<EventResponse>

    @DELETE("api/calendar/events/{id}")
    suspend fun deleteEvent(@Path("id") id: String): Response<ResponseBody>

    // holiday_country: null = automático (UpdateProfileFieldRequest não manda null).
    @PATCH("api/user/settings")
    suspend fun updateUserSetting(@Body body: JsonObject): Response<ResponseBody>

    @POST("api/calendar/events/{id}/rsvp")
    suspend fun rsvp(@Path("id") id: String, @Body body: JsonObject): Response<EventResponse>
}

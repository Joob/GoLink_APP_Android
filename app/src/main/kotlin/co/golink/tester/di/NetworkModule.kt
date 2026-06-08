package co.golink.tester.di

import co.golink.tester.BuildConfig
import co.golink.tester.network.interceptors.AppLoggerInterceptor
import co.golink.tester.network.interceptors.AuthInterceptor
import co.golink.tester.network.interceptors.HostRewriteInterceptor
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    @Named("base")
    fun provideBaseOkHttp(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
        if (BuildConfig.DEBUG) {
            // HEADERS instead of BODY: BODY buffers the entire request/response body
            // in memory for inspection, which causes OOM and timeouts on large uploads.
            builder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.HEADERS }
            )
        }
        return builder.build()
    }

    @Provides
    @Singleton
    @Named("authed")
    fun provideAuthedOkHttp(
        @Named("base") base: OkHttpClient,
        hostRewrite: HostRewriteInterceptor,
        auth: AuthInterceptor,
        appLogger: AppLoggerInterceptor,
    ): OkHttpClient = base.newBuilder()
        .addInterceptor(hostRewrite)
        .addInterceptor(auth)
        .addInterceptor(appLogger)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        @Named("authed") client: OkHttpClient,
        json: Json,
    ): Retrofit = Retrofit.Builder()
        .baseUrl("http://localhost/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    @Named("longRunning")
    fun provideLongRunningOkHttp(
        @Named("authed") authed: OkHttpClient,
    ): OkHttpClient = authed.newBuilder()
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .callTimeout(10, TimeUnit.MINUTES)
        .build()

    @Provides
    @Singleton
    @Named("longRunningRetrofit")
    fun provideLongRunningRetrofit(
        @Named("longRunning") client: OkHttpClient,
        json: Json,
    ): Retrofit = Retrofit.Builder()
        .baseUrl("http://localhost/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}

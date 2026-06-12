package co.golink.tester.di

import co.golink.tester.network.AuthApi
import co.golink.tester.network.BillingApi
import co.golink.tester.network.BrowseApi
import co.golink.tester.network.ConfigApi
import co.golink.tester.network.FavouritesApi
import co.golink.tester.network.FilesApi
import co.golink.tester.network.NewsApi
import co.golink.tester.network.NotificationsApi
import co.golink.tester.network.SettingsApi
import co.golink.tester.network.ShareApi
import co.golink.tester.network.TeamsApi
import co.golink.tester.network.TrashApi
import co.golink.tester.network.UploadRequestApi
import co.golink.tester.network.UserApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object ApiModule {

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideUserApi(retrofit: Retrofit): UserApi = retrofit.create(UserApi::class.java)

    @Provides
    @Singleton
    fun provideConfigApi(retrofit: Retrofit): ConfigApi = retrofit.create(ConfigApi::class.java)

    @Provides
    @Singleton
    fun provideNewsApi(retrofit: Retrofit): NewsApi = retrofit.create(NewsApi::class.java)

    @Provides
    @Singleton
    fun provideBrowseApi(retrofit: Retrofit): BrowseApi = retrofit.create(BrowseApi::class.java)

    @Provides
    @Singleton
    fun provideFilesApi(@Named("longRunningRetrofit") retrofit: Retrofit): FilesApi = retrofit.create(FilesApi::class.java)

    @Provides
    @Singleton
    fun provideShareApi(retrofit: Retrofit): ShareApi = retrofit.create(ShareApi::class.java)

    @Provides
    @Singleton
    fun provideFavouritesApi(retrofit: Retrofit): FavouritesApi = retrofit.create(FavouritesApi::class.java)

    @Provides
    @Singleton
    fun provideTrashApi(@Named("longRunningRetrofit") retrofit: Retrofit): TrashApi =
        retrofit.create(TrashApi::class.java)

    @Provides
    @Singleton
    fun provideNotificationsApi(retrofit: Retrofit): NotificationsApi =
        retrofit.create(NotificationsApi::class.java)

    @Provides
    @Singleton
    fun provideSettingsApi(retrofit: Retrofit): SettingsApi =
        retrofit.create(SettingsApi::class.java)

    @Provides
    @Singleton
    fun provideTeamsApi(retrofit: Retrofit): TeamsApi = retrofit.create(TeamsApi::class.java)

    @Provides
    @Singleton
    fun provideUploadRequestApi(retrofit: Retrofit): UploadRequestApi = retrofit.create(UploadRequestApi::class.java)

    @Provides
    @Singleton
    fun provideBillingApi(retrofit: Retrofit): BillingApi = retrofit.create(BillingApi::class.java)
}

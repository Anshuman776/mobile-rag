package com.ml.Anshuman776.docqa

import android.app.Application
import com.ml.Anshuman776.docqa.data.ObjectBoxStore
import com.ml.Anshuman776.docqa.di.AppModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.ksp.generated.module

class DocQAApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@DocQAApplication)
            modules(AppModule().module)
        }
        ObjectBoxStore.init(this)
    }
}

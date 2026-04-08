package com.ml.Anshuman776.docqa.ui.screens.edit_credentials

import androidx.lifecycle.ViewModel
import com.ml.Anshuman776.docqa.data.HFAccessToken
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class EditCredentialsViewModel(
    private val hfAccessToken: HFAccessToken,
) : ViewModel() {
    fun getHFAccessToken(): String? = hfAccessToken.getToken()

    fun saveHFAccessToken(accessToken: String) {
        hfAccessToken.saveToken(accessToken)
    }
}


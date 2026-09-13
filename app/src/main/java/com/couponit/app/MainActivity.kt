package com.couponit.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.couponit.app.importing.CouponImporter
import com.couponit.app.recognition.BarcodeRecognizer
import com.couponit.app.ui.CouponItApp
import com.couponit.app.ui.WalletViewModel
import com.couponit.app.ui.WalletViewModelFactory
import com.couponit.app.ui.theme.CouponItTheme

class MainActivity : ComponentActivity() {
    private val model: WalletViewModel by viewModels {
        val app = application as CouponItApplication
        WalletViewModelFactory(app.repository, CouponImporter(this, app.repository, BarcodeRecognizer(this)))
    }
    private val picker = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { model.import(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeShare(intent)
        setContent {
            CouponItTheme {
                CouponItApp(model = model, onPickImages = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeShare(intent)
    }

    private fun consumeShare(intent: Intent) {
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(parcelableUri(intent))
            Intent.ACTION_SEND_MULTIPLE -> parcelableUris(intent)
            else -> emptyList()
        }
        model.import(uris)
    }

    @Suppress("DEPRECATION")
    private fun parcelableUri(intent: Intent): Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else intent.getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun parcelableUris(intent: Intent): List<Uri> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
    } else intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
}

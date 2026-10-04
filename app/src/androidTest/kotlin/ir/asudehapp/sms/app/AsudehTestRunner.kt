package ir.asudehapp.sms.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** اپ را با [TestAsudehApplication] بالا می‌آورد. */
class AsudehTestRunner : AndroidJUnitRunner() {

    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, TestAsudehApplication::class.java.name, context)
}

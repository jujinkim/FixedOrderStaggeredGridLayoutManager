package kim.jujin.fixedorder.sample

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class EntryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_entry)

        findViewById<MaterialButton>(R.id.button_simple_test).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.button_complex_test).setOnClickListener {
            startActivity(Intent(this, ComplexTestActivity::class.java))
        }
    }
}

/*
 * Copyright (c) 2014 - 2015 Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gnucash.android.ui.passcode

import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.gnucash.android.R
import org.gnucash.android.ui.common.UxArgument

/**
 * Activity for entering and confirming passcode
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 */
class PasscodePreferenceActivity : AppCompatActivity(), KeyboardFragment.OnPasscodeEnteredListener {

    private var isPassEnabled = false
    private var reenter = false
    private lateinit var passcode: String
    private lateinit var passTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.passcode_lockscreen)

        passTextView = findViewById(R.id.passcode_label)

        isPassEnabled = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            .getBoolean(UxArgument.ENABLED_PASSCODE, false)

        if (isPassEnabled) {
            passTextView.setText(R.string.label_old_passcode)
        }
    }

    override fun onPasscodeEntered(pass: String) {
        val savedPasscode = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            .getString(UxArgument.PASSCODE, "")

        if (isPassEnabled) {
            if (pass == savedPasscode) {
                isPassEnabled = false
                passTextView.setText(R.string.label_new_passcode)
            } else {
                Toast.makeText(this, R.string.toast_wrong_passcode, Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (reenter) {
            if (passcode == pass) {
                setResult(RESULT_OK, Intent().putExtra(UxArgument.PASSCODE, pass))
                finish()
            } else {
                Toast.makeText(
                    this,
                    R.string.toast_invalid_passcode_confirmation,
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            passcode = pass
            reenter = true
            passTextView.setText(R.string.label_confirm_passcode)
        }
    }
}

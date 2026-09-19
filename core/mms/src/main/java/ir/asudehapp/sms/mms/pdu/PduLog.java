/*
 * Copyright (C) 2026 Asudeh contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ir.asudehapp.sms.mms.pdu;

/**
 * Replaces android.util.Log and Timber in the AOSP PDU code. Logging is
 * deliberately a no-op: the original code logs header values and message text,
 * and Asudeh never writes message text or phone numbers to any log (D58).
 */
final class PduLog {
    private PduLog() {
    }

    static void v(Object... ignored) {
    }

    static void e(Object... ignored) {
    }
}

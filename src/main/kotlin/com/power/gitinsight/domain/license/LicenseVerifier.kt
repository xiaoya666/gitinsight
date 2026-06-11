package com.power.gitinsight.domain.license

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64

/**
 * team : gitInsight.
 * Class Name: LicenseVerifier
 * Description: Offline Ed25519 license verification. Mirrors the server's signing contract
 *              (gitinsight-server src/license.ts): a key is "base64url(payloadJson).base64url(sig)"
 *              where payloadJson = {id,email,tier,exp} and exp is epoch SECONDS (0 = perpetual).
 *              The plugin only ever holds the PUBLIC key, so it can validate a pasted key without
 *              any network call; the optional /license/verify revocation check happens elsewhere.
 *              Expiry is enforced with a 7-day offline grace so a brief clock/network hiccup never
 *              locks out a paying user. Dormant until the 1.1.x activation flow wires it into
 *              LicenseSettings — PUBLIC_KEY_SPKI_B64URL is a placeholder until `npm run keygen`.
 *
 * @author: power
 * on Date: 2026/06/10 Time: 19:30
 **/
internal object LicenseVerifier {

    /** base64url-encoded SPKI Ed25519 public key, embedded at release time. Replace via keygen. */
    private const val PUBLIC_KEY_SPKI_B64URL = "REPLACE_WITH_SPKI_BASE64URL"

    /** Grace window after expiry before a key is rejected (covers clock skew / offline periods). */
    const val OFFLINE_GRACE_SECONDS: Long = 7L * 24 * 3600

    data class Verified(
        val valid: Boolean,
        val reason: String,
        val tier: LicenseTier = LicenseTier.FREE,
        val email: String? = null,
        val expiresAt: Long = 0L, // epoch seconds, 0 = perpetual
    )

    /** Verify signature + expiry of [key] offline. Never throws — any failure maps to a reason. */
    fun verify(key: String, nowEpochSeconds: Long = Instant.now().epochSecond): Verified {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return Verified(false, "empty")

        val dot = trimmed.indexOf('.')
        if (dot <= 0 || dot == trimmed.length - 1) return Verified(false, "malformed")

        val payloadBytes = runCatching { b64urlDecode(trimmed.substring(0, dot)) }
            .getOrElse { return Verified(false, "malformed") }
        val signature = runCatching { b64urlDecode(trimmed.substring(dot + 1)) }
            .getOrElse { return Verified(false, "malformed") }

        val signatureOk = runCatching { verifySignature(payloadBytes, signature) }
            .getOrElse { return Verified(false, "verify-error") }
        if (!signatureOk) return Verified(false, "bad-signature")

        val json = String(payloadBytes, Charsets.UTF_8)
        val tierStr = stringField(json, "tier") ?: return Verified(false, "bad-payload")
        val email = stringField(json, "email")
        val exp = longField(json, "exp") ?: 0L
        val tier = parseTier(tierStr)

        if (exp != 0L && nowEpochSeconds > exp + OFFLINE_GRACE_SECONDS) {
            return Verified(false, "expired", tier, email, exp)
        }
        return Verified(true, "ok", tier, email, exp)
    }

    private fun verifySignature(payload: ByteArray, signature: ByteArray): Boolean {
        val spki = b64urlDecode(PUBLIC_KEY_SPKI_B64URL)
        val publicKey = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(spki))
        val verifier = Signature.getInstance("Ed25519")
        verifier.initVerify(publicKey)
        verifier.update(payload)
        return verifier.verify(signature)
    }

    private fun parseTier(raw: String): LicenseTier = when (raw.lowercase()) {
        "pro" -> LicenseTier.PRO
        "enterprise" -> LicenseTier.ENTERPRISE
        "oss" -> LicenseTier.OSS
        "pro_preview", "preview" -> LicenseTier.PRO_PREVIEW
        else -> LicenseTier.FREE
    }

    private fun b64urlDecode(s: String): ByteArray = Base64.getUrlDecoder().decode(s)

    /** First `"name":"..."` string value, escapes decoded for the simple values we sign. */
    private fun stringField(json: String, name: String): String? {
        val m = Regex("\"" + Regex.escape(name) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json) ?: return null
        return m.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\")
    }

    /** First `"name":<number>` integer value. */
    private fun longField(json: String, name: String): Long? {
        val m = Regex("\"" + Regex.escape(name) + "\"\\s*:\\s*(-?\\d+)").find(json) ?: return null
        return m.groupValues[1].toLongOrNull()
    }
}

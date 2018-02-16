package models

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import play.api.Logger

object AuthorizationChecker { 
    def generateHMAC(message: String, key: String): String = {
        val secret = new SecretKeySpec(key.getBytes, "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(secret)
        val hashString: Array[Byte] = mac.doFinal(message.getBytes)
        new String(hashString.map(_.toChar))
    }

    def check(application: String, token: String, timestamp: String, signature: String): Boolean = {
        val message = application + token + timestamp
        val hmac = generateHMAC(message, token)
        Logger.debug(signature)
        Logger.debug(hmac)
        signature == hmac
    }
}
package com.muhammadiqbalafandi.enotes.utils

import com.muhammadiqbalafandi.classiccryptographyalgorithm.algorithm.AtbashCipher
import com.muhammadiqbalafandi.classiccryptographyalgorithm.algorithm.VigenereCipher

object Utils {
    fun encryptionText(text: String, key: String): String {
        return AtbashCipher.encryption(VigenereCipher.encryption(text, key))
    }

    fun decryptionText(text: String, key: String): String {
        return VigenereCipher.decryption(AtbashCipher.encryption(text), key)
    }
}
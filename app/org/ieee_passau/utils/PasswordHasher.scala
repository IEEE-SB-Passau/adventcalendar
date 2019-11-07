package org.ieee_passau.utils

import java.security.SecureRandom

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.util.Base64

/**
  * Utility class to hash and verify passwords with PBKDF2.
  */
object PasswordHasher {

  val NUM_ITERATIONS: Int = 64000
  val HASH_LENGTH: Int = 32 * 8
  val SALT_LENGTH: Int = 16 * 8
  val MAX_PASSWORD_LENGTH: Int = 128

  private val rng = new SecureRandom()
  private val encoder = Base64.getEncoder
  private val decoder = Base64.getDecoder

  /**
    * Generate a new hash from a password.
    * This method is not deterministic, do not use it for verifying passwords.
    *
    * @param password to hash.
    * @return a base64 encoded hash.
    */
  def hashPassword(password: String): String = {
    val truncatedPassword = this.truncatePassword(password)
    val salt = this.generateSalt()
    val hash = this.hash(truncatedPassword, salt)
    this.encoder.encodeToString(hash)
  }

  /**
    * Verify that a password matches a given hash.
    *
    * @param password to test.
    * @param hash to verify against.
    * @return true if the hash was generated with the given password, false otherwise.
    */
  def verifyPassword(password: String, hash: String): Boolean = {
    val truncatedPassword = this.truncatePassword(password)
    val hashBytes = this.decoder.decode(hash)
    val salt = this.extractSalt(hashBytes)
    val computedHash = this.hash(truncatedPassword, salt)
    this.secureCompare(hashBytes, computedHash)
  }

  private def hash(password: String, salt: Array[Byte]): Array[Byte] = {
    val chars = password.toCharArray
    val kspec = new PBEKeySpec(chars, salt, NUM_ITERATIONS, HASH_LENGTH)
    val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
    val hash = skf.generateSecret(kspec).getEncoded
    salt ++ hash
  }

  private def extractSalt(hash: Array[Byte]): Array[Byte] = {
    hash.slice(0, SALT_LENGTH / 8)
  }

  private def generateSalt(): Array[Byte] = {
    val salt = new Array[Byte](SALT_LENGTH / 8)
    this.rng.nextBytes(salt)
    salt
  }

  private def truncatePassword(password: String): String = {
    if (password.length > MAX_PASSWORD_LENGTH) {
      password.substring(0, MAX_PASSWORD_LENGTH)
    } else {
      password
    }
  }

  private def secureCompare(a: Array[Byte], b: Array[Byte]): Boolean = {
    val length = Math.min(a.length, b.length)

    var x = 0
    for (i <- 0 until length) {
      x |= a(i) ^ b(i)
    }

    a.length == b.length && x == 0
  }

  def generateUrlString(): String = {
    val salt = new Array[Byte](265 / 8)
    this.rng.nextBytes(salt)
    encoder.encodeToString(salt).replace("/", "_").replace("+", "_").replace("=", "")
  }
}

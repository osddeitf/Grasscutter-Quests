
package emu.grasscutter.utils;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public final class TextUtils {
  public static long getTextHash(String textString)
          throws NoSuchAlgorithmException {
      byte[] bytes = textString.getBytes(StandardCharsets.UTF_8);

      // Matches: ((textString.length shr 8) + 1) shl 8
      int size = ((textString.length() >> 8) + 1) << 8;
      bytes = Arrays.copyOf(bytes, size); // Pads with zeroes or truncates.

      byte[] hash = MessageDigest.getInstance("MD5").digest(bytes);

      // The first 8 digest bytes form a little-endian unsigned integer.
      long result = 0L;
      for (int i = 0; i < 8; i++) {
          result |= (hash[i] & 0xffL) << (8 * i);
      }
      return result;
  }
}
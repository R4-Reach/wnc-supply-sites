package org.r4reach.auth.setup.password.set.pass;

import java.util.List;

/** List of passwords that are too easy. */
class EasyPasswordList {

  private static final List<String> easyPasswords =
      List.of(
          "tarheel strong",
          "jumping rabbit",
          "big truck",
          "move stuff",
          "password",
          "password123",
          "12345",
          "123456",
          "admin",
          "passw0rd",
          "password1",
          "00000",
          "     ");

  static boolean isEasyPassword(String password) {
    return easyPasswords.contains(password.toLowerCase());
  }
}

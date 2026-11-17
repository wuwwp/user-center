package com.wwp.usercenter.service.impl;

import com.wwp.usercenter.common.BusinessException;
import com.wwp.usercenter.common.ErrorCode;
import com.wwp.usercenter.service.PasswordPolicyService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * 基于 Top 1000 弱密码字典的强度补充校验。
 *
 * <p>字典来源见 {@code security/weak-passwords-NOTICE.txt}。字典与用户输入都做同一套归一化：转小写， 并把常见 leet 替换还原为字母，避免
 * {@code Passw0rd}、{@code P@ssw0rd} 绕过字典。
 */
@Slf4j
@Service
public class PasswordPolicyServiceImpl implements PasswordPolicyService {

  private static final String DICTIONARY_PATH = "security/weak-passwords.txt";

  private final Set<String> weakPasswords;

  public PasswordPolicyServiceImpl() {
    this(loadDictionary());
  }

  PasswordPolicyServiceImpl(Set<String> weakPasswords) {
    this.weakPasswords = weakPasswords;
  }

  @Override
  public void ensureNotWeak(String password) {
    if (password == null || password.isEmpty()) {
      return;
    }
    if (weakPasswords.contains(normalize(password))) {
      throw new BusinessException(ErrorCode.PARAM_INVALID, "密码过于简单或常见，请更换");
    }
  }

  /** 把数字/符号还原为字母，覆盖 Passw0rd、P@ssw0rd 这类常见变形。 */
  private static String normalize(String password) {
    StringBuilder normalized = new StringBuilder(password.length());
    for (char character : password.toLowerCase(Locale.ROOT).toCharArray()) {
      normalized.append(
          switch (character) {
            case '0' -> 'o';
            case '1' -> 'l';
            case '3' -> 'e';
            case '4' -> 'a';
            case '5' -> 's';
            case '7' -> 't';
            case '@' -> 'a';
            case '$' -> 's';
            default -> character;
          });
    }
    return normalized.toString();
  }

  private static Set<String> loadDictionary() {
    ClassPathResource resource = new ClassPathResource(DICTIONARY_PATH);
    try {
      Set<String> entries =
          resource
              .getContentAsString(StandardCharsets.UTF_8)
              .lines()
              .map(String::trim)
              .filter(line -> !line.isEmpty() && !line.startsWith("#"))
              .map(PasswordPolicyServiceImpl::normalize)
              .collect(Collectors.toUnmodifiableSet());
      log.info("weak password dictionary loaded, entries={}", entries.size());
      return entries;
    } catch (IOException exception) {
      throw new IllegalStateException("无法加载弱密码字典: " + DICTIONARY_PATH, exception);
    }
  }
}

package com.kodality.termserver

import com.kodality.commons.model.Issue
import org.apache.commons.text.StringSubstitutor

class AuthUtil {
  static String message(Issue issue) {
    return StringSubstitutor.replace(issue.getMessage(), issue.getParams(), "{{", "}}")
  }
}

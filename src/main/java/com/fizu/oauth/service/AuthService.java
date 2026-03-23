package com.fizu.oauth.service;

import java.util.Map;

public interface AuthService {
    Map<String,String> loginWithGoogle(String idToken);
}
